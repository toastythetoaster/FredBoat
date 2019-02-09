package fredboat.db.mongo

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import fredboat.db.api.BaseRepository
import fredboat.db.transfer.MongoEntity
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Mono
import java.io.Serializable
import java.util.concurrent.TimeUnit

abstract class BaseRepositoryImpl<ID : Serializable, T : MongoEntity<ID>>(private val repo: ReactiveCrudRepository<T, ID>) : ReactiveCrudRepository<T, ID> by repo, BaseRepository<ID, T> {

    private val cache: Cache<ID, T> = CacheBuilder.newBuilder()
            .expireAfterAccess(60, TimeUnit.SECONDS)
            .expireAfterWrite(120, TimeUnit.SECONDS)
            .build<ID, T>()

    override fun fetch(id: ID): Mono<T> {
        return if (default(id) != null)
            Mono.justOrEmpty<T>(cache.getIfPresent(id))
                    .switchIfEmpty(repo.findById(id))
                    .defaultIfEmpty(default(id)!!)
                    .doOnSuccess { cache.put(id, it) }
        else
            Mono.justOrEmpty<T>(cache.getIfPresent(id))
                    .switchIfEmpty(repo.findById(id))
                    .doOnSuccess { if (it != null) cache.put(id, it) }
    }

    override fun update(mono: Mono<T>): Mono<T> {
        return mono.flatMap { cache.put(it.id, it); save(it) }
    }

    override fun update(target: T): Mono<T> {
        return repo.save(target).doOnSuccess { cache.put(target.id, target) }
    }

    override fun remove(id: ID): Mono<Void> {
        return repo.deleteById(id)
    }
}