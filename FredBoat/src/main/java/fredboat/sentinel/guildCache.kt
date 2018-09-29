package fredboat.sentinel

import com.fredboat.sentinel.SentinelExchanges
import com.fredboat.sentinel.entities.GuildSubscribeRequest
import fredboat.audio.lavalink.SentinelLavalink
import fredboat.config.property.AppConfig
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.reactive.awaitFirstOrNull
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeoutException

@Service
class GuildCache(private val sentinel: Sentinel,
                 private val appConfig: AppConfig,
                 private val lavalink: SentinelLavalink) {

    init {
        @Suppress("LeakingThis")
        INSTANCE = this
    }

    companion object {
        lateinit var INSTANCE: GuildCache
        private val log: Logger = LoggerFactory.getLogger(GuildCache::class.java)
    }

    @Autowired /* Cyclic dependency */
    lateinit var rabbitConsumer: RabbitConsumer
    val cache = ConcurrentHashMap<Long, InternalGuild>()

    fun get(id: Long): Mono<Guild?> = Mono.create<Guild?> { sink ->
        val guild = cache[id]
        if (guild != null) {
            sink.success(guild)
            return@create
        }
        val startTime = System.currentTimeMillis()

        sentinel.genericMonoSendAndReceive<RawGuild?, Guild?>(
                SentinelExchanges.REQUESTS,
                sentinel.tracker.getKey(calculateShardId(id)),
                GuildSubscribeRequest(id),
                mayBeEmpty = true,
                transform = {
                    if (it == null) return@genericMonoSendAndReceive null

                    val timeTakenReceive = System.currentTimeMillis() - startTime
                    val g = InternalGuild(it)
                    cache[g.id] = g
                    val timeTakenParse = System.currentTimeMillis() - startTime - timeTakenReceive
                    val timeTaken = timeTakenReceive + timeTakenParse

                    log.info("Subscribing to {} took {}ms including {}ms parsing time.\nMembers: {}\nChannels: {}\nRoles: {}\n",
                            g,
                            timeTaken,
                            timeTakenParse,
                            g.members.size,
                            g.textChannels.size + g.voiceChannels.size,
                            g.roles.size
                    )

                    // Asynchronously handle existing VSU from an older FredBoat session, if it exists
                    it.voiceServerUpdate?.let { vsu ->
                        launch {
                            val channelId = g.selfMember.voiceChannel?.idString

                            val link = lavalink.getLink(g)
                            if (channelId == null) {
                                log.warn("Received voice server update during guild subscribe, but we are not in a channel." +
                                        "This should not happen. Disconnecting...")
                                link.queueAudioDisconnect()
                                return@launch
                            }

                            link.setChannel(channelId)
                            rabbitConsumer.receive(vsu)
                            /*
                            // This code is an excellent way to test expired voice server updates
                            val json = JSONObject(vsu.raw)
                            json.put("token", "asd")
                            rabbitConsumer.receive(VoiceServerUpdate(vsu.sessionId, json.toString()))
                            */
                        }
                    }

                    g
                }
        )
                .doOnError { sink.error(it) }
                .subscribe { sink.success(it) }
    }.timeout(Duration.ofSeconds(30), Mono.error(TimeoutException("Timed out while subscribing to $id")))

    fun getIfCached(id: Long): Guild? = cache[id]

    private fun calculateShardId(guildId: Long): Int = ((guildId shr 22) % appConfig.shardCount.toLong()).toInt()

}

suspend fun getGuild(id: Long) = GuildCache.INSTANCE.get(id).awaitFirstOrNull()
fun getGuildMono(id: Long) = GuildCache.INSTANCE.get(id)
fun getGuild(id: Long, callback: (Guild) -> Unit) {
    GuildCache.INSTANCE.get(id).subscribe { callback(it!!) }
}
