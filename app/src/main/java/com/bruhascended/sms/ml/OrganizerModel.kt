package com.bruhascended.sms.ml

import android.content.Context
import android.os.Bundle
import com.bruhascended.sms.data.MESSAGE_CHECK_COUNT
import com.bruhascended.sms.db.Message
import com.google.firebase.analytics.FirebaseAnalytics
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
    private val fe = FeatureExtractor(mContext)
    private val firebaseAnalytics = FirebaseAnalytics.getInstance(mContext)

    private fun loadModelFile(context: Context): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd("model.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun getPredictions(messages: ArrayList<Message>) : FloatArray {

        val tfliteModel = loadModelFile(mContext)
        val delegate = GpuDelegate()
        val options = Interpreter.Options()
            .setUseNNAPI(true)
            .addDelegate(delegate)
        val tflite =  try {
            Interpreter(tfliteModel, options)
        } catch (e: IllegalArgumentException) {
            Interpreter(tfliteModel, Interpreter.Options())
        }

        val n = fe.getFeaturesLength()

        val probs = FloatArray(5){0f}

        for (i in 0 until min(messages.size, MESSAGE_CHECK_COUNT)) {
            val feature = fe.getFeatureVector(messages[i])

            val inputData = ByteBuffer.allocateDirect(n * 4)
            inputData.order(ByteOrder.nativeOrder())
            for (it in feature) {
                inputData.putFloat(it)
            }
            val out = Array(1){FloatArray(5)}
            tflite.run(inputData, out)

            messages[i].label = out[0].toList().indexOf(out[0].maxOrNull())
            for (j in 0..4) probs[j] += out[0][j]

            val bundle = Bundle()
            bundle.putString(FirebaseAnalytics.Param.METHOD, "default")
            firebaseAnalytics.logEvent("message_organised", bundle)
        }

        delegate.close()
        tflite.close()
        return probs
    }

}