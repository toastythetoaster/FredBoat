package fredboat.audio.lavalink

import fredboat.config.property.AppConfig
import fredboat.sentinel.Sentinel
import lavalink.client.io.Lavalink
import org.springframework.stereotype.Service

@Service
class SentinelLavalink(
        val sentinel: Sentinel,
        val appConfig: AppConfig
) : Lavalink<SentinelLink>(
        sentinel.getApplicationInfo().botId.toString(),
        appConfig.shardCount
) {

    companion object {
        lateinit var INSTANCE: SentinelLavalink
    }

    init {
        INSTANCE = this
    }

    override fun buildNewLink(guildId: String) = SentinelLink(this, guildId)
}