package fredboat.db.api

import fredboat.db.transfer.GuildPermissions
import reactor.core.publisher.Mono

interface GuildPermissionsRepository {

    fun fetch(guild: Long): Mono<GuildPermissions>

    fun update(mono: Mono<GuildPermissions>): Mono<GuildPermissions>

    fun update(permissions: GuildPermissions): Mono<GuildPermissions>

    fun delete(guild: Long): Mono<Void>
}