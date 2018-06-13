package fredboat.test.config

import com.fredboat.sentinel.entities.ApplicationInfo
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration
open class TestConfig {

    @Bean
    @Primary
    open fun applicationInfo() = ApplicationInfo(
            168672778860494849,
            152691313123393536,
            false,
            "The best bot",
            "bdc4465f37fde2d04335d388076ece26",
            "https://cdn.discordapp.com/avatars/152691313123393536/bdc4465f37fde2d04335d388076ece26.png",
            "FredBoatβ",
            81011298891993088,
            false
    )

}