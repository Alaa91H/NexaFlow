package com.nexaflow.core.execution

import androidx.paging.PagingSource
import androidx.paging.PagingState

/**
 * Shared test helper for an always-empty [PagingSource]. Paging 3.4 removed the
 * `PagingSource.from(...)` convenience factory, so fakes that implement
 * [com.nexaflow.domain.repositories.HistoryRepository] build their stub via
 * this instead.
 */
fun <Key : Any, Value : Any> emptyPagingSource(): PagingSource<Key, Value> =
    object : PagingSource<Key, Value>() {
        override fun getRefreshKey(state: PagingState<Key, Value>): Key? = null

        override suspend fun load(params: LoadParams<Key>): LoadResult<Key, Value> =
            LoadResult.Page(data = emptyList(), prevKey = null, nextKey = null)
    }
