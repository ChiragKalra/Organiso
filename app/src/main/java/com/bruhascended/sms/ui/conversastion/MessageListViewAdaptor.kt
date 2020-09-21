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

package com.bruhascended.sms.ui.conversastion

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.util.SparseBooleanArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.*
import androidx.core.content.FileProvider
import androidx.core.view.doOnDetach
import com.bruhascended.sms.R
import com.bruhascended.sms.db.Message
import com.squareup.picasso.Picasso
import java.io.File
import java.io.FileOutputStream
import java.util.*


class MessageListViewAdaptor(
    context: Context,
    val messages: List<Message>
) : BaseAdapter() {

    private val mContext: Context = context
    private var mSelectedItemsIds = SparseBooleanArray()
    private val previewCache: MutableMap<Int, String> = mutableMapOf()
    private val picasso = Picasso.get()

    init {
        Thread {
            for (position in messages.indices) {
                val sms = messages[position]
                val p = sms.path
                if (p != null && getMimeType(p).startsWith("video")) {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(p)
                    val length = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
                    val bm = retriever.getFrameAtTime(
                        length / 2,
                        MediaMetadataRetriever.OPTION_CLOSEST
                    )!!
                    previewCache[position] = saveCache(bm, getItem(position).time)
                }
            }
        }.start()
    }

    private fun displayTime(time: Long): String {
        val smsTime = Calendar.getInstance()
        smsTime.timeInMillis = time

        val now = Calendar.getInstance()

        val timeFormatString = if (DateFormat.is24HourFormat(mContext)) "H:mm" else "h:mm aa"
        val timeString = DateFormat.format(timeFormatString, smsTime).toString()
        val dateFormatString = "d MMMM"
        val dateYearFormatString = "dd/MM/yyyy"
        return when {
            DateUtils.isToday(time) -> timeString
            DateUtils.isToday(time + DateUtils.DAY_IN_MILLIS) -> "$timeString,\nYesterday"
            now[Calendar.YEAR] == smsTime[Calendar.YEAR] ->
                timeString + ",\n" + DateFormat.format(dateFormatString, smsTime).toString()
            else ->
                timeString + ",\n" + DateFormat.format(dateYearFormatString, smsTime).toString()
        }
    }

    private fun selectView(position: Int, value: Boolean) {
        if (value) mSelectedItemsIds.put(position, value)
        else mSelectedItemsIds.delete(position)
    }

    private fun getMimeType(url: String): String {
        val extension = MimeTypeMap.getFileExtensionFromUrl(url)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: ""
    }

    private fun saveCache(bitmap: Bitmap, date: Long): String {
        val destination = File(mContext.cacheDir, date.toString())
        FileOutputStream(destination).apply {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, this)
            close()
        }
        return destination.absolutePath
    }

    private fun displayMedia(
        path: String, image: ImageView, slider: SeekBar,
        playPause: ImageButton, videoPlayPause: ImageButton, mediaLayout: LinearLayout,
        contentLayout: LinearLayout, position: Int
    ) {
        mediaLayout.visibility = View.VISIBLE
        if (getItem(position).type == 1) contentLayout.setBackgroundResource(R.drawable.bg_mms)
        else contentLayout.setBackgroundResource(R.drawable.bg_mms_out)
        val data = Uri.parse(path)
        val mmsTypeString = getMimeType(path)
        val contentUri = FileProvider.getUriForFile(
            mContext,
            "com.bruhascended.sms.fileProvider", File(path)
        )
        mContext.grantUriPermission(
            "com.bruhascended.sms", contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        val contentIntent = Intent(Intent.ACTION_QUICK_VIEW)
        contentIntent.setDataAndType(contentUri, mmsTypeString)
        contentIntent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION

        when {
            mmsTypeString.startsWith("image") -> {
                image.visibility = View.VISIBLE
                picasso.load(File(data.toString())).into(image)
                image.setOnClickListener{
                    mContext.startActivity(contentIntent)
                }
            }
            mmsTypeString.startsWith("audio") -> {
                MediaPlayer().apply {
                    slider.visibility = View.VISIBLE
                    playPause.visibility = View.VISIBLE
                    setDataSource(mContext, data)
                    prepareAsync()
                    setOnPreparedListener {
                        slider.max = duration / 500

                        val mHandler = Handler(mContext.mainLooper)
                        (mContext as Activity).runOnUiThread(object : Runnable {
                            override fun run() {
                                slider.progress = currentPosition / 500
                                mHandler.postDelayed(this, 500)
                            }
                        })

                        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                            override fun onStopTrackingTouch(s: SeekBar) {}
                            override fun onStartTrackingTouch(s: SeekBar) {}
                            override fun onProgressChanged(
                                seekBar: SeekBar, progress: Int, fromUser: Boolean
                            ) {
                                if (fromUser) seekTo(progress * 500)
                            }
                        })

                        playPause.setOnClickListener {
                            if (isPlaying) {
                                pause()
                                playPause.setImageResource(R.drawable.ic_play)
                            } else {
                                start()
                                slider.doOnDetach { reset() }
                                playPause.setImageResource(R.drawable.ic_pause)
                            }
                        }
                    }
                }
            }
            mmsTypeString.startsWith("video") -> {
                videoPlayPause.visibility = View.VISIBLE
                image.visibility = View.VISIBLE
                if (previewCache.containsKey(position))
                    picasso.load(File(previewCache[position]!!)).into(image)
                else Thread {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(path)
                    val length = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
                    val bm = retriever.getFrameAtTime(
                        length / 2,
                        MediaMetadataRetriever.OPTION_CLOSEST
                    )!!
                    previewCache[position] = saveCache(bm, getItem(position).time)
                    (mContext as Activity).runOnUiThread {
                        picasso.load(File(previewCache[position]!!)).into(image)
                    }
                }.start()

                videoPlayPause.setOnClickListener{
                    mContext.startActivity(contentIntent)
                }
            }
        }
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val message = messages[position]
        val layoutInflater = LayoutInflater.from(mContext)

        val root = if (messages[position].type == 1)
            layoutInflater.inflate(R.layout.item_message, parent, false)
        else layoutInflater.inflate(R.layout.item_message_out, parent, false)

        root.apply {
            val messageTextView: TextView = findViewById(R.id.message)
            val timeTextView: TextView = findViewById(R.id.time)

            messageTextView.text = message.text
            timeTextView.text = displayTime(message.time)

            if (message.type != 1) {
                val statusTextView: TextView = findViewById(R.id.status)
                statusTextView.text = if (message.delivered)
                    "delivered"
                else when (message.type) {
                    2 -> "sent"
                    5 -> {
                        statusTextView.setTextColor(mContext.getColor(R.color.red))
                        "failed"
                    }
                    6 -> "queued"
                    else -> "unknown"
                }
            }

            if (message.path != null) {
                displayMedia(
                    message.path!!, findViewById(R.id.image),
                    findViewById(R.id.slider), findViewById(R.id.playPause),
                    findViewById(R.id.videoPlayPause), findViewById(R.id.mediaLayout),
                    findViewById(R.id.content), position
                )
                if (message.text == "") messageTextView.visibility = View.GONE
            }
        }

        return root
    }

    override fun getCount() = messages.size
    override fun getItem(position: Int) = messages[position]
    override fun getItemId(position: Int) = messages[position].id!!

    fun getSelectedIds() = mSelectedItemsIds
    fun toggleSelection(position: Int) = selectView(position, !mSelectedItemsIds.get(position))

    fun removeSelection() {
        mSelectedItemsIds = SparseBooleanArray()
    }

    fun isMedia(position: Int) = getItem(position).path != null

    fun getSharable(position: Int) : Intent {
        val path = getItem(position).path!!
        val mmsTypeString = getMimeType(path)
        val contentUri = FileProvider.getUriForFile(
            mContext,
            "com.bruhascended.sms.fileProvider", File(path)
        )
        mContext.grantUriPermission(
            "com.bruhascended.sms", contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        return Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, contentUri)
            type = mmsTypeString
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
    }
}
