package fredboat.sentinel

import com.fredboat.sentinel.SentinelExchanges
import com.fredboat.sentinel.entities.GuildSubscribeRequest
import fredboat.config.property.AppConfig
import kotlinx.coroutines.experimental.reactive.awaitFirstOrNull
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

@Service
class GuildCache(private val sentinel: Sentinel,
                 private val appConfig: AppConfig) {

    init {
        INSTANCE = this
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(GuildCache::class.java)
        lateinit var INSTANCE: GuildCache
    }

    val cache = ConcurrentHashMap<Long, InternalGuild>()

    fun get(id: Long): Mono<Guild?> = Mono.create<Guild?> { sink ->
        val guild = cache[id]
        if (guild != null) sink.success(guild)

        sentinel.genericMonoSendAndReceive<RawGuild?, Guild?>(
                SentinelExchanges.REQUESTS,
                sentinel.tracker.getKey(calculateShardId(id)),
                GuildSubscribeRequest(id),
                mayBeEmpty = true,
                transform = {
                    if (it == null) return@genericMonoSendAndReceive null

                    val g = InternalGuild(it)
                    cache[g.id] = g
                    g
                }
        )
                .doOnError { sink.error(it) }
                .subscribe { sink.success(it) }
    }.timeout(Duration.ofSeconds(10), Mono.empty())

    fun getIfCached(id: Long): Guild? = cache[id]

    private fun calculateShardId(guildId: Long): Int = ((guildId shr 22) % appConfig.shardCount.toLong()).toInt()

}

suspend fun getGuild(id: Long) = GuildCache.INSTANCE.get(id).awaitFirstOrNull()
fun getGuildMono(id: Long) = GuildCache.INSTANCE.get(id)
fun getGuild(id: Long, callback: (Guild) -> Unit) {
    GuildCache.INSTANCE.get(id).subscribe { callback(it!!) }
}
