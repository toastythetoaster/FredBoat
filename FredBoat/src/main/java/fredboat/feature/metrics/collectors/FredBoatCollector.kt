/*
 *
 * MIT License
 *
 * Copyright (c) 2017 Frederik Ar. Mikkelsen
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

package fredboat.feature.metrics.collectors

import fredboat.feature.metrics.BotMetrics
import fredboat.util.SentinelCountingService
import io.prometheus.client.Collector
import io.prometheus.client.CounterMetricFamily
import io.prometheus.client.GaugeMetricFamily
import org.springframework.stereotype.Component
import java.util.*

/**
 * Created by napster on 19.10.17.
 *
 * Collects various FredBoat stats for prometheus
 */
@Component
class FredBoatCollector(
        private val botMetrics: BotMetrics,
        private val sentinelCountingService: SentinelCountingService
) : Collector() {

    override fun collect(): List<Collector.MetricFamilySamples> {

        val mfs = ArrayList<Collector.MetricFamilySamples>()
        //NOTE: shard specific metrics have been disabled, because we were not really using them, but they take up a ton
        // of samples (Example: 800 shards x 7 metrics = 5600 unused samples). the label has been kept to not break
        // existing graphs. Use "total" for it when adding botwide metrics.
        val labelNames = Arrays.asList("shard", "entity")

        val jdaEntities = GaugeMetricFamily("fredboat_jda_entities",
                "Amount of JDA entities", labelNames)
        mfs.add(jdaEntities)

        val playersPlaying = GaugeMetricFamily("fredboat_playing_music_players",
                "Currently playing music players", labelNames)
        mfs.add(playersPlaying)

        val dockerPulls = CounterMetricFamily("fredboat_docker_pulls",
                "Total fredboat docker image pulls as reported by the docker hub.", labelNames)
        mfs.add(dockerPulls)

        //global jda entity stats
        sentinelCountingService.getAllCounts().subscribe() // Make sure we cache new values, so we can get them later
        val countsPair = sentinelCountingService.getAllCountsCached() // We can't block in webflux, so we just use cached values
        val counts = countsPair.first
        jdaEntities.addMetric(listOf("total", "User"), countsPair.second.toDouble())
        jdaEntities.addMetric(listOf("total", "Guild"), counts.guilds.toDouble())
        jdaEntities.addMetric(listOf("total", "TextChannel"), counts.textChannels.toDouble())
        jdaEntities.addMetric(listOf("total", "VoiceChannel"), counts.voiceChannels.toDouble())
        jdaEntities.addMetric(listOf("total", "Category"), counts.categories.toDouble())
        jdaEntities.addMetric(listOf("total", "Emote"), counts.emotes.toDouble())
        jdaEntities.addMetric(listOf("total", "Role"), counts.roles.toDouble())

        //music player stats
        val musicPlayerStats = botMetrics.musicPlayerStats
        if (musicPlayerStats.isCounted) {
            playersPlaying.addMetric(Arrays.asList("total", "Players"), musicPlayerStats.playing.toDouble()) //entity could be better named "PlayingPlayers", but dont break existing graphs...besides, players will hopefully one day be stateless entities in the database instead of paused objects in the JVM.
            playersPlaying.addMetric(Arrays.asList("total", "TotalPlayers"), musicPlayerStats.total.toDouble())
        }

        //docker stats
        val dockerStats = botMetrics.dockerStats
        if (dockerStats.isFetched) {
            dockerPulls.addMetric(Arrays.asList("total", "Bot"), dockerStats.dockerPullsBot.toDouble())
            dockerPulls.addMetric(Arrays.asList("total", "Db"), dockerStats.dockerPullsDb.toDouble())
        }

        return mfs
    }
}
