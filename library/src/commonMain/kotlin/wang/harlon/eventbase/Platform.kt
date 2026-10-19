package wang.harlon.eventbase

import kotlinx.coroutines.CoroutineScope

/**
 * 进后台时的 flush 入口。iOS 在这里向系统申请后台执行时间：不申请的话进程在通知回调返回后几秒内
 * 就被挂起，请求发出去了、响应回不来，下次进后台整批重发——2026-10 实测这是 iOS 重复率的主因
 * （生产包 1.3.0 约 23%，Android 同期 3%）。Android 进后台后进程不会立刻挂起，直接起协程即可。
 */
internal expect fun launchBackgroundFlush(scope: CoroutineScope, flush: suspend () -> Unit)

/** 摘掉平台侧的生命周期观察者。[Eventbase.reset] 调用，避免重装后叠加。 */
internal expect fun detachLifecycle()

/** 诊断日志。Android 走 android.util.Log（tag `eventbase`，可 `adb logcat -s eventbase`），iOS 走 NSLog。 */
internal expect fun logLine(message: String)
