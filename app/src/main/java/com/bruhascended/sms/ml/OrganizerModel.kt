package com.bruhascended.sms.ml

import android.app.Activity
import android.content.Context
import com.bruhascended.sms.data.MESSAGE_CHECK_COUNT
import com.bruhascended.sms.db.Message
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.lang.Integer.min
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel


class OrganizerModel (context: Context) {
    private val mContext = context

    private fun loadModelFile(activity: Activity): MappedByteBuffer {
        val fileDescriptor = activity.assets.openFd("model.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun getPredictions (messages: ArrayList<Message>, features: Array<Array<Float>>) : FloatArray {
        val tfliteModel = loadModelFile(mContext as Activity)
        val delegate = GpuDelegate()
        val options = Interpreter.Options().addDelegate(delegate)
        val tflite = Interpreter(tfliteModel, options)

        val n = features[0].size

        val probs = FloatArray(5){0f}

        for (i in 0 until min(features.size, MESSAGE_CHECK_COUNT)) {
            val feature = features[i]

            val inputData = ByteBuffer.allocateDirect(n * 4)
            inputData.order(ByteOrder.nativeOrder())
            for (it in feature) {
                inputData.putFloat(it)
            }
            val out = Array(1){FloatArray(5)}
            tflite.run(inputData, out)

            messages[i].label = out[0].indexOf(out[0].max()!!)
            for (j in 0..4) probs[j] += out[0][j]

            if (i==9) break
        }

        delegate.close()
        tflite.close()
        return probs
    }

}