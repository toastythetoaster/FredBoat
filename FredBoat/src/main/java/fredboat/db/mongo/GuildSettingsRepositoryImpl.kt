package fredboat.db.mongo

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import fredboat.db.api.GuildSettingsRepository
import fredboat.db.transfer.GuildSettings
import reactor.core.publisher.Mono
import java.util.concurrent.TimeUnit

class GuildSettingsRepositoryImpl(private val repo: InternalGuildSettingsRepository) : InternalGuildSettingsRepository by repo, GuildSettingsRepository {

    private val cache: Cache<Long, GuildSettings> = CacheBuilder.newBuilder()
            .expireAfterAccess(60, TimeUnit.SECONDS)
            .expireAfterWrite(120, TimeUnit.SECONDS)
            .build<Long, GuildSettings>()

    override fun fetch(guild: Long): Mono<GuildSettings> {
        return Mono.justOrEmpty<GuildSettings>(cache.getIfPresent(guild))
                .switchIfEmpty(repo.findById(guild))
                .defaultIfEmpty(GuildSettings(guild))
                .doOnSuccess { cache.put(guild, it) }
    }

    override fun update(settings: Mono<GuildSettings>):Mono <GuildSettings> {
        return settings.flatMap { cache.put(it.guildId, it); save(it) }
    }

    override fun update(settings: GuildSettings): Mono<GuildSettings> {
        return repo.save(settings).doOnSuccess { cache.put(settings.guildId, settings) }
    }

    override fun delete(guild: Long): Mono<Void> {
        return repo.deleteById(guild)
    }
}