package fredboat.db.api

import fredboat.db.transfer.GuildSettings
import reactor.core.publisher.Mono
import java.util.*

interface GuildSettingsRepository : BaseRepository<GuildSettings>