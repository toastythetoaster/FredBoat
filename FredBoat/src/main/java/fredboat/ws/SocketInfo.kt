package fredboat.ws

import fredboat.audio.player.GuildPlayer
import fredboat.sentinel.Guild
import fredboat.sentinel.GuildCache

data class SocketInfo(private val guildCache: GuildCache, var guildId: Long) {
    val guild: Guild? get() = guildCache.getIfCached(guildId)
    val player: GuildPlayer? get() = guild?.guildPlayer
}