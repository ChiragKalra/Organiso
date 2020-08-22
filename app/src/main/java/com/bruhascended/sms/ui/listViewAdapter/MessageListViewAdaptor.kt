package com.bruhascended.sms.ui.listViewAdapter

import android.app.Activity
import android.content.Context
import android.content.Intent
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
import java.util.*


class MessageListViewAdaptor(context: Context, data: List<Message>) : BaseAdapter() {

    private val mContext: Context = context
    private var messages: List<Message> = data
    private var mSelectedItemsIds = SparseBooleanArray()

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
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)?:""
    }

    private fun displayMedia(
        path: String, video: VideoView, image: ImageView, slider: SeekBar,
        playPause: ImageButton, videoPlayPause: ImageButton, mediaLayout: LinearLayout,
        contentLayout: LinearLayout
    ) {
        mediaLayout.visibility = View.VISIBLE
        contentLayout.setBackgroundResource(R.drawable.bg_mms_out)
        val data = Uri.fromFile(File(path))
        val mmsTypeString = getMimeType(path)

        when {
            mmsTypeString.startsWith("image") -> {
                image.visibility = View.VISIBLE
                image.setImageURI(data)
            }
            mmsTypeString.startsWith("audio") -> {
                val mp = MediaPlayer()
                slider.visibility = View.VISIBLE
                playPause.visibility = View.VISIBLE
                mp.setDataSource(mContext, data)
                mp.prepareAsync()
                slider.max = mp.duration/500

                val mHandler = Handler(mContext.mainLooper)
                (mContext as Activity).runOnUiThread(object : Runnable {
                    override fun run() {
                        slider.progress = mp.currentPosition / 500
                        mHandler.postDelayed(this, 500)
                    }
                })

                slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onStopTrackingTouch(seekBar: SeekBar) {}
                    override fun onStartTrackingTouch(seekBar: SeekBar) {}
                    override fun onProgressChanged(
                        seekBar: SeekBar, progress: Int, fromUser: Boolean
                    ) {
                        if (fromUser) mp.seekTo(progress * 500)
                    }
                })

                playPause.setOnClickListener{
                    if (mp.isPlaying) {
                        mp.pause()
                        playPause.setImageResource(R.drawable.ic_play)
                    } else {
                        mp.start()
                        slider.doOnDetach {
                            Thread{mp.reset()}.start()
                        }
                        playPause.setImageResource(R.drawable.ic_pause)
                    }
                }
            }
            mmsTypeString.startsWith("video") -> {
                video.visibility = View.VISIBLE
                videoPlayPause.visibility = View.VISIBLE
                val videoUri = FileProvider.getUriForFile(mContext,
                    "com.bruhascended.sms.fileProvider", File(path))
                mContext.grantUriPermission(
                    "com.bruhascended.sms", videoUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                video.setVideoURI(videoUri)
                video.setOnPreparedListener {
                    mp -> mp.isLooping = true
                }
                videoPlayPause.setOnClickListener{
                    if (video.isPlaying) {
                        video.pause()
                        videoPlayPause.setImageResource(R.drawable.ic_play)
                    } else {
                        video.start()
                        videoPlayPause.setImageResource(R.drawable.ic_pause)
                    }
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
                    message.path!!, findViewById(R.id.video), findViewById(R.id.image),
                    findViewById(R.id.slider), findViewById(R.id.playPause),
                    findViewById(R.id.videoPlayPause), findViewById(R.id.mediaLayout),
                    findViewById(R.id.content)
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
