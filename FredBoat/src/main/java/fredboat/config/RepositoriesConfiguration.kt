package fredboat.config

import com.google.common.cache.CacheBuilder
import fredboat.db.api.*
import fredboat.db.mongo.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

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

    @Bean
    fun searchResultRepository(repo: InternalSearchResultRepository): SearchResultRepository {
        return SearchResultRepositoryImpl(repo)
    }
}