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

package fredboat.command.util;

import fredboat.commandmeta.abs.JCommand;
import fredboat.commandmeta.abs.CommandContext;
import fredboat.commandmeta.abs.IUtilCommand;
import fredboat.main.Launcher;
import fredboat.messaging.CentralMessaging;
import fredboat.messaging.internal.Context;
import fredboat.perms.PermsUtil;
import fredboat.util.ArgumentUtil;
import fredboat.util.TextUtils;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;

import javax.annotation.Nonnull;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by midgard on 17/01/20.
 */
public class UserInfoCommand extends JCommand implements IUtilCommand {

    public UserInfoCommand(String name, String... aliases) {
        super(name, aliases);
    }

    @Override
    public void onInvoke(@Nonnull CommandContext context) {
        //slow execution due to guilds traversal
        Launcher.getBotController().getExecutor().execute(() -> invoke(context));
    }

    public void invoke(@Nonnull CommandContext context) {
        Member target;
        StringBuilder knownServers = new StringBuilder();
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
        if (!context.hasArguments()) {
            target = context.getMember();
        } else {
            target = ArgumentUtil.INSTANCE.checkSingleFuzzyMemberSearchResult(context, context.getRawArgs(), true);
        }
        if (target == null) return;
        List<String> matchedGuildNames = Launcher.getBotController().getJdaEntityProvider().streamGuilds()
                .filter(guild -> guild.isMember(target.getUser()))
                .map(Guild::getName)
                .collect(Collectors.toList());

        if (matchedGuildNames.size() >= 30) {
            knownServers.append(matchedGuildNames.size());
        } else {
            int i = 0;
            for (String guildName : matchedGuildNames) {
                i++;
                knownServers.append(guildName);
                if (i < matchedGuildNames.size()) {
                    knownServers.append(",\n");
                }

            }
        }
        //DMify if I can
        context.reply(CentralMessaging.getClearThreadLocalEmbedBuilder()
                .setColor(target.getColor())
                .setThumbnail(target.getUser().getAvatarUrl())
                .setTitle(context.i18nFormat("userinfoTitle", target.getEffectiveName()), null)
                .addField(context.i18n("userinfoUsername"), TextUtils.escapeMarkdown(target.getUser().getName())
                        + "#" + target.getUser().getDiscriminator(), true)
                .addField(context.i18n("userinfoId"), target.getUser().getId(), true)
                .addField(context.i18n("userinfoNick"), TextUtils.escapeMarkdown(target.getEffectiveName()), true) //Known Nickname
                .addField(context.i18n("userinfoKnownServer"), knownServers.toString(), true) //Known Server
                .addField(context.i18n("userinfoJoinDate"), target.getJoinDate().format(dtf), true)
                .addField(context.i18n("userinfoCreationTime"), target.getUser().getCreationTime().format(dtf), true)
                .addField(context.i18n("userinfoBlacklisted"),
                        Boolean.toString(Launcher.getBotController().getRatelimiter().isBlacklisted(target.getUser().getIdLong())), true)
                .addField("Permission Level", PermsUtil.INSTANCE.getPerms(target).getName(), true) //todo i18n
                .build()
        );
    }

    @Nonnull
    @Override
    public String help(@Nonnull Context context) {
        return "{0}{1} OR {0}{1} <user>\n#" + context.i18n("helpUserInfoCommand");
    }
}
