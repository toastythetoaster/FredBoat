package fredboat.audio.queue.tbd

import fredboat.audio.queue.AudioTrackContext
import java.util.concurrent.ConcurrentLinkedDeque

interface IShufflableQueueHandler : IQueueHandler {

    val shuffleQueue: ConcurrentLinkedDeque<AudioTrackContext>?

    var shuffle: Boolean

    fun reshuffle()
}