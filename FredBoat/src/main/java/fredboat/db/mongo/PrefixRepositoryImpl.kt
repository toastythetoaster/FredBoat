package fredboat.db.mongo

import fredboat.db.api.PrefixRepository
import fredboat.db.transfer.Prefix
import java.util.*

class PrefixRepositoryImpl(repo: InternalPrefixRepository): BaseRepositoryImpl<Prefix>(repo), PrefixRepository {

    override fun default(id: Long): Prefix {
        return Prefix(id)
    }

    override fun getOptional(id: Long): Optional<String> {
        return Optional.ofNullable(fetch(id).block()?.prefix)
    }

}