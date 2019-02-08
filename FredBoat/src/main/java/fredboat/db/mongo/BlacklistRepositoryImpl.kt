package fredboat.db.mongo

import fredboat.db.api.BlacklistRepository
import fredboat.db.transfer.BlacklistEntry

class BlacklistRepositoryImpl(repo: InternalBlacklistRepository) : BaseRepositoryImpl<BlacklistEntry>(repo), BlacklistRepository {

    override fun default(id: Long): BlacklistEntry {
        return BlacklistEntry(id)
    }
}