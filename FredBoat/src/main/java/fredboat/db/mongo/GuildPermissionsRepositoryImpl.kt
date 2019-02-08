package fredboat.db.mongo

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import fredboat.db.api.GuildPermissionsRepository
import fredboat.db.transfer.GuildPermissions
import reactor.core.publisher.Mono
import java.util.concurrent.TimeUnit

class GuildPermissionsRepositoryImpl(private val repo: InternalGuildPermissionRepository) : InternalGuildPermissionRepository by repo, GuildPermissionsRepository {

    private val cache: Cache<Long, GuildPermissions> = CacheBuilder.newBuilder()
            .expireAfterAccess(60, TimeUnit.SECONDS)
            .expireAfterWrite(120, TimeUnit.SECONDS)
            .build<Long, GuildPermissions>()

    override fun fetch(guild: Long): Mono<GuildPermissions> {
        return Mono.justOrEmpty<GuildPermissions>(cache.getIfPresent(guild))
                .switchIfEmpty(repo.findById(guild))
                .defaultIfEmpty(GuildPermissions(guild))
                .doOnSuccess { cache.put(guild, it) }
    }

    override fun update(mono: Mono<GuildPermissions>): Mono<GuildPermissions> {
        return mono.flatMap { cache.put(it.guildId, it); save(it) }
    }

    override fun update(permissions: GuildPermissions): Mono<GuildPermissions> {
        return repo.save(permissions).doOnSuccess { cache.put(permissions.guildId, permissions) }
    }

    override fun delete(guild: Long): Mono<Void> {
        return repo.deleteById(guild)
    }
}