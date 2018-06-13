/*
 * MIT License
 *
 * Copyright (c) 2017-2018 Frederik Ar. Mikkelsen
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package fredboat.event

import com.fredboat.sentinel.entities.LifecycleEventEnum.READIED
import com.fredboat.sentinel.entities.LifecycleEventEnum.SHUTDOWN
import com.fredboat.sentinel.entities.ShardLifecycleEvent
import fredboat.audio.player.PlayerRegistry
import fredboat.config.property.AppConfig
import fredboat.sentinel.Guild
import fredboat.util.DiscordUtil
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Collectors
import java.util.stream.Stream

/**
 * Created by napster on 24.02.18.
 */
@Component
class ShardReviveHandler(
        private val playerRegistry: PlayerRegistry,
        private val appConfig: AppConfig
) : SentinelEventHandler() {

    companion object {
        private val log = LoggerFactory.getLogger(ShardReviveHandler::class.java)
    }

    private val channelsToRejoin = ConcurrentHashMap<Int, MutableList<ChannelReference>>()

    override fun onShardLifecycle(event: ShardLifecycleEvent) {
        @Suppress("NON_EXHAUSTIVE_WHEN")
        when (event.change) {
            READIED -> {
                //Rejoin old channels if revived
                val channels = channelsToRejoin.computeIfAbsent(event.shard.id, { ArrayList(it) })
                val toRejoin = ArrayList(channels)
                channels.clear()//avoid situations where this method is called twice with the same channels

                toRejoin.forEach { ref ->
                    val channel = ref.guild.getVoiceChannel(ref.channelId) ?: return@forEach
                    val player = playerRegistry.getOrCreate(channel.guild)
                    channel.connect()
                    player.play()
                }
            }
            SHUTDOWN -> {
                try {
                    val shardId = event.shard.id
                    channelsToRejoin[shardId] = playerRegistry.playingPlayers.stream()
                            .filter { DiscordUtil.getShardId(it.guildId, appConfig) == shardId }
                            .flatMap {
                                val channel = it.currentVoiceChannel ?: return@flatMap Stream.empty<ChannelReference>()
                                return@flatMap Stream.of(ChannelReference(channel.guild, channel.id))
                            }.collect(Collectors.toList())
                } catch (ex: Exception) {
                    log.error("Caught exception while saving channels to revive shard {}", event.shard, ex)
                }
            }
        }
    }

    data class ChannelReference(
            val guild: Guild,
            val channelId: Long
    )
}
