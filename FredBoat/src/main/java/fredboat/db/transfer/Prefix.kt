package fredboat.db.transfer

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "Prefix")
class Prefix(
        @Id
        override val id: Long,
        var prefix: String? = null
) : MongoEntry