package fredboat.sentinel

import com.fredboat.sentinel.SentinelExchanges
import com.fredboat.sentinel.entities.GuildSubscribeRequest
import fredboat.config.property.AppConfig
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap

@Service
class GuildCache(private val sentinel: Sentinel,
                 private val appConfig: AppConfig) {

    init {
        INSTANCE = this
    }

    companion object {
        lateinit var INSTANCE: GuildCache
    }

    // TODO: Invalidation
    val cache = ConcurrentHashMap<Long, InternalGuild>()

    fun get(id: Long): Mono<Guild?> = Mono.create {
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
    }

    private fun calculateShardId(guildId: Long): Int = ((guildId shr 22) % appConfig.shardCount.toLong()).toInt()

}