package fredboat.audio.lavalink

import fredboat.config.property.AppConfig
import fredboat.sentinel.Sentinel
import lavalink.client.io.Lavalink
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
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

interface JdaConfig {
    var discordToken: String
    var shardStart: Int
    var shardEndExcl: Int
    var shardCount: Int
}

@Component
@ConfigurationProperties(prefix = "sentinel")
class JdaConfigProperties : JdaConfig {
    override var discordToken = ""
    override var shardStart = 0
    override var shardEndExcl = 1
    override var shardCount = 1
}