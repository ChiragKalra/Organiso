package com.bruhascended.organiso.common

import android.content.Intent
import android.graphics.drawable.AnimatedVectorDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Parcelable
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.VideoView
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import com.bruhascended.organiso.R
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

abstract class MediaPreviewActivity : AppCompatActivity() {
    companion object {
        fun getMimeType(url: String): String {
            val extension = MimeTypeMap.getFileExtensionFromUrl(url)
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: ""
        }
    }

    private val mp = MediaPlayer()
    protected abstract var mVideoView: VideoView
    protected abstract var mImagePreview: ImageView
    protected abstract var mSeekBar: SeekBar
    protected abstract var mPlayPauseButton: ImageButton
    protected abstract var mVideoPlayPauseButton: ImageButton
    protected abstract var mAddMedia: ImageButton

    protected var isMms = false
    protected lateinit var mmsTypeString: String
    protected lateinit var mmsURI: Uri

    private val loadMediaResult = registerForActivityResult(StartActivityForResult()) {
        if (it.data != null && it.data!!.data != null) {
            showMediaPreview(it.data!!)
        }
    }

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
                    runOnUiThread {
                        alpha = 1f
                        visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun loadMedia() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "*/*"
        intent.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "audio/*", "video/*"))
        loadMediaResult.launch(intent)
    }

    override fun onStart() {
        super.onStart()

        mAddMedia.setOnClickListener{
            loadMedia()
        }
    }

    fun hideMediaPreview() {
        mVideoView.stopPlayback()
        mp.reset()
        fadeAway(mVideoView, mImagePreview, mSeekBar, mPlayPauseButton, mVideoPlayPauseButton)
        isMms = false
        mAddMedia.setImageResource(R.drawable.close_to_add)
        (mAddMedia.drawable as AnimatedVectorDrawable).start()
        mAddMedia.setOnClickListener {
            loadMedia()
        }
    }

    fun showMediaPreview(data: Intent) {
        mAddMedia.apply {
            setImageResource(R.drawable.close)
            setOnClickListener {
                hideMediaPreview()
            }
        }

        mmsURI = data.data ?: data.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as Uri
        mmsTypeString = contentResolver.getType(mmsURI)!!
        isMms = when {
            mmsTypeString.startsWith("image") -> {
                mImagePreview.visibility = View.VISIBLE
                mImagePreview.setImageURI(mmsURI)
                true
            }
            mmsTypeString.startsWith("audio") -> {
                mSeekBar.visibility = View.VISIBLE
                mPlayPauseButton.visibility = View.VISIBLE
                val mContext = this
                mp.apply {
                    setDataSource(mContext, mmsURI)
                    prepareAsync()
                    setOnPreparedListener {
                        mSeekBar.max = mp.duration / 500

                        val mHandler = Handler(mainLooper)
                        runOnUiThread(object : Runnable {
                            override fun run() {
                                mSeekBar.progress = currentPosition / 500
                                mHandler.postDelayed(this, 500)
                            }
                        })

                        mSeekBar.setOnSeekBarChangeListener(object :
                            SeekBar.OnSeekBarChangeListener {
                            override fun onStopTrackingTouch(seekBar: SeekBar) {}
                            override fun onStartTrackingTouch(seekBar: SeekBar) {}
                            override fun onProgressChanged(
                                seekBar: SeekBar, progress: Int, fromUser: Boolean
                            ) {
                                if (fromUser) seekTo(progress * 500)
                            }
                        })

                        mPlayPauseButton.apply {
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
                }
                true
            }
            mmsTypeString.startsWith("video") -> {
                mVideoView.apply {
                    visibility = View.VISIBLE
                    mVideoPlayPauseButton.visibility = View.VISIBLE
                    setVideoURI(mmsURI)
                    setOnPreparedListener { mp -> mp.isLooping = true }
                    mVideoPlayPauseButton.setOnClickListener {
                        if (isPlaying) {
                            pause()
                            mVideoPlayPauseButton.setImageResource(R.drawable.ic_play)
                        } else {
                            start()
                            mVideoPlayPauseButton.setImageResource(R.drawable.ic_pause)
                        }
                    }
                }
                true
            }
            else -> false
        }
        if (!isMms) hideMediaPreview()
    }


}

const val ARG_MEDIA = 0