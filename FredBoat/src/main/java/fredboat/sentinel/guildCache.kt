package fredboat.sentinel

import com.fredboat.sentinel.SentinelExchanges
import com.fredboat.sentinel.entities.GuildSubscribeRequest
import fredboat.config.property.AppConfig
import kotlinx.coroutines.experimental.reactive.awaitSingle
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeoutException

@Service
class GuildCache(private val sentinel: Sentinel,
                 private val appConfig: AppConfig) {

    init {
        INSTANCE = this
    }

    companion object {
        lateinit var INSTANCE: GuildCache
    }

    val cache = ConcurrentHashMap<Long, InternalGuild>()

    fun get(id: Long): Mono<Guild?> = Mono.create<Guild?> {
        val guild = cache[id]
        if (guild != null) it.success(guild)

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
    }.timeout(Duration.ofSeconds(10), Mono.create { it.error(TimeoutException("Guild $id timed out")) })

    fun getIfCached(id: Long): Guild? = cache[id] as Guild

    private fun calculateShardId(guildId: Long): Int = ((guildId shr 22) % appConfig.shardCount.toLong()).toInt()

}

suspend fun getGuild(id: Long) = GuildCache.INSTANCE.get(id).awaitSingle()
fun getGuildMono(id: Long) = GuildCache.INSTANCE.get(id)
fun getGuild(id: Long, callback: (Guild) -> Unit) {
    GuildCache.INSTANCE.get(id).subscribe {callback(it!!)}
}
