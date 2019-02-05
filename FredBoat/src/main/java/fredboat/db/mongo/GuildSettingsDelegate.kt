package fredboat.db.mongo

import reactor.core.publisher.Mono

//TODO: what do i name this
class GuildSettingsDelegate(repo: GuildSettingsRepository) : GuildSettingsRepository by repo, CachedRepository<Long, GuildSettings>() {

    override fun findByCache(id: Long): Mono<GuildSettings> {
        return Mono.justOrEmpty<GuildSettings>(cache.getIfPresent(id))
                .switchIfEmpty(findById(id))
                .doOnNext { cache.put(id, it) }
    }

    override fun findByCacheWithDefault(id: Long): Mono<GuildSettings> {
        return Mono.justOrEmpty<GuildSettings>(cache.getIfPresent(id))
                .switchIfEmpty(findById(id))
                .defaultIfEmpty(GuildSettings(id))
                .doOnNext { cache.put(id, it) }
    }

    override fun saveWithCache(entity: GuildSettings): Mono<GuildSettings> {
        cache.put(entity.guildId, entity)

        return save(entity)
    }
}