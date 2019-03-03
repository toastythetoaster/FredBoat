package fredboat.audio.queue.tbd

import fredboat.audio.queue.AudioTrackContext
import fredboat.definitions.RepeatMode

interface IRepeatableQueueHandler : IRoundRobinQueueHandler {

    val lastTrack: AudioTrackContext?

    var repeat: RepeatMode

}