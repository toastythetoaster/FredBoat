package fredboat.db.mongo

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "GuildSettings")
data class GuildSettings(
        @Id
        val guildId: Long,
        var allowPublicPlayerInfo: Boolean = false
)