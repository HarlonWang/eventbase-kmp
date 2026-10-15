package wang.harlon.eventbase

data class Batch(
    val install: String,
    val session: String,
    val user: String?,
    /** 这一批事件入队时的 app 版本；与 [config] 的 `appVersion` 可能不同（升级前积压的事件） */
    val version: String,
    val config: EventbaseConfig,
    val events: List<QueuedEvent>,
)

/** 上报结果决定队列去留：DROP 出队，RETRY 留在队列等下次。 */
enum class SendResult { DROP, RETRY }

interface Sink {
    suspend fun send(batch: Batch): SendResult
}
