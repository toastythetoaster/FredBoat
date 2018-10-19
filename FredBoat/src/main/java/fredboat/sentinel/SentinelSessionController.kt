package fredboat.sentinel

import com.fredboat.sentinel.SentinelExchanges
import com.fredboat.sentinel.entities.AppendSessionEvent
import com.fredboat.sentinel.entities.RemoveSessionEvent
import com.fredboat.sentinel.entities.RunSessionRequest
import fredboat.config.property.AppConfigProperties
import fredboat.util.DiscordUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Service
import java.lang.Thread.sleep
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

@Service
class SentinelSessionController(
        val rabbitTemplate: RabbitTemplate,
        val appConfig: AppConfigProperties,
        val sentinelTracker: SentinelTracker
) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(SentinelSessionController::class.java)
        private const val MAX_HELLO_AGE_MS = 40000
        private const val HOME_GUILD_ID = 174820236481134592L // FredBoat Hangout is to be prioritized
        private const val IDENTIFY_DELAY = 5000L
    }

    @Suppress("LeakingThis")
    val homeShardId = DiscordUtil.getShardId(HOME_GUILD_ID, appConfig)
    val queued = ConcurrentHashMap<Int, AppendSessionEvent>()
    var worker: Thread? = null

    init {
        startWorker()
    }

    fun appendSession(event: AppendSessionEvent) {
        event.totalShards.assertShardCount()
        queued[event.shardId] = event
        log.info("Appended ${event.shardId}")
    }

    fun removeSession(event: RemoveSessionEvent) {
        event.totalShards.assertShardCount()
        queued.remove(event.shardId)
        log.info("Removed ${event.shardId}")
    }

    private fun Int.assertShardCount() {
        if (this != appConfig.shardCount) {
            throw IllegalStateException("Mismatching shard count. Got $this, expected ${appConfig.shardCount}")
        }
    }

    fun getNextShard(): AppendSessionEvent? {
        // Figure out which sentinels we wish to command. This filters outs unresponsive ones
        val sentinelKeys = sentinelTracker.sentinels
                .asSequence()
                .filter { System.currentTimeMillis() - it.time <= MAX_HELLO_AGE_MS }
                .map { it.key }
                .toList()

        // Check for the home shard
        queued[homeShardId]?.let {
            if (sentinelKeys.contains(it.routingKey)) return it
        }

        // Otherwise just get the lowest possible shard
        queued.values.sortedBy { it.shardId }.forEach {
            if (sentinelKeys.contains(it.routingKey)) return it
        }

        return null
    }

    private fun workerLoop() {
        val next = getNextShard()

        if (next == null) {
            sleep(1000)
            return
        }

        val request = RunSessionRequest(next.shardId)
        log.info("Requesting ${next.routingKey} to start shard ${next.shardId}")
        val started = System.currentTimeMillis()
        rabbitTemplate.convertSendAndReceive(SentinelExchanges.REQUESTS, next.routingKey, request)
        val timeTaken = System.currentTimeMillis() - started
        log.info("Started ${next.shardId} from ${next.routingKey}, took ${timeTaken}ms")
        sleep(IDENTIFY_DELAY)
        queued.remove(next.shardId)
    }

    private fun startWorker() {
        if (worker?.isAlive == true) throw IllegalStateException("Worker is already alive")
        worker = thread(name = "session-worker") {
            log.info("Started session worker")
            while (true) {
                try {
                    workerLoop()
                } catch (e: Exception) {
                    log.error("Caught exception in session worker loop", e)
                    sleep(500)
                }
            }
        }
    }

}