package fredboat.audio.lavalink

import com.fredboat.sentinel.entities.VoiceServerUpdate
import fredboat.config.idString
import fredboat.config.property.AppConfig
import fredboat.config.property.LavalinkConfig
import fredboat.sentinel.Guild
import fredboat.sentinel.Sentinel
import lavalink.client.io.Lavalink
import org.json.JSONObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class SentinelLavalink(
        val sentinel: Sentinel,
        val appConfig: AppConfig,
        lavalinkConfig: LavalinkConfig
) : Lavalink<SentinelLink>(
        sentinel.selfUser.idString,
        appConfig.shardCount
) {

    companion object {
        lateinit var INSTANCE: SentinelLavalink
        private val log: Logger = LoggerFactory.getLogger(SentinelLavalink::class.java)
    }

    init {
        @Suppress("LeakingThis")
        INSTANCE = this
        lavalinkConfig.nodes.forEach { addNode(it.name, it.uri, it.password) }
    }

    override fun buildNewLink(guildId: String) = SentinelLink(this, guildId)

    fun getLink(guild: Guild) = getLink(guild.id.toString())
    fun getExistingLink(guild: Guild) = getExistingLink(guild.idString)

    fun onVoiceServerUpdate(update: VoiceServerUpdate) {
        val json = JSONObject(update.raw)
        val gId = json.getString("guild_id")
        val link = getLink(gId)

        if (link.channel == null) {
            log.warn("Received voice server update, but we are not expecting a particular channel. " +
                    "This should not happen \uD83E\uDD14. Ignoring...")
            return
        }

        link.onVoiceServerUpdate(json, update.sessionId)
    }
}