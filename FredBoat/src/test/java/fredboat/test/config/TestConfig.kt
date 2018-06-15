package fredboat.test.config

import fredboat.config.ApplicationInfo
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration
open class TestConfig {

    @Bean
    @Primary
    open fun applicationInfo() = ApplicationInfo(
            168672778860494849,
            false,
            "The best bot",
            "bdc4465f37fde2d04335d388076ece26",
            "FredBoatβ",
            81011298891993088,
            false
    )

}