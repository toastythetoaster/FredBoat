package fredboat.db.mongo

import fredboat.db.api.GuildSettingsRepository
import fredboat.db.transfer.GuildSettings
import java.util.*

class GuildSettingsRepositoryImpl(repo: InternalGuildSettingsRepository) : BaseRepositoryImpl<GuildSettings>(repo), GuildSettingsRepository {

    override fun default(id: Long): GuildSettings {
        return GuildSettings(id)
    }

}