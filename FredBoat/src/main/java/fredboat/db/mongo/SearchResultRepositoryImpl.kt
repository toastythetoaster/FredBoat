package fredboat.db.mongo

import fredboat.db.api.SearchResultRepository
import fredboat.db.transfer.SearchResult
import fredboat.db.transfer.SearchResultId

class SearchResultRepositoryImpl(repo: InternalSearchResultRepository) : BaseRepositoryImpl<SearchResultId, SearchResult>(repo), SearchResultRepository {

    override fun default(id: SearchResultId): SearchResult? {
        return null
    }

}