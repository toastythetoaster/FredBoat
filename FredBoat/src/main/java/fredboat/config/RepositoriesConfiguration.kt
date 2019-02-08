package fredboat.config

import fredboat.db.api.BlacklistRepository
import fredboat.db.api.GuildPermissionsRepository
import fredboat.db.api.GuildSettingsRepository
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
    fun guildPermissionsRepository(repo: InternalGuildPermissionRepository): GuildPermissionsRepository {
        return GuildPermissionsRepositoryImpl(repo)
    }

    @Bean
    fun blacklistRepository(repo: InternalBlacklistRepository): BlacklistRepository {
        return BlacklistRepositoryImpl(repo)
    }
}