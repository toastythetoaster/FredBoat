/*
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
 *
 */
package fredboat.event;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import fredboat.audio.player.GuildPlayer;
import fredboat.audio.player.PlayerRegistry;
import fredboat.command.info.HelloCommand;
import fredboat.command.info.HelpCommand;
import fredboat.command.info.ShardsCommand;
import fredboat.command.info.StatsCommand;
import fredboat.commandmeta.CommandContextParser;
import fredboat.commandmeta.CommandInitializer;
import fredboat.commandmeta.CommandManager;
import fredboat.commandmeta.abs.CommandContext;
import fredboat.config.SentryConfiguration;
import fredboat.config.property.AppConfig;
import fredboat.db.api.GuildConfigService;
import fredboat.db.api.GuildDataService;
import fredboat.db.transfer.GuildData;
import fredboat.definitions.Module;
import fredboat.definitions.PermissionLevel;
import fredboat.feature.I18n;
import fredboat.feature.metrics.Metrics;
import fredboat.jda.JdaEntityProvider;
import fredboat.messaging.CentralMessaging;
import fredboat.perms.PermsUtil;
import fredboat.util.DiscordUtil;
import fredboat.util.ratelimit.Ratelimiter;
import io.prometheus.client.Summary;
import io.prometheus.client.guava.cache.CacheMetricsCollector;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.ReadyEvent;
import net.dv8tion.jda.core.events.guild.GuildJoinEvent;
import net.dv8tion.jda.core.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.core.events.guild.voice.GuildVoiceJoinEvent;
import net.dv8tion.jda.core.events.guild.voice.GuildVoiceLeaveEvent;
import net.dv8tion.jda.core.events.guild.voice.GuildVoiceMoveEvent;
import net.dv8tion.jda.core.events.http.HttpRequestEvent;
import net.dv8tion.jda.core.events.message.MessageDeleteEvent;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.events.message.priv.PrivateMessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;

@Component
public class EventListenerBoat extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(EventListenerBoat.class);

    //first string is the users message ID, second string the id of fredboat's message that should be deleted if the
    // user's message is deleted
    public static final Cache<Long, Long> messagesToDeleteIfIdDeleted = CacheBuilder.newBuilder()
            .recordStats()
            .expireAfterWrite(6, TimeUnit.HOURS)
            .build();

    private final CommandManager commandManager;
    private final CommandContextParser commandContextParser;
    private final PlayerRegistry playerRegistry;
    private final JdaEntityProvider jdaEntityProvider;
    private final Ratelimiter ratelimiter;
    private final AppConfig appConfig;
    private final GuildDataService guildDataService;
    private final GuildConfigService guildConfigService;

    public EventListenerBoat(CommandManager commandManager, CommandContextParser commandContextParser,
                             PlayerRegistry playerRegistry, CacheMetricsCollector cacheMetrics,
                             JdaEntityProvider jdaEntityProvider, Ratelimiter ratelimiter, AppConfig appConfig,
                             GuildDataService guildDataService, GuildConfigService guildConfigService) {
        this.commandManager = commandManager;
        this.commandContextParser = commandContextParser;
        this.playerRegistry = playerRegistry;
        this.jdaEntityProvider = jdaEntityProvider;
        this.ratelimiter = ratelimiter;
        this.appConfig = appConfig;
        this.guildDataService = guildDataService;
        this.guildConfigService = guildConfigService;
        cacheMetrics.addCache("messagesToDeleteIfIdDeleted", messagesToDeleteIfIdDeleted);
    }

    @Override
    public void onGuildVoiceMove(GuildVoiceMoveEvent event) {
        checkForAutoPause(event.getChannelLeft());
        checkForAutoResume(event.getChannelJoined(), event.getMember());

        //were we moved?
        if (event.getMember().getUser().getIdLong() == event.getJDA().getSelfUser().getIdLong()) {
            checkForAutoPause(event.getChannelJoined());
        }
    }

    @Override
    public void onGuildVoiceJoin(GuildVoiceJoinEvent event) {
        checkForAutoResume(event.getChannelJoined(), event.getMember());
    }

    private void checkForAutoResume(VoiceChannel joinedChannel, Member joined) {
        Guild guild = joinedChannel.getGuild();
        //ignore bot users that arent us joining / moving
        if (joined.getUser().isBot()
                && guild.getSelfMember().getUser().getIdLong() != joined.getUser().getIdLong()) return;

        GuildPlayer player = playerRegistry.getExisting(guild);

        if (player != null
                && player.isPaused()
                && player.getPlayingTrack() != null
                && joinedChannel.getMembers().contains(guild.getSelfMember())
                && player.getHumanUsersInCurrentVC().size() > 0
                && guildConfigService.fetchGuildConfig(guild).isAutoResume()
                ) {
            player.setPause(false);
            TextChannel activeTextChannel = player.getActiveTextChannel();
            if (activeTextChannel != null) {
                CentralMessaging.message(activeTextChannel, I18n.get(guild).getString("eventAutoResumed"))
                        .send(null);
            }
        }
    }

    // TODO: Move to Sentinel
    @Override
    public void onHttpRequest(HttpRequestEvent event) {
        if (event.getResponse().code >= 300) {
            log.warn("Unsuccessful JDA HTTP Request:\n{}\nResponse:{}\n",
                    event.getRequestRaw(), event.getResponseRaw());
        }
    }

    private void sendHelloOnJoin(@Nonnull Guild guild) {
        //filter guilds that already received a hello message
        // useful for when discord trolls us with fake guild joins
        // or to prevent it send repeatedly due to kick and reinvite
        GuildData gd = guildDataService.fetchGuildData(guild);
        if (gd.getTimestampHelloSent() > 0) {
            return;
        }

        TextChannel channel = guild.getTextChannelById(guild.getIdLong()); //old public channel
        if (channel == null || !channel.canTalk()) {
            //find first channel that we can talk in
            for (TextChannel tc : guild.getTextChannels()) {
                if (tc.canTalk()) {
                    channel = tc;
                    break;
                }
            }
        }
        if (channel == null) {
            //no channel found to talk in
            return;
        }

        //send actual hello message and persist on success
        CentralMessaging.message(channel, HelloCommand.getHello(guild))
                .success(__ -> guildDataService.transformGuildData(guild, GuildData::helloSent))
                .send(null);
    }
}
