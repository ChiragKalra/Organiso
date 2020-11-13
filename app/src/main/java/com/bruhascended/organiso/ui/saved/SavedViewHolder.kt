package com.bruhascended.organiso.ui.saved

import android.content.Context
import android.view.View
import com.bruhascended.core.constants.SAVED_TYPE_RECEIVED
import com.bruhascended.core.db.Saved
import com.bruhascended.organiso.R
import com.bruhascended.organiso.common.MediaViewHolder

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

class SavedViewHolder(mContext: Context, root: View) : MediaViewHolder(mContext, root) {

    lateinit var message: Saved

    override fun getUid(): Long = message.time
    override fun getDataPath(): String = message.path!!

    override fun hideMedia() {
        super.hideMedia()
        if (message.type == 1) content.setBackgroundResource(R.drawable.bg_message)
        else content.setBackgroundResource(R.drawable.bg_message_out)
    }

    fun onBind() {
        hideMedia()
        content.setBackgroundResource(
            if (message.type == SAVED_TYPE_RECEIVED)
                R.drawable.bg_message else R.drawable.bg_message_out
        )

        messageTextView.text = message.text
        if (message.path != null) {
            showMedia()
        }
    }
}