package com.bruhascended.core.model

import android.content.Context
import com.bruhascended.core.analytics.AnalyticsLogger
import com.bruhascended.core.constants.EVENT_MESSAGE_ORGANISED
import com.bruhascended.core.db.Message
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.lang.Integer.min
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

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


class OrganizerModel (private val context: Context) {

    companion object {
        const val HP_NUM_THREADS = 6
        const val HP_MESSAGE_CHECK_COUNT = 6
    }


    private val mContext = context
    private val fe = FeatureExtractor(mContext)
    private var tfliteModel = loadModelFile()
    private val delegate = GpuDelegate()
    private val options = Interpreter.Options()
        .setUseNNAPI(true)
        .setNumThreads(HP_NUM_THREADS)
        .addDelegate(delegate)
    private val tflite =  try {
        Interpreter(tfliteModel, options)
    } catch (e: IllegalArgumentException) {
        Interpreter(tfliteModel, Interpreter.Options())
    }

    private val n = fe.getFeaturesLength()

    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd("model.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun getPrediction(message: Message) = getPredictions(arrayListOf(message))

    fun getPredictions(messages: ArrayList<Message>) : Array<Float> {
        val probs = Array(5) { 0f }

        for (i in 0 until min(messages.size, HP_MESSAGE_CHECK_COUNT)) {
            val feature = fe.getFeatureVector(messages[i])

            val inputData = ByteBuffer.allocateDirect(n * 4)
            inputData.order(ByteOrder.nativeOrder())
            for (it in feature) {
                inputData.putFloat(it)
            }
            val out = Array(1) { FloatArray(5) }
            tflite.run(inputData, out)

            for (j in 0..4) probs[j] += out[0][j]

            AnalyticsLogger(mContext).log(EVENT_MESSAGE_ORGANISED)
        }
        return probs
    }

    fun close() {
        delegate.close()
        tflite.close()
        tfliteModel.clear()
    }

}