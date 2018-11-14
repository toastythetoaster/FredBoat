package fredboat.db.mongo

import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document
class MongoPlayer(
        @Id
        val gid: Long,
        val paused: Boolean,
        val shuffled: Boolean,
        /** [fredboat.definitions.RepeatMode] ordinal */
        val repeat: Byte,
        /** 0-1 */
        val volume: Float,
        /** Time of the playing track at the time of saving in milliseconds */
        val position: Long?,
        /** Name of Lavalink node. Used for resuming */
        val node: String?,
        val queue: List<MongoTrack>
)

class MongoTrack(
        val id: ObjectId,
        val blob: ByteArray,
        val requester: Long
)