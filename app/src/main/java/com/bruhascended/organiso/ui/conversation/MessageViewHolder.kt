package com.bruhascended.organiso.ui.conversation

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.webkit.MimeTypeMap
import android.widget.*
import androidx.core.content.FileProvider
import androidx.core.view.doOnDetach
import com.bruhascended.organiso.R
import com.bruhascended.core.db.Message
import com.bruhascended.core.data.DateTimeProvider
import com.bruhascended.organiso.ui.common.ScrollEffectFactory
import com.squareup.picasso.Picasso
import java.io.File
import java.io.FileOutputStream
import java.util.*

@SuppressLint("ResourceType")
class MessageViewHolder(
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

    lateinit var message: Message

    var searchKey = ""

    private val messageTextView: TextView = root.findViewById(R.id.message)
    private val timeTextView: TextView = root.findViewById(R.id.time)
    val slider: SeekBar = root.findViewById(R.id.slider)
    val content: LinearLayout = root.findViewById(R.id.content)
    private val statusTextView: TextView? = try {
        root.findViewById(R.id.status)
    } catch (e: Exception) {
        null
    }

    var defaultBackground: Drawable
    var selectedColor = 0
    var backgroundColor = 0
    var textColor = 0
    var failed = false

    init {
        val tp = mContext.obtainStyledAttributes(intArrayOf(
            R.attr.multiChoiceSelectorColor,
            android.R.attr.selectableItemBackground,
            R.attr.unreadTextColor,
            R.attr.backgroundColor
        ))
        defaultBackground = tp.getDrawable(1)!!
        selectedColor = tp.getColor(0, 0)
        backgroundColor = tp.getColor(3, 0)
        textColor = tp.getColor(2, 0)
        tp.recycle()
    }


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
            "com.bruhascended.organiso.fileProvider", File(message.path!!)
        )
        mContext.grantUriPermission(
            "com.bruhascended.organiso", contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION
        )

        contentIntent.setDataAndType(contentUri, mmsTypeString)
        contentIntent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION

        when {
            mmsTypeString.startsWith("image") -> showImage()
            mmsTypeString.startsWith("audio") -> showAudio()
            mmsTypeString.startsWith("video") -> showVideo()
        }
    }

    fun onBind(retryEnabled: Boolean = false) {
        hideMedia()

        messageTextView.text = if (!searchKey.isBlank()) SpannableString(message.text).apply {
            val regex = Regex("\\b${searchKey}")
            val matches = regex.findAll(message.text.toLowerCase(Locale.ROOT))
            for (match in matches) {
                val index = match.range
                setSpan(BackgroundColorSpan(highlightColor), index.first, index.last+1, flag)
            }
        } else message.text
        timeTextView.text = dtp.getFull(message.time)
        timeTextView.alpha = 0f

        if (message.type != 1) {
            statusTextView!!.visibility = VISIBLE
            statusTextView.setTextColor(textColor)
            statusTextView.text =  when {
                message.delivered -> mContext.getString(R.string.delivered)
                message.type == 2 -> mContext.getString(R.string.sent)
                message.type == 6 -> mContext.getString(R.string.queued)
                else -> {
                    failed = true
                    statusTextView.setTextColor(mContext.getColor(R.color.red))
                    if (retryEnabled) mContext.getString(R.string.failed_retry)
                    else mContext.getString(R.string.failed)
                }
            }
        }

        if (message.path != null) {
            showMedia()
            if (message.text.isBlank()) messageTextView.visibility = GONE
        }

    }

    fun rangeSelectionAnim() {
        backgroundAnimator = ValueAnimator.ofObject(
            ArgbEvaluator(),
            selectedColor,
            backgroundColor
        ).apply {
            duration = 700
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animator ->
                root.setBackgroundColor(animator.animatedValue as Int)
            }
            start()
        }
    }

    fun stopBgAnim() {
        backgroundAnimator?.cancel()
    }

    fun showTime() {
        timeTextView.apply {
            if (alpha != 0f) return
            postOnAnimation {
                animate().alpha(1f).setDuration(700).start()
                postDelayed( {
                    animate().alpha(0f).setDuration(700).start()
                }, 3700)
            }
        }
    }

}