package com.bruhascended.sms.ui.search

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.Room
import com.bruhascended.sms.db.Conversation
import com.bruhascended.sms.db.Message
import com.bruhascended.sms.db.MessageDatabase
import com.bruhascended.sms.mainViewModel
import com.bruhascended.sms.ui.search.ResultPagingSource.ResultKey
import com.bruhascended.sms.ui.search.SearchResultViewHolder.ResultItem


class ResultPagingSource(
    private val mContext: Context,
    private val key: String,
    private val categories: Array<Int>
) : PagingSource<ResultKey, ResultItem>() {

    data class ResultKey(
        val isConversation: Boolean,
        val categoryIndex: Int,
        val conversations: List<Conversation> = listOf(),
        val conversationIndex: Int = -1,
    )

    private val firstKey = ResultKey (
        true,
        categories.first()
    )

    private val isEmpty = BooleanArray(categories.size) {true}

    override suspend fun load(params: LoadParams<ResultKey>): LoadResult<ResultKey, ResultItem> {
        (params.key ?: firstKey).apply {
            val result = arrayListOf<ResultItem>()

            val prevKey: ResultKey?
            val nextKey: ResultKey?

            val category = categories[categoryIndex]

            when {
                isConversation -> {
                    val cons = mainViewModel.daos[category].search("$key%", "% $key%")
                    if (cons.isNotEmpty()) {
                        result.add(ResultItem(4, categoryHeader = category))
                        for (con in cons) {
                            result.add(ResultItem(0, conversation = con))
                        }
                    }

                    prevKey = if (this !== firstKey) {
                        ResultKey(
                            true,
                            categoryIndex - 1
                        )
                    } else null

                    nextKey = ResultKey(
                        categories.last() != category,
                        (categoryIndex + 1) % categories.size
                    )
                }
                conversations.isEmpty() -> {
                    prevKey = ResultKey(
                        categories.first() == category,
                        categories.lastIndex,
                        if (categories.first() == category) listOf()
                        else mainViewModel.daos[categories[categoryIndex - 1]].loadAllSync()
                    )

                    nextKey = ResultKey(
                        false,
                        (categoryIndex) % categories.size,
                        mainViewModel.daos[category].loadAllSync(),
                        0
                    )
                }
                else -> {
                    val conversation = conversations[conversationIndex]

                    var msgs: List<Message>
                    Room.databaseBuilder(
                        mContext, MessageDatabase::class.java, conversation.sender
                    ).allowMainThreadQueries().build().apply {
                        msgs = manager().search("%$key%", "% $key%")
                        close()
                    }
                    if (!msgs.isNullOrEmpty()) {
                        if (isEmpty[categoryIndex]) {
                            isEmpty[categoryIndex] = false
                            result.add(ResultItem(4, categoryHeader = 10+category))
                        }
                        result.add(ResultItem(1, conversation = conversation))
                        for (msg in msgs) {
                            result.add(ResultItem(2, conversation = conversation, message = msg))
                        }
                    }


                    prevKey = ResultKey(
                        false,
                        category,
                        if (conversationIndex == 0) listOf() else conversations,
                        conversationIndex - 1
                    )

                    nextKey = when {
                        conversationIndex == conversations.lastIndex &&
                                categoryIndex == categories.lastIndex -> null
                        conversationIndex == conversations.lastIndex -> ResultKey(
                            false,
                            categoryIndex + 1,
                            listOf(),
                            -1
                        )
                        else -> ResultKey(
                            false,
                            categoryIndex,
                            conversations,
                            conversationIndex + 1
                        )
                    }
                }
            }

            LoadResult.Page
            return LoadResult.Page(
                data = result.toList(),
                prevKey = prevKey,
                nextKey = nextKey
            )
        }
    }
}