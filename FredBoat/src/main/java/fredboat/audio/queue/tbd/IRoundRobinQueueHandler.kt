package fredboat.audio.queue.tbd

import fredboat.audio.queue.AudioTrackContext
import java.util.concurrent.ConcurrentLinkedDeque

interface IRoundRobinQueueHandler : IShufflableQueueHandler {

    var roundRobinQueue: ConcurrentLinkedDeque<AudioTrackContext>

    var roundRobin: Boolean
}