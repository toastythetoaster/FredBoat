package fredboat.config

import fredboat.db.api.*
import fredboat.db.mongo.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RepositoriesConfiguration {

    @Bean
    fun guildSettingsRepository(repo: InternalGuildSettingsRepository): GuildSettingsRepository {
        return GuildSettingsRepositoryImpl(repo)
    }

    @Bean
    fun blacklistRepository(repo: InternalBlacklistRepository): BlacklistRepository {
        return BlacklistRepositoryImpl(repo)
    }
}