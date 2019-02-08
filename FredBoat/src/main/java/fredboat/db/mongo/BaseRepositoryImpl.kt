package fredboat.db.mongo

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import fredboat.db.api.BaseRepository
import fredboat.db.transfer.MongoEntry
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Mono
import java.util.concurrent.TimeUnit

abstract class BaseRepositoryImpl<T : MongoEntry>(private val repo: ReactiveCrudRepository<T, Long>) : ReactiveCrudRepository<T, Long> by repo, BaseRepository<T> {

    private val cache: Cache<Long, T> = CacheBuilder.newBuilder()
            .expireAfterAccess(60, TimeUnit.SECONDS)
            .expireAfterWrite(120, TimeUnit.SECONDS)
            .build<Long, T>()

    override fun fetch(id: Long): Mono<T> {
        return Mono.justOrEmpty<T>(cache.getIfPresent(id))
                .switchIfEmpty(repo.findById(id))
                .defaultIfEmpty(default(id))
                .doOnSuccess { cache.put(id, it) }
    }

    override fun update(mono: Mono<T>): Mono<T> {
        return mono.flatMap { cache.put(it.id, it); save(it) }
    }

    override fun update(target: T): Mono<T> {
        return repo.save(target).doOnSuccess { cache.put(target.id, target) }
    }

    override fun delete(id: Long): Mono<Void> {
        return repo.deleteById(id)
    }
}