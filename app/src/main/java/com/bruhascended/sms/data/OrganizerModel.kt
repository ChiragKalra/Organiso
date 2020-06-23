package com.bruhascended.sms.data

import android.app.Activity
import android.content.Context
import com.bruhascended.sms.db.Message
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
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

    fun getPredictions (messages: ArrayList<Message>, features: Array<Array<Float>>) : Int {
        val tfliteModel = loadModelFile(mContext as Activity)
        val delegate = GpuDelegate()
        val options = Interpreter.Options().addDelegate(delegate)
        val tflite = Interpreter(tfliteModel, options)

        val n = features[0].size

        val count = IntArray(5)

        features.forEachIndexed { i, feature ->
            val inputData = ByteBuffer.allocateDirect(n * 4)
            inputData.order(ByteOrder.nativeOrder())
            for (it in feature) {
                inputData.putFloat(it)
            }
            val out = Array(1){FloatArray(5)}
            tflite.run(inputData, out)

            messages[i].label = out[0].indexOf(out[0].max()!!)
            count[messages[i].label]++
        }

        delegate.close()
        tflite.close()
        return count.indexOf(count.max()!!)
    }

}