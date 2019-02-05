package fredboat.db.mongo

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Mono

abstract class CachedRepository<ID, T> : ReactiveCrudRepository<T, ID> {

    protected val cache: Cache<ID, T> = CacheBuilder.newBuilder()
            .build<ID, T>()

    abstract fun findByCache(id: ID): Mono<T>

    abstract fun findByCacheWithDefault(id: ID): Mono<T>

    abstract fun saveWithCache(entity: T): Mono<T>
}