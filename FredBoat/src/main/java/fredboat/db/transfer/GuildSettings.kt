package fredboat.db.transfer

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "GuildSettings")
data class GuildSettings(
        @Id
        override val id: Long,
        var trackAnnounce: Boolean = false,
        var autoResume: Boolean = false,
        var allowPublicPlayerInfo: Boolean = false,
        var lang: String = "en_US"
) : MongoEntry