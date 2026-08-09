package com.nexaflow.data.paging

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MappedPagingSourceTest {

    private class FakeSource(
        private val data: List<Int>,
        private val fail: Boolean = false
    ) : PagingSource<Int, Int>() {

        override fun getRefreshKey(state: PagingState<Int, Int>): Int? =
            state.anchorPosition?.let { data.getOrNull(it) }

        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Int> {
            if (fail) return LoadResult.Error(IllegalStateException("boom"))
            return LoadResult.Page(
                data = data,
                prevKey = params.key?.minus(1),
                nextKey = params.key?.plus(1)
            )
        }
    }

    private fun refreshParams(): PagingSource.LoadParams.Refresh<Int> =
        PagingSource.LoadParams.Refresh(
            key = 0,
            loadSize = 3,
            placeholdersEnabled = false
        )

    @Test
    fun mapsValuesPageByPage() = runBlocking {
        val mapped = MappedPagingSource(FakeSource(listOf(1, 2, 3))) { it * 10 }
        val result = mapped.load(refreshParams())
        assertTrue(result is PagingSource.LoadResult.Page)
        val page = result as PagingSource.LoadResult.Page<Int, Int>
        assertEquals(listOf(10, 20, 30), page.data)
        assertEquals(1, page.nextKey)
        assertEquals(-1, page.prevKey)
    }

    @Test
    fun propagatesError() = runBlocking {
        val mapped = MappedPagingSource(FakeSource(emptyList(), fail = true)) { it * 10 }
        val result = mapped.load(refreshParams())
        assertTrue(result is PagingSource.LoadResult.Error)
        assertEquals("boom", (result as PagingSource.LoadResult.Error).throwable.message)
    }

    @Test
    fun forwardsRefreshKey() {
        val mapped = MappedPagingSource(FakeSource(listOf(1, 2, 3))) { it * 10 }
        // No anchor position -> null refresh key (safe default).
        val state = PagingState<Int, Int>(
            pages = emptyList(),
            anchorPosition = null,
            config = PagingConfig(pageSize = 3),
            leadingPlaceholderCount = 0
        )
        assertNull(mapped.getRefreshKey(state))
    }
}
