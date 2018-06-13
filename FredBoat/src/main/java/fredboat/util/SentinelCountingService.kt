package fredboat.util

import fredboat.config.property.AppConfig
import fredboat.sentinel.Sentinel
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.core.publisher.toMono

@Service
class SentinelCountingService(private val sentinel: Sentinel, appConfig: AppConfig) {

    /** Rough average users per shard + 5000 (for good measure) all timed the max number of shards */
    private val estimatedUsers = (30000 + 5000) * appConfig.shardCount

    private var cachedCounts: Counts = Counts(0,0,0,0,0,0)
    private var cachedUserCount = 0
    private var countsCacheTime = 0L
    private var userCountCacheTime = 0L

    companion object {
        private const val COUNTS_TIMEOUT = 60000 // 1 minute
        private const val USERS_TIMEOUT = 10 * 60000 // 10 minutes
    }

    fun getCounts(): Mono<Counts> {
        if(countsCacheTime + COUNTS_TIMEOUT > System.currentTimeMillis()) return cachedCounts.toMono()

        return Mono.create { sink ->
            var guilds = 0L
            var roles = 0L
            var textChannels = 0L
            var voiceChannels = 0L
            var categories = 0L
            var emotes = 0L

            sentinel.getAllSentinelInfo(includeShards = false)
                    .doOnComplete {
                        sink.success(Counts(guilds, roles, textChannels, voiceChannels, categories, emotes))
                    }
                    .doOnError { sink.error(it) }
                    .subscribe {
                        guilds += it.response.guilds
                        roles += it.response.roles
                        textChannels += it.response.textChannels
                        voiceChannels += it.response.voiceChannels
                        categories += it.response.categories
                        emotes += it.response.emotes
                    }
        }
    }

    /**
     * The day that we reach 2,147,483,647 users will be a glorious one
     */
    fun getUniqueUserCount(): Mono<Int> {
        if(userCountCacheTime + USERS_TIMEOUT > System.currentTimeMillis()) return cachedUserCount.toMono()

        return Mono.create { sink ->
            val set = LongOpenHashSet(estimatedUsers)
            sentinel.getFullSentinelUserList()
                    .doOnComplete { sink.success(set.size) }
                    .doOnError { sink.error(it) }
                    .subscribe { set.add(it) }
        }
    }

    data class Counts(
            val guilds: Long,
            val roles: Long,
            val textChannels: Long,
            val voiceChannels: Long,
            val categories: Long,
            val emotes: Long
    )
}