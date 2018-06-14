package fredboat.audio.lavalink

import com.fredboat.sentinel.entities.VoiceServerUpdate
import fredboat.config.idString
import fredboat.config.property.AppConfig
import fredboat.sentinel.Guild
import fredboat.sentinel.Sentinel
import lavalink.client.io.Lavalink
import org.json.JSONObject
import org.springframework.stereotype.Service

@Service
class SentinelLavalink(
        val sentinel: Sentinel,
        val appConfig: AppConfig
) : Lavalink<SentinelLink>(
        sentinel.selfUser.idString,
        appConfig.shardCount
) {

    companion object {
        lateinit var INSTANCE: SentinelLavalink
    }

    init {
        INSTANCE = this
    }

    override fun buildNewLink(guildId: String) = SentinelLink(this, guildId)

    fun getLink(guild: Guild) = getLink(guild.id.toString())
    fun getExistingLink(guild: Guild) = getExistingLink(guild.idString)

    fun onVoiceServerUpdate(update: VoiceServerUpdate) {
        val json = JSONObject(update.raw)
        val gId = json.getString("guild-id")
        getLink(gId).onVoiceServerUpdate(json, update.sessionId)
    }
}