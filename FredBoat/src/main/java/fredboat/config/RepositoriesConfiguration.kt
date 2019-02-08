package fredboat.config

import fredboat.db.api.GuildPermissionsRepository
import fredboat.db.api.GuildSettingsRepository
import fredboat.db.mongo.GuildPermissionsRepositoryImpl
import fredboat.db.mongo.GuildSettingsRepositoryImpl
import fredboat.db.mongo.InternalGuildPermissionRepository
import fredboat.db.mongo.InternalGuildSettingsRepository
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
}