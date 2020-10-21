package com.bruhascended.organiso.ui.saved

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.text.Spannable
import android.view.View
import android.view.View.*
import android.webkit.MimeTypeMap
import android.widget.*
import android.widget.TextView.TEXT_ALIGNMENT_VIEW_START
import androidx.core.content.FileProvider
import androidx.core.view.doOnDetach
import com.bruhascended.core.constants.SAVED_TYPE_RECEIVED
import com.bruhascended.organiso.R
import com.bruhascended.organiso.BuildConfig.APPLICATION_ID
import com.bruhascended.core.db.Saved
import com.bruhascended.organiso.common.DateTimeProvider
import com.bruhascended.organiso.common.ScrollEffectFactory
import com.squareup.picasso.Picasso
import java.io.File
import java.io.FileOutputStream

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

class SavedViewHolder(
    private val mContext: Context,
    val root: View,
) : ScrollEffectFactory.ScrollEffectViewHolder(root) {
    private val picasso = Picasso.get()
    private val dtp = DateTimeProvider(mContext)
    private val contentIntent = Intent(Intent.ACTION_QUICK_VIEW)

    private val playPause: ImageButton = root.findViewById(R.id.playPause)
    private val videoPlayPause: ImageButton = root.findViewById(R.id.videoPlayPause)
    private val mediaLayout: LinearLayout = root.findViewById(R.id.mediaLayout)
    private val imageView: ImageView = root.findViewById(R.id.image)
    private var backgroundAnimator: ValueAnimator? = null

    private val flag = Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
    private val highlightColor = mContext.getColor(R.color.textHighLight)

    lateinit var message: Saved

    private val messageTextView: TextView = root.findViewById(R.id.message)
    val slider: SeekBar = root.findViewById(R.id.slider)
    val content: LinearLayout = root.findViewById(R.id.content)

    private fun getMimeType(url: String): String {
        val extension = MimeTypeMap.getFileExtensionFromUrl(url)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: ""
    }

    private fun saveCache(bitmap: Bitmap, date: Long): File {
        val destination = File(mContext.cacheDir, date.toString())
        FileOutputStream(destination).apply {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, this)
            close()
        }
        return destination
    }

    private fun getCache(date: Long): File? {
        val source = File(mContext.cacheDir, date.toString())
        return if (source.exists()) source else null
    }

    private fun showVideo() {
        videoPlayPause.visibility = VISIBLE
        imageView.visibility = VISIBLE
        var cache = getCache(message.time)
        if (cache != null)
            picasso.load(cache).into(imageView)
        else Thread {
            val retriever = MediaMetadataRetriever().apply {
                setDataSource(message.path)
            }
            val length = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!
                    .toLong()
            val bm = retriever.getFrameAtTime(
                1000000 % length,
                MediaMetadataRetriever.OPTION_CLOSEST
            )!!
            cache = saveCache(bm, message.time)
            (mContext as Activity).runOnUiThread {
                picasso.load(cache!!).into(imageView)
            }
        }.start()

        videoPlayPause.setOnClickListener {
            mContext.startActivity(contentIntent)
        }
    }

    private fun showImage() {
        imageView.visibility = VISIBLE
        picasso.load(File(message.path!!)).into(imageView)
        imageView.setOnClickListener{
            mContext.startActivity(contentIntent)
        }
    }

    private fun showAudio() {
        MediaPlayer().apply {
            slider.visibility = VISIBLE
            playPause.visibility = VISIBLE
            setDataSource(mContext,  Uri.parse(message.path))
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

    private fun hideMedia() {
        playPause.visibility = GONE
        videoPlayPause.visibility = GONE
        mediaLayout.visibility = GONE
        imageView.visibility = GONE
        slider.visibility = GONE
        messageTextView.visibility = VISIBLE
        if (message.type == 1) content.setBackgroundResource(R.drawable.bg_message)
        else content.setBackgroundResource(R.drawable.bg_message_out)
    }

    private fun showMedia() {
        mediaLayout.visibility = VISIBLE
        if (message.type == 1) content.setBackgroundResource(R.drawable.bg_mms)
        else content.setBackgroundResource(R.drawable.bg_mms_out)
        val mmsTypeString = getMimeType(message.path!!)
        val contentUri = FileProvider.getUriForFile(
            mContext,
            "$APPLICATION_ID.fileProvider", File(message.path!!)
        )
        mContext.grantUriPermission(
            APPLICATION_ID, contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION
        )

        contentIntent.setDataAndType(contentUri, mmsTypeString)
        contentIntent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION

        when {
            mmsTypeString.startsWith("image") -> showImage()
            mmsTypeString.startsWith("audio") -> showAudio()
            mmsTypeString.startsWith("video") -> showVideo()
        }
    }

    fun onBind() {
        hideMedia()

        content.setBackgroundResource(
            if (message.type == SAVED_TYPE_RECEIVED)
                R.drawable.bg_message else R.drawable.bg_message_out
        )

        messageTextView.text = message.text
        messageTextView.textAlignment = if (message.type == SAVED_TYPE_RECEIVED)
            TEXT_ALIGNMENT_VIEW_START else TEXT_ALIGNMENT_VIEW_END

        if (message.path != null) {
            showMedia()
            if (message.text.isBlank()) messageTextView.visibility = GONE
        }

    }
}