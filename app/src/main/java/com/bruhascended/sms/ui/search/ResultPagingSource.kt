package com.bruhascended.sms.ui.search

import android.content.Context
import androidx.paging.PagingSource
import com.bruhascended.sms.mainViewModel
import com.bruhascended.sms.ui.search.ResultPagingSource.ResultKey
import com.bruhascended.sms.ui.search.SearchResultViewHolder.ResultItem

class ResultPagingSource(
    private val mContext: Context,
    private val key: String,
    private val visibleCategories: Array<Int>,
    private val hiddenCategories: Array<Int>
) : PagingSource<ResultKey, ResultItem>() {

    data class ResultKey(
        val isConversation: Boolean,
        val category: Int,
        val senders: List<String> = listOf(),
        val sender: String = "",
    )

    private val firstKey = ResultKey (
        true,
        visibleCategories.first()
    )

    val prevKey = null
    val nextKey = null


    override suspend fun load(params: LoadParams<ResultKey>): LoadResult<ResultKey, ResultItem> {
        val pageKey = params.key ?: firstKey
        val result = arrayListOf<ResultItem>()

        if (pageKey.isConversation) {
            val cons = mainViewModel.daos[pageKey.category].search("$key%", "% $key%")
            if (cons.isNotEmpty()) {
                result.add(ResultItem(4, categoryHeader = pageKey.category))
                for (con in cons) {
                    result.add(ResultItem(0, conversation = con))
                }
            }
        } else {

        }


        return LoadResult.Page(
            data = result.toList(),
            prevKey = prevKey,
            nextKey = nextKey
        )
    }
}