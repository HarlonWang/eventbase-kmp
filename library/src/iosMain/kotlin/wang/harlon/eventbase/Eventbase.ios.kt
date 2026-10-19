package wang.harlon.eventbase

import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIBackgroundTaskIdentifier
import platform.UIKit.UIBackgroundTaskInvalid
import platform.darwin.NSObjectProtocol

fun Eventbase.init(
    config: EventbaseConfig,
    httpClient: HttpClient? = null,
): EventbaseClient {
    val installed = installClient(config, UserDefaultsStorage(), httpClient)
    if (installed.isNew && config.autoLifecycle) {
        observeLifecycle()
        Eventbase.markLifecycleAttached()
    }
    return installed.client
}

/** addObserverForName 返回的 token 必须留住，否则观察者再也摘不掉。 */
private val observers = mutableListOf<NSObjectProtocol>()

private fun observeLifecycle() {
    val center = NSNotificationCenter.defaultCenter
    observers += center.addObserverForName(
        UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue
    ) { Eventbase.onForeground() }
    observers += center.addObserverForName(
        UIApplicationDidEnterBackgroundNotification, null, NSOperationQueue.mainQueue
    ) { Eventbase.onBackground() }
}

/**
 * 用 beginBackgroundTask 向系统要约 30 秒窗口，flush 完成或窗口到期即归还。
 * 到期时只归还窗口不取消协程：协程跑到进程挂起为止，没发完的留在队列，由至少一次语义兜底。
 */
internal actual fun launchBackgroundFlush(scope: CoroutineScope, flush: suspend () -> Unit) {
    val task = BackgroundTask().apply { begin() }
    scope.launch {
        try {
            flush()
        } finally {
            task.end()
        }
    }
}

/** end 必须恰好一次：到期回调与 flush 收尾可能并发，多 end 一次 UIKit 会告警。 */
private class BackgroundTask {
    private val lock = createLock()
    private var id: UIBackgroundTaskIdentifier = UIBackgroundTaskInvalid
    private var ended = false

    fun begin() {
        // 文档保证 begin/end 可在任意线程调用；拿不到 UIApplication（扩展进程等）就当没有窗口
        val granted = runCatching {
            UIApplication.sharedApplication.beginBackgroundTaskWithExpirationHandler { end() }
        }.getOrDefault(UIBackgroundTaskInvalid)
        lock.withLock { id = granted }
    }

    fun end() {
        val toEnd = lock.withLock {
            if (ended || id == UIBackgroundTaskInvalid) null else id.also { ended = true }
        } ?: return
        runCatching { UIApplication.sharedApplication.endBackgroundTask(toEnd) }
    }
}

internal actual fun detachLifecycle() {
    val center = NSNotificationCenter.defaultCenter
    observers.forEach(center::removeObserver)
    observers.clear()
}
