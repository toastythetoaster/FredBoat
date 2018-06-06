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

package fredboat.util;

import fredboat.config.property.AppConfig;
import fredboat.config.property.Credentials;
import fredboat.sentinel.Member;
import fredboat.sentinel.Role;
import fredboat.shared.constant.BotConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class DiscordUtil {

    private static final Logger log = LoggerFactory.getLogger(DiscordUtil.class);

    private DiscordUtil() {}

    public static int getHighestRolePosition(Member member) {
        List<Role> roles = member.getRoles();

        if (roles.isEmpty()) return -1;

        Role top = roles.get(0);

        for (Role r : roles) {
            if (r.getPosition() > top.getPosition()) {
                top = r;
            }
        }

        return top.getPosition();
    }

    /**
     * @return true if this bot account is an "official" fredboat (music, patron, CE, etc).
     * This is useful to lock down features that we only need internally, like polling the docker hub for pull stats.
     */
    public static boolean isOfficialBot(Credentials credentials) {
        long botId = getBotId(credentials);
        return botId == BotConstants.MUSIC_BOT_ID
                || botId == BotConstants.PATRON_BOT_ID
                || botId == BotConstants.CUTTING_EDGE_BOT_ID
                || botId == BotConstants.BETA_BOT_ID
                || botId == BotConstants.MAIN_BOT_ID;
    }

    //https://discordapp.com/developers/docs/topics/gateway#sharding
    public static int getShardId(long guildId, AppConfig appConfig) {
        return (int) ((guildId >> 22) % appConfig.getShardCount());
    }
}
