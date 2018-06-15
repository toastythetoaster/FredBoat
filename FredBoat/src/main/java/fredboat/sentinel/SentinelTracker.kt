package fredboat.sentinel

import com.fredboat.sentinel.entities.SentinelHello
import fredboat.config.property.AppConfig
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/** Class that tracks Sentinels and their routing keys */
@Service
class SentinelTracker(private val appConfig: AppConfig) {
    companion object {
        private val log: Logger = LoggerFactory.getLogger(SentinelTracker::class.java)
    }

    /** Shard id mapped to [SentinelHello] */
    private val map: ConcurrentHashMap<Int, SentinelHello> = ConcurrentHashMap()
    val sentinels: Set<SentinelHello>
        get() = map.values.toSet()

    fun onHello(hello: SentinelHello) = hello.run {
        log.info("Received hello from $key with shards [$shardStart;$shardEnd] \uD83D\uDC4B")

        if (shardCount != appConfig.shardCount) {
            throw IllegalStateException("Received SentinelHello from $key with shard count $shardCount shards, " +
                    "but we are configured for ${appConfig.shardCount}!")
        }

        (shardStart..shardEnd).forEach {
            map[it] = hello
        }
    }

    fun getHello(shardId: Int) = map[shardId]
    fun getKey(shardId: Int): String {
        val hello = getHello(shardId)
                ?: throw IllegalStateException("Attempted to access routing key of $shardId," +
                        " but we haven't received hello from it.")
        return hello.key
    }
}