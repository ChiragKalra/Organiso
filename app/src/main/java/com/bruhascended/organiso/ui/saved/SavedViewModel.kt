package com.bruhascended.organiso.ui.saved

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.bruhascended.core.db.*
import kotlinx.coroutines.flow.Flow

/*
                    Copyright 2020 Chirag Kalra

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

*/

class SavedViewModel(mApp: Application) : AndroidViewModel(mApp) {
    private val mDao: SavedDao = SavedDbFactory(mApp).get().manager()

    val flow: Flow<PagingData<Saved>> = Pager(
        PagingConfig(
            pageSize = 15,
            initialLoadSize = 15,
            prefetchDistance = 60,
            maxSize = 180,
        )
    ) {
        mDao.loadPaged()
    }.flow

    fun dbIsEmpty() = mDao.loadSingleSync() == null

    fun delete(saved: Saved) = mDao.delete(saved)
}