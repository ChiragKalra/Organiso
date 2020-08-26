package com.bruhascended.sms.ui

import android.content.Intent
import android.graphics.drawable.AnimatedVectorDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Parcelable
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import com.bruhascended.sms.R
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MediaPreviewManager(
    private val mActivity: AppCompatActivity,
    private val videoView: VideoView,
    private val imagePreview: ImageView,
    private val seekBar: SeekBar,
    private val playPauseButton: ImageButton,
    private val videoPlayPauseButton: ImageButton,
    private val addMedia: ImageButton,
) {
    var mmsType = 0

    lateinit var mmsTypeString: String
    lateinit var mmsURI: Uri

    private val mp = MediaPlayer()

    private fun fadeAway(vararg views: View) {
        for (view in views) view.apply {
            if (visibility == View.VISIBLE) {
                alpha = 1f
                animate()
                    .alpha(0f)
                    .setDuration(300)
                    .start()
                GlobalScope.launch {
                    delay(400)
                    mActivity.runOnUiThread {
                        alpha = 1f
                        visibility = View.GONE
                    }
                }
            }
        }
    }

    fun loadMedia() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "*/*"
        intent.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "audio/*", "video/*"))
        mActivity.startActivityForResult(intent, 0)
    }

    fun hideMediaPreview() {
        videoView.stopPlayback()
        mp.reset()
        fadeAway(videoView, imagePreview, seekBar, playPauseButton, videoPlayPauseButton)
        mmsType = 0
        addMedia.setImageResource(R.drawable.close_to_add)
        (addMedia.drawable as AnimatedVectorDrawable).start()
        addMedia.setOnClickListener {
            loadMedia()
        }
    }

    fun showMediaPreview(data: Intent) {
        addMedia.apply {
            setImageResource(R.drawable.close)
            setOnClickListener {
                hideMediaPreview()
            }
        }

        mmsURI = data.data ?: data.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as Uri
        mmsTypeString = mActivity.contentResolver.getType(mmsURI)!!
        mmsType = when {
            mmsTypeString.startsWith("image") -> {
                imagePreview.visibility = View.VISIBLE
                imagePreview.setImageURI(mmsURI)
                1
            }
            mmsTypeString.startsWith("audio") -> {
                seekBar.visibility = View.VISIBLE
                playPauseButton.visibility = View.VISIBLE
                mp.apply {
                    setDataSource(mActivity, mmsURI)
                    prepare()
                    seekBar.max = mp.duration / 500

                    val mHandler = Handler(mActivity.mainLooper)
                    mActivity.runOnUiThread(object : Runnable {
                        override fun run() {
                            seekBar.progress = currentPosition / 500
                            mHandler.postDelayed(this, 500)
                        }
                    })

                    seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onStopTrackingTouch(seekBar: SeekBar) {}
                        override fun onStartTrackingTouch(seekBar: SeekBar) {}
                        override fun onProgressChanged(
                            seekBar: SeekBar, progress: Int, fromUser: Boolean
                        ) {
                            if (fromUser) seekTo(progress * 500)
                        }
                    })

                    playPauseButton.apply {
                        setOnClickListener {
                            if (isPlaying) {
                                pause()
                                setImageResource(R.drawable.ic_play)
                            } else {
                                start()
                                setImageResource(R.drawable.ic_pause)
                            }
                        }
                    }
                }
                2
            }
            mmsTypeString.startsWith("video") -> {
                videoView.apply {
                    visibility = View.VISIBLE
                    videoPlayPauseButton.visibility = View.VISIBLE
                    setVideoURI(mmsURI)
                    setOnPreparedListener { mp -> mp.isLooping = true }
                    videoPlayPauseButton.setOnClickListener {
                        if (isPlaying) {
                            pause()
                            videoPlayPauseButton.setImageResource(R.drawable.ic_play)
                        } else {
                            start()
                            videoPlayPauseButton.setImageResource(R.drawable.ic_pause)
                        }
                    }
                }
                3
            }
            else -> 0
        }
        if (mmsType == 0) hideMediaPreview()
    }
}