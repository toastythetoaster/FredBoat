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

package fredboat.feature.metrics

import com.fredboat.sentinel.entities.ApplicationInfo
import fredboat.agent.StatsAgent
import fredboat.audio.player.PlayerRegistry
import fredboat.util.DiscordUtil
import org.springframework.stereotype.Service

/**
 * Metrics for the whole FredBoat.
 *
 * Sets up stats actions for the various metrics that we want to count proactively on our own terms instead of whenever
 * Prometheus scrapes.
 */
@Service
class BotMetrics(
        private val statsAgent: StatsAgent,
        private val applicationInfo: ApplicationInfo,
        private val playerRegistry: PlayerRegistry
) {
    val dockerStats = DockerStats()
    val musicPlayerStats = MusicPlayerStats()


    init {

        start()
    }

    private fun start() {
        if (DiscordUtil.isOfficialBot(applicationInfo.botId)) {
            try {
                dockerStats.fetch()
            } catch (ignored: Exception) {
            }

            statsAgent.addAction(StatsAgent.ActionAdapter("docker stats for fredboat", Runnable { dockerStats.fetch() }))
        }

        try {
            musicPlayerStats.count(playerRegistry)
        } catch (ignored: Exception) {
        }

        statsAgent.addAction(StatsAgent.ActionAdapter("music player stats for fredboat"
        ) { musicPlayerStats.count(playerRegistry) })
    }
}
