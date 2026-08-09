package com.nexaflow.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState

/**
 * Maps a [PagingSource]'s values page-by-page without pulling whole pages into
 * memory. Paging 3.4 removed the `PagingSource.map` convenience extension, so
 * this thin adapter keeps entity→domain mapping inside the data layer.
 */
class MappedPagingSource<Key : Any, Value : Any, R : Any>(
    private val upstream: PagingSource<Key, Value>,
    private val transform: (Value) -> R
) : PagingSource<Key, R>() {

    override fun getRefreshKey(state: PagingState<Key, R>): Key? = null

    override suspend fun load(params: LoadParams<Key>): LoadResult<Key, R> {
        return when (val result = upstream.load(params)) {
            is LoadResult.Page -> LoadResult.Page(
                data = result.data.map(transform),
                prevKey = result.prevKey,
                nextKey = result.nextKey,
                itemsBefore = result.itemsBefore,
                itemsAfter = result.itemsAfter
            )
            is LoadResult.Error -> LoadResult.Error(result.throwable)
            is LoadResult.Invalid -> LoadResult.Invalid()
        }
    }
}
