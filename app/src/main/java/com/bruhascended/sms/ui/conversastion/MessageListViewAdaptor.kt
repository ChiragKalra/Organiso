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
import java.io.File
import java.io.FileOutputStream
import java.util.*


class MessageListViewAdaptor(context: Context, data: List<Message>) : BaseAdapter() {

    private val mContext: Context = context
    private var messages: List<Message> = data
    private var mSelectedItemsIds = SparseBooleanArray()
    private val previewCache: MutableMap<Int, String> = mutableMapOf()

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
        contentLayout.setBackgroundResource(R.drawable.bg_mms_out)
        val data = Uri.parse(path)
        val mmsTypeString = getMimeType(path)
        val contentUri = FileProvider.getUriForFile(
            mContext,
            "com.bruhascended.sms.fileProvider", File(path)
        )
        mContext.grantUriPermission(
            "com.bruhascended.sms", contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        val contentIntent = Intent(Intent.ACTION_VIEW)
        contentIntent.setDataAndType(contentUri, mmsTypeString)
        contentIntent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION

        when {
            mmsTypeString.startsWith("image") -> {
                image.visibility = View.VISIBLE
                image.setImageURI(data)
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
                    slider.max = duration / 500

                    val mHandler = Handler(mContext.mainLooper)
                    (mContext as Activity).runOnUiThread(object : Runnable {
                        override fun run() {
                            slider.progress = currentPosition / 500
                            mHandler.postDelayed(this, 500)
                        }
                    })

                    slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onStopTrackingTouch(seekBar: SeekBar) {}
                        override fun onStartTrackingTouch(seekBar: SeekBar) {}
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
                            slider.doOnDetach {
                                reset()
                            }
                            playPause.setImageResource(R.drawable.ic_pause)
                        }
                    }
                }
            }
            mmsTypeString.startsWith("video") -> {
                videoPlayPause.visibility = View.VISIBLE
                image.visibility = View.VISIBLE
                if (previewCache.containsKey(position))
                    image.setImageURI(Uri.parse(previewCache[position]))
                else Thread {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(path)
                    val length = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
                    previewCache[position] = saveCache(retriever.getFrameAtTime(
                        length / 2,
                        MediaMetadataRetriever.OPTION_CLOSEST
                    )!!, getItem(position).time)
                    previewCache[position]!!

                    (mContext as Activity).runOnUiThread {
                        image.setImageURI(Uri.parse(previewCache[position]))
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
        val root =  if (messages[position].type == 1)
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
                    5 -> "failed"
                    6 -> "sending"
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
}
