package com.bruhascended.core.model

import android.content.Context
import com.bruhascended.core.db.Message
import org.tensorflow.lite.Interpreter
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
        const val HP_NUM_THREADS = 2
        const val HP_MESSAGE_CHECK_COUNT = 6
    }


    private val mContext = context
    private val fe = FeatureExtractor(mContext)
    private var tfliteModel = loadModelFile()
    // Disabling GPU and NNAPI for stability on Android 14/Pixel 7
    private val options = Interpreter.Options()
        .setNumThreads(HP_NUM_THREADS)
    private val tflite = Interpreter(tfliteModel, options)
    init {
        val inputShape = tflite.getInputTensor(0).shape()
        val outputShape = tflite.getOutputTensor(0).shape()
        android.util.Log.d("OrganizerModel", "Model loaded. Input shape: ${inputShape.contentToString()}, Output shape: ${outputShape.contentToString()}")
    }

    private val n = fe.getFeaturesLength()

    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd("model.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        val buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        fileDescriptor.close()
        return buffer
    }

    @Synchronized
    fun getPrediction(message: Message) = getPredictions(arrayListOf(message))

    @Synchronized
    fun getPredictions(messages: ArrayList<Message>) : Array<Float> {
        val probs = Array(5) { 0f }
        android.util.Log.d("OrganizerModel", "Predicting for ${messages.size} messages")

        for (i in 0 until min(messages.size, HP_MESSAGE_CHECK_COUNT)) {
            val feature = fe.getFeatureVector(messages[i])
            if (feature.size != n) {
                android.util.Log.e("OrganizerModel", "Feature size mismatch: ${feature.size} vs $n")
                continue
            }

            val inputData = ByteBuffer.allocateDirect(n * 4)
            inputData.order(ByteOrder.nativeOrder())
            for (it in feature) {
                inputData.putFloat(it)
            }
            val out = Array(1) { FloatArray(5) }
            try {
                tflite.run(inputData, out)
            } catch (e: Exception) {
                android.util.Log.e("OrganizerModel", "TFLite run failed for n=$n", e)
                throw e
            }

            for (j in 0..4) probs[j] += out[0][j]
        }
        return probs
    }

    fun close() {
        tflite.close()
        tfliteModel.clear()
    }

}