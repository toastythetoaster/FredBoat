package fredboat.db.api

import fredboat.db.transfer.GuildSettings
import reactor.core.publisher.Mono

interface GuildSettingsRepository {

    fun fetch(guild: Long): Mono<GuildSettings>

    fun update(settings: Mono<GuildSettings>): Mono<GuildSettings>

    fun update(settings: GuildSettings): Mono<GuildSettings>

    fun delete(guild: Long): Mono<Void>
}