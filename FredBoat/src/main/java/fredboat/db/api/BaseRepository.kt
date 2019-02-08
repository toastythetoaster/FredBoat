package fredboat.db.api

import reactor.core.publisher.Mono

interface BaseRepository<T> {

    fun fetch(id: Long): Mono<T>

    fun update(mono: Mono<T>): Mono<T>

    fun update(target: T): Mono<T>

    fun delete(id: Long): Mono<Void>

    fun default(id: Long): T
}