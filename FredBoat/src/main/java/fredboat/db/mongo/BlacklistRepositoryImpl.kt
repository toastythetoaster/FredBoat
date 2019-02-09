package fredboat.db.mongo

import fredboat.db.api.BlacklistRepository
import fredboat.db.transfer.BlacklistEntity

class BlacklistRepositoryImpl(repo: InternalBlacklistRepository) : BaseRepositoryImpl<Long, BlacklistEntity>(repo), BlacklistRepository {

    override fun default(id: Long): BlacklistEntity? {
        return BlacklistEntity(id)
    }
}