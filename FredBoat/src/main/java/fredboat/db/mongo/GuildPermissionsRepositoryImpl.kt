package fredboat.db.mongo

import fredboat.db.api.GuildPermissionsRepository
import fredboat.db.transfer.GuildPermissions

class GuildPermissionsRepositoryImpl(repo: InternalGuildPermissionRepository) : BaseRepositoryImpl<GuildPermissions>(repo), GuildPermissionsRepository {

    override fun default(id: Long): GuildPermissions {
        return GuildPermissions(id)
    }

}