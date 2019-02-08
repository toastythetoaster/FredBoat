package fredboat.db.api

import fredboat.db.transfer.GuildSettings
import reactor.core.publisher.Mono

interface GuildSettingsRepository : BaseRepository<GuildSettings>