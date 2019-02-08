package fredboat.db.api

import fredboat.db.transfer.Prefix
import java.util.*

interface PrefixRepository : BaseRepository<Prefix> {

    fun getOptional(id: Long): Optional<String>

}