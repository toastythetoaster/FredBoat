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

package fredboat.test.config

import fredboat.config.property.*
import fredboat.shared.constant.DistributionEnum
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

/**
 * Created by napster on 19.02.18.
 *
 *
 * A default fake config to be used in tests.
 */
@Component
@Primary
class MockConfig : AppConfig, AudioSourcesConfig, Credentials, EventLoggerConfig, LavalinkConfig {

    private val distributionEnum = DistributionEnum.DEVELOPMENT

    override fun getDistribution() = distributionEnum

    override fun getAdminIds() = emptyList<Long>()

    override fun useAutoBlacklist() = false

    override fun getGame() = "Passing all tests"

    override fun getContinuePlayback() = false

    override fun getPlayerLimit() = -1

    override fun isYouTubeEnabled() = false

    override fun isSoundCloudEnabled() = false

    override fun isBandCampEnabled() = false

    override fun isTwitchEnabled() = false

    override fun isVimeoEnabled() = false

    override fun isMixerEnabled() = false

    override fun isSpotifyEnabled() = false

    override fun isLocalEnabled() = false

    override fun isHttpEnabled() = false

    override fun getBotToken() = ""

    override fun getGoogleKeys() = emptyList<String>()

    override fun getMalUser() = ""

    override fun getMalPassword() = ""

    override fun getImgurClientId() = ""

    override fun getSpotifyId() = ""

    override fun getSpotifySecret() = ""

    override fun getOpenWeatherKey() = ""

    override fun getSentryDsn() = ""

    override fun getNodes() = listOf(LavalinkConfig.LavalinkNode().apply {
        name = "test-node"
        setPass("youshallnotpass")
        setHost("ws://localhost:5555")
    })

    override fun getEventLogWebhook() = ""

    override fun getEventLogInterval() = 1

    override fun getGuildStatsWebhook() = ""

    override fun getGuildStatsInterval() = 1

    override fun getCarbonKey() = ""
}
