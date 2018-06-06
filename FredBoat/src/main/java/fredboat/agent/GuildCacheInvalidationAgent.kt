package fredboat.agent

import fredboat.sentinel.GuildCache
import fredboat.sentinel.InternalGuild
import lavalink.client.io.Link
import org.springframework.stereotype.Controller
import java.util.concurrent.TimeUnit

@Controller
class GuildCacheInvalidationAgent(
        private val guildCache: GuildCache
) : FredBoatAgent("cache-invalidator", 10, TimeUnit.MINUTES) {

    companion object {
        private const val TIMEOUT_MILLIS: Long = 30 * 60 * 1000 // 30 minutes
    }

    override fun doRun() {
        val keysToRemove = mutableListOf<Long>()
        guildCache.cache.forEach { key, guild ->
            if (!guild.shouldInvalidate()) return@forEach
            keysToRemove.add(key)
            guild.doBeforeInvalidation()
        }
    }

    private fun InternalGuild.shouldInvalidate(): Boolean {
        // Has this guild been used recently?
        if (lastUsed + TIMEOUT_MILLIS > System.currentTimeMillis()) return false

        // Are we connected to voice?
        if (link.state == Link.State.CONNECTED) return false

        // If not then invalidate
        return true
    }

    private fun InternalGuild.doBeforeInvalidation() {
        link.destroy()
    }

}