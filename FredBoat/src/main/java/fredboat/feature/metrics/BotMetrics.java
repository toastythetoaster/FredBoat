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

package fredboat.feature.metrics;

import fredboat.agent.StatsAgent;
import fredboat.audio.player.PlayerRegistry;
import fredboat.config.property.Credentials;
import fredboat.jda.GuildProvider;
import fredboat.jda.ShardProvider;
import fredboat.util.DiscordUtil;
import net.dv8tion.jda.core.JDA;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Metrics for the whole FredBoat.
 *
 * Sets up stats actions for the various metrics that we want to count proactively on our own terms instead of whenever
 * Prometheus scrapes.
 */
@Component
public class BotMetrics {

    private final StatsAgent statsAgent;
    private final ShardProvider shardProvider;
    private final Credentials credentials;
    private final GuildProvider guildProvider;
    private final PlayerRegistry playerRegistry;
    private final JdaEntityStats jdaEntityStatsTotal = new JdaEntityStats();
    private final DockerStats dockerStats = new DockerStats();
    private final MusicPlayerStats musicPlayerStats = new MusicPlayerStats();
    private final GuildSizes guildSizes = new GuildSizes();


    public BotMetrics(StatsAgent statsAgent, ShardProvider shardProvider, Credentials credentials,
                      GuildProvider guildProvider, PlayerRegistry playerRegistry) {
        this.statsAgent = statsAgent;
        this.shardProvider = shardProvider;
        this.credentials = credentials;
        this.guildProvider = guildProvider;
        this.playerRegistry = playerRegistry;

        start();
    }

    @Nonnull
    public JdaEntityStats getJdaEntityStatsTotal() {
        return jdaEntityStatsTotal;
    }

    @Nonnull
    public DockerStats getDockerStats() {
        return dockerStats;
    }

    @Nonnull
    public MusicPlayerStats getMusicPlayerStats() {
        return musicPlayerStats;
    }

    @Nonnull
    public GuildSizes getGuildSizes() {
        return guildSizes;
    }

    private void start() {
        Supplier<Collection<JDA>> shardsSupplier = () -> shardProvider.streamShards().collect(Collectors.toList());
        try {
            jdaEntityStatsTotal.count(shardsSupplier);
        } catch (Exception ignored) {}

        statsAgent.addAction(new StatsAgent.ActionAdapter("jda entity stats for fredboat",
                () -> jdaEntityStatsTotal.count(shardsSupplier)));

        if (DiscordUtil.INSTANCE.isOfficialBot(credentials)) {
            try {
                dockerStats.fetch();
            } catch (Exception ignored) {}
            statsAgent.addAction(new StatsAgent.ActionAdapter("docker stats for fredboat", dockerStats::fetch));
        }

        try {
            musicPlayerStats.count(playerRegistry);
        } catch (Exception ignored) {}
        statsAgent.addAction(new StatsAgent.ActionAdapter("music player stats for fredboat",
                () -> musicPlayerStats.count(playerRegistry)));

        try {
            guildSizes.count(guildProvider::streamGuilds);
        } catch (Exception ignored) {}
        statsAgent.addAction(new StatsAgent.ActionAdapter("guild sizes for fredboat",
                () -> guildSizes.count(guildProvider::streamGuilds)));
    }
}
