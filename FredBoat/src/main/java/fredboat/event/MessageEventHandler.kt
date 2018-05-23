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

package fredboat.event

import com.google.common.cache.CacheBuilder
import fredboat.command.info.HelpCommand
import fredboat.command.info.ShardsCommand
import fredboat.command.info.StatsCommand
import fredboat.commandmeta.CommandContextParser
import fredboat.commandmeta.CommandInitializer
import fredboat.commandmeta.CommandManager
import fredboat.commandmeta.abs.CommandContext
import fredboat.config.property.AppConfigProperties
import fredboat.definitions.PermissionLevel
import fredboat.feature.metrics.Metrics
import fredboat.perms.Permission.MESSAGE_READ
import fredboat.perms.Permission.MESSAGE_WRITE
import fredboat.perms.PermsUtil
import fredboat.sentinel.*
import fredboat.util.ratelimit.Ratelimiter
import io.prometheus.client.guava.cache.CacheMetricsCollector
import kotlinx.coroutines.experimental.async
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class MessageEventHandler(
        private val sentinel: Sentinel,
        private val ratelimiter: Ratelimiter,
        private val commandContextParser: CommandContextParser,
        private val commandManager: CommandManager,
        private val appConfig: AppConfigProperties,
        cacheMetrics: CacheMetricsCollector
) : SentinelEventHandler() {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(MessageEventHandler::class.java)
        // messageId <-> messageId
        private val messagesToDeleteIfIdDeleted = CacheBuilder.newBuilder()
                .recordStats()
                .expireAfterWrite(6, TimeUnit.HOURS)
                .build<Long, Long>()
    }

    init {
        cacheMetrics.addCache("messagesToDeleteIfIdDeleted", messagesToDeleteIfIdDeleted)
    }

    override fun onGuildMessage(channel: TextChannel, author: Member, message: Message) {
        if (ratelimiter.isBlacklisted(author.id)) {
            Metrics.blacklistedMessagesReceived.inc()
            return
        }

        if (channel.guild.selfMember.id == author.id) log.info(message.content)
        if (author.isBot) return

        //Preliminary permission filter to avoid a ton of parsing
        //Let messages pass on to parsing that contain "help" since we want to answer help requests even from channels
        // where we can't talk in
        if (!channel.checkOurPermissions(MESSAGE_READ + MESSAGE_WRITE)
                && !message.content.contains(CommandInitializer.HELP_COMM_NAME)) return

        val context = commandContextParser.parse(channel, author, message) ?: return
        log.info(message.content)

        //ignore all commands in channels where we can't write, except for the help command
        if (!channel.checkOurPermissions(MESSAGE_READ + MESSAGE_WRITE) && context.command !is HelpCommand) {
            log.info("Ignoring command {} because this bot cannot write in that channel", context.command.name)
            return
        }

        Metrics.commandsReceived.labels(context.command.javaClass.simpleName).inc()

        async {
            //ignore commands of disabled modules for plebs
            //BOT_ADMINs can always use all commands everywhere
            val module = context.command.module
            if (module != null
                    && !context.enabledModules.contains(module)
                    && !PermsUtil.checkPerms(PermissionLevel.BOT_ADMIN, author)) {
                log.debug("Ignoring command {} because its module {} is disabled",
                        context.command.name, module.name)
                return@async
            }

            limitOrExecuteCommand(context)
        }
    }

    /**
     * Check the rate limit of the user and execute the command if everything is fine.
     * @param context Command context of the command to be invoked.
     */
    private suspend fun limitOrExecuteCommand(context: CommandContext) {
        if (ratelimiter.isRatelimited(context, context.command)) {
            return
        }

        Metrics.executionTime.labels(context.command.javaClass.simpleName).startTimer().use {
            commandManager.prefixCalled(context)
        }
        //NOTE: Some commands, like ;;mal, run async and will not reflect the real performance of FredBoat
        // their performance should be judged by the totalResponseTime metric instead
    }

    override fun onPrivateMessage(author: User, content: String) {
        if (ratelimiter.isBlacklisted(author.id)) {
            Metrics.blacklistedMessagesReceived.inc()
            return
        }

        //Technically not possible anymore to receive private messages from bots but better safe than sorry
        //Also ignores our own messages since we're a bot
        if (author.isBot) {
            return
        }

        //quick n dirty bot admin / owner check
        if (appConfig.adminIds.contains(author.id) || sentinel.getApplicationInfo().ownerId == author.id) {

            //hack in / hardcode some commands; this is not meant to look clean
            val lowered = content.toLowerCase()
            if (lowered.contains("shard")) {
                for (message in ShardsCommand.getShardStatus(content)) {
                    author.sendPrivate(message).subscribe()
                }
                return
            } else if (lowered.contains("stats")) {
                author.sendPrivate(StatsCommand.getStats(null)).subscribe()
                return
            }
        }

        HelpCommand.sendGeneralHelp(author, content)
    }

    override fun onGuildMessageDelete(channel: TextChannel, messageId: Long) {
        val toDelete = messagesToDeleteIfIdDeleted.getIfPresent(messageId) ?: return
        messagesToDeleteIfIdDeleted.invalidate(toDelete)
        channel.sentinel.deleteMessages(channel, listOf(toDelete)).subscribe()
    }

}