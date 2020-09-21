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

package com.bruhascended.sms.ui.search

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.provider.ContactsContract
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.doOnDetach
import androidx.lifecycle.Observer
import com.bruhascended.sms.R
import com.bruhascended.sms.data.Contact
import com.bruhascended.sms.data.ContactsManager
import com.bruhascended.sms.db.Conversation
import com.bruhascended.sms.db.Message
import com.bruhascended.sms.ui.dpMemoryCache
import com.bruhascended.sms.ui.mainViewModel
import com.squareup.picasso.Picasso
import java.io.File
import java.io.FileOutputStream
import java.util.*


class SearchListViewAdaptor(
    private val mContext: Context,
    private val items: List<Pair<Conversation, Message?>>
) : BaseAdapter() {

    private var colors: Array<Int> = arrayOf(
        ContextCompat.getColor(mContext, R.color.red),
        ContextCompat.getColor(mContext, R.color.blue),
        ContextCompat.getColor(mContext, R.color.purple),
        ContextCompat.getColor(mContext, R.color.green),
        ContextCompat.getColor(mContext, R.color.teal),
        ContextCompat.getColor(mContext, R.color.orange)
    )

    private var cm: ContactsManager = ContactsManager(mContext)
    private val previewCache: MutableMap<Long, String> = mutableMapOf()
    private val picasso = Picasso.get()

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
        contentLayout: LinearLayout, message: Message
    ) {
        mediaLayout.visibility = View.VISIBLE
        if (message.type == 1) contentLayout.setBackgroundResource(R.drawable.bg_mms)
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
                if (previewCache.containsKey(message.id))
                    picasso.load(File(previewCache[message.id]!!)).into(image)
                else Thread {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(path)
                    val length = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
                    val bm = retriever.getFrameAtTime(
                        length / 2,
                        MediaMetadataRetriever.OPTION_CLOSEST
                    )!!
                    previewCache[message.id!!] = saveCache(bm, message.time)
                    (mContext as Activity).runOnUiThread {
                        picasso.load(File(previewCache[message.id]!!)).into(image)
                    }
                }.start()

                videoPlayPause.setOnClickListener{
                    mContext.startActivity(contentIntent)
                }
            }
        }
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val layoutInflater = LayoutInflater.from(mContext)
        if (items[position].second != null) {
            val message = items[position].second!!

            val root = if (message.type == 1) layoutInflater.inflate(
                R.layout.item_message,
                parent,
                false
            )
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
                        findViewById(R.id.content), message
                    )
                    if (message.text == "") messageTextView.visibility = View.GONE
                }
            }
            return root
        } else {
            val cur = items[position].first

            val root = layoutInflater.inflate(R.layout.item_conversation, parent, false)

            val imageView: QuickContactBadge = root.findViewById(R.id.dp)
            val muteImage: ImageView = root.findViewById(R.id.mutedImage)
            val senderTextView: TextView = root.findViewById(R.id.sender)
            val messageTextView: TextView = root.findViewById(R.id.lastMessage)
            val timeTextView: TextView = root.findViewById(R.id.time)

            if (cur.name == null) {
                mainViewModel.contacts.observe(
                    mContext as AppCompatActivity,
                    object : Observer<Array<Contact>?> {
                        override fun onChanged(contacts: Array<Contact>?) {
                            mainViewModel.contacts.removeObserver(this)
                            if (contacts == null) return
                            for (contact in contacts) {
                                if (contact.number == cur.sender) {
                                    cur.name = contact.name
                                    mainViewModel.daos[cur.label].update(cur)
                                    break
                                }
                            }

                        }
                    }
                )
            }

            senderTextView.apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                text = cur.name ?: cur.sender
                val lp = layoutParams as ConstraintLayout.LayoutParams
                lp.bottomToBottom = R.id.root
                layoutParams = lp
            }

            timeTextView.visibility = View.GONE
            messageTextView.visibility = View.GONE
            if (cur.isMuted) muteImage.visibility = View.VISIBLE

            imageView.assignContactFromPhone(cur.sender, true)
            imageView.setMode(ContactsContract.QuickContact.MODE_LARGE)

            imageView.setBackgroundColor(colors[position % colors.size])
            imageView.performClick()

            if (cur.sender.first().isLetter()) {
                imageView.setImageResource(R.drawable.ic_bot)
                imageView.isEnabled = false
                val density = mContext.resources.displayMetrics.density
                val dps = 12 * density.toInt()
                imageView.setPadding(dps, dps, dps, dps)
            } else if (cur.name != null) {
                if (dpMemoryCache.containsKey(cur.sender)) {
                    val dp = dpMemoryCache[cur.sender]
                    if (dp != null) imageView.setImageBitmap(dp)
                } else Thread {
                    dpMemoryCache[cur.sender] = cm.retrieveContactPhoto(cur.sender)
                    val dp = dpMemoryCache[cur.sender]
                    (mContext as Activity).runOnUiThread {
                        if (dp != null) imageView.setImageBitmap(dp)
                    }
                }.start()
            }
            return root
        }
    }

    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]
    override fun getItemId(position: Int) = position.toLong()
}
