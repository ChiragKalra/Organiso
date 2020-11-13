package com.bruhascended.organiso.common

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.view.View
import android.widget.*
import androidx.core.content.FileProvider
import androidx.core.view.doOnDetach
import com.bruhascended.core.constants.getMimeType
import com.bruhascended.organiso.BuildConfig
import com.bruhascended.organiso.R
import com.squareup.picasso.Picasso
import java.io.File
import java.io.FileOutputStream

abstract class MediaViewHolder(
    private val mContext: Context,
    val root: View,
) : ScrollEffectFactory.ScrollEffectViewHolder(root) {

    private val picasso: Picasso = Picasso.get()
    private val contentIntent = Intent(Intent.ACTION_QUICK_VIEW)
    private val playPause: ImageButton = root.findViewById(R.id.playPause)
    private val videoPlayPause: ImageButton = root.findViewById(R.id.videoPlayPause)
    private val mediaLayout: LinearLayout = root.findViewById(R.id.mediaLayout)
    private val imageView: ImageView = root.findViewById(R.id.image)

    protected val dtp = DateTimeProvider(mContext)

    val slider: SeekBar = root.findViewById(R.id.slider)
    val content: LinearLayout = root.findViewById(R.id.content)
    val messageTextView: TextView = root.findViewById(R.id.message)


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
        videoPlayPause.visibility = View.VISIBLE
        imageView.visibility = View.VISIBLE
        var cache = getCache(getUid())
        if (cache != null)
            picasso.load(cache).into(imageView)
        else Thread {
            val retriever = MediaMetadataRetriever().apply {
                setDataSource(getDataPath())
            }
            val length = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!
                .toLong()
            val bm = retriever.getFrameAtTime(
                1000000 % length,
                MediaMetadataRetriever.OPTION_CLOSEST
            )!!
            cache = saveCache(bm, getUid())
            (mContext as Activity).runOnUiThread {
                picasso.load(cache!!).into(imageView)
            }
        }.start()

        videoPlayPause.setOnClickListener {
            mContext.startActivity(contentIntent)
        }
    }

    private fun showImage() {
        imageView.visibility = View.VISIBLE
        picasso.load(File(getDataPath())).into(imageView)
        imageView.setOnClickListener{
            mContext.startActivity(contentIntent)
        }
    }

    private fun showAudio() {
        MediaPlayer().apply {
            slider.visibility = View.VISIBLE
            playPause.visibility = View.VISIBLE
            setDataSource(mContext, Uri.fromFile(File(getDataPath())))
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

    protected fun showMedia() {
        if (messageTextView.text.isBlank()) {
            messageTextView.visibility = View.GONE
        }
        mediaLayout.visibility = View.VISIBLE
        val mmsTypeString = getMimeType(getDataPath())
        val contentUri = FileProvider.getUriForFile(
            mContext,
            "${BuildConfig.APPLICATION_ID}.fileProvider", File(getDataPath())
        )
        mContext.grantUriPermission(
            BuildConfig.APPLICATION_ID, contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION
        )

        contentIntent.setDataAndType(contentUri, mmsTypeString)
        contentIntent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION

        when {
            mmsTypeString.startsWith("image") -> showImage()
            mmsTypeString.startsWith("audio") -> showAudio()
            mmsTypeString.startsWith("video") -> showVideo()
        }
    }

    protected open fun hideMedia() {
        playPause.visibility = View.GONE
        videoPlayPause.visibility = View.GONE
        mediaLayout.visibility = View.GONE
        imageView.visibility = View.GONE
        slider.visibility = View.GONE
        messageTextView.visibility = View.VISIBLE
    }

    protected abstract fun getUid(): Long
    protected abstract fun getDataPath(): String
}