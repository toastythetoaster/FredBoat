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

package fredboat.command.config

import fredboat.command.info.HelpCommand
import fredboat.commandmeta.abs.Command
import fredboat.commandmeta.abs.CommandContext
import fredboat.commandmeta.abs.ICommandRestricted
import fredboat.commandmeta.abs.IConfigCommand
import fredboat.db.mongo.GuildSettings
import fredboat.db.mongo.GuildSettingsDelegate
import fredboat.definitions.PermissionLevel
import fredboat.messaging.internal.Context
import fredboat.perms.PermsUtil
import fredboat.util.extension.escapeAndDefuse
import fredboat.util.localMessageBuilder
import kotlinx.coroutines.reactive.awaitSingle

class ConfigCommand(name: String, private val repo: GuildSettingsDelegate, vararg aliases: String) : Command(name, *aliases), IConfigCommand, ICommandRestricted {

    override val minimumPerms: PermissionLevel
        get() = PermissionLevel.BASE

    override suspend fun invoke(context: CommandContext) {
        if (!context.hasArguments()) {
            printConfig(context)
        } else {
            setConfig(context)
        }
    }

    private fun printConfig(context: CommandContext) {
        repo.findByCacheWithDefault(context.guild.id)
                .subscribe {
                    val mb = localMessageBuilder()
                            .append(context.i18nFormat("configNoArgs", context.guild.name)).append("\n")
                            .append("track_announce = ${it.trackAnnounce}\n")
                            .append("auto_resume = ${it.autoResume}\n")
                            .append("```") //opening ``` is part of the configNoArgs language string

                    context.reply(mb.build())
                }
    }

    private suspend fun setConfig(context: CommandContext) {
        val invoker = context.member
        if (!PermsUtil.checkPermsWithFeedback(PermissionLevel.ADMIN, context)) {
            return
        }

        if (context.args.size != 2) {
            HelpCommand.sendFormattedCommandHelp(context)
            return
        }

        val key = context.args[0]
        val value = context.args[1]

        if (!(value.equals("true", ignoreCase = true) or value.equals("false", ignoreCase = true))) {
            context.reply(context.i18nFormat("configMustBeBoolean", invoker.effectiveName.escapeAndDefuse()))
            return
        }

        val guildSettings = repo.findByCacheWithDefault(context.guild.id).awaitSingle()

        when (key) {
            "track_announce" -> {
                guildSettings.trackAnnounce = value.toBoolean()

                repo.saveWithCache(guildSettings).subscribe {
                    context.replyWithName("`track_announce` " + context.i18nFormat("configSetTo", value))
                }
            }
            "auto_resume" -> {
                guildSettings.autoResume = value.toBoolean()

                repo.saveWithCache(guildSettings).subscribe {
                    context.replyWithName("`auto_resume` " + context.i18nFormat("configSetTo", value))
                }
            }
            else -> context.reply(context.i18nFormat("configUnknownKey", invoker.effectiveName.escapeAndDefuse()))
        }
    }

    override fun help(context: Context): String {
        val usage = "{0}{1} OR {0}{1} <key> <value>\n#"
        return usage + context.i18n("helpConfigCommand")
    }
}
