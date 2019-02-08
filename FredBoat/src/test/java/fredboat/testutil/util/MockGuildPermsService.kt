package fredboat.testutil.util

import fredboat.db.api.GuildPermissionsRepository
import fredboat.db.transfer.GuildPermissions
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
@Primary
class MockGuildPermsService : GuildPermissionsRepository {

    override fun default(id: Long): GuildPermissions {
        return GuildPermissions(id)
    }

    final val default: (guild: Long) -> GuildPermissions = { GuildPermissions(it) }
    var factory = default

    override fun fetch(guild: Long): Mono<GuildPermissions> {
        return Mono.just(factory(guild))
    }

    override fun update(mono: Mono<GuildPermissions>): Mono<GuildPermissions> {
        return mono.flatMap { Mono.just(factory(it.id)) }
    }

    override fun update(permissions: GuildPermissions): Mono<GuildPermissions> {
        return Mono.just(permissions)
    }

    override fun delete(guild: Long): Mono<Void> {
        return Mono.empty()
    }
}