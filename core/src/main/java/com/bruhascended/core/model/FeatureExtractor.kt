package com.bruhascended.core.model

import android.content.Context
import com.bruhascended.core.db.Message

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

class FeatureExtractor (context: Context) {
    private var mContext = context
    private val nonWordFeatures = arrayOf(
        "Time",
        "Digit",
        "Decimal",
        "URL",
        "Date",
        "NumberOfWords"
    )
    private val wordFeatures = getWordFeatures()

    private fun getWordFeatures(): List<String> {
        val fileStr = mContext.assets.open("words.csv").bufferedReader().use{
            it.readText()
        }
        return fileStr.split("\n").dropLast(1)
    }

    private fun getFeatures(k: Int, l: Int, message: Message, wordFeatures: List<String>) : Array<Float> {
        val features = Array(k+l){0f}

        var text = removeLines(message.text)

        features[0] = time(message.time)

        var yeah = removeDates(text)
        text = yeah.first
        features[1] = yeah.second

        yeah = removeNumbers(text)
        text = yeah.first
        features[2] = yeah.second

        yeah = removeDecimals(text)
        text = yeah.first
        features[3] = yeah.second

        yeah = trimUrls(text)
        text = yeah.first
        features[4] = yeah.second

        text = stem(text)

        for (i in 0 until l) {
            features[k + i] = (wordFeatures[i] in text).toFloat()
            features[5] += features[k+i]
        }
        features[5] /= 150f
        return features
    }

    fun getFeaturesLength() : Int {
        val k = nonWordFeatures.size
        val l = wordFeatures.size
        return k+l
    }

    /*
    fun getFeatureMatrix(messages: ArrayList<Message>): Array<Array<Float>> {
        val wordFeatures = getWordFeatures()

        val k = nonWordFeatures.size
        val l = wordFeatures.size
        val m = messages.size

        return Array(m){getFeatures(k, l, messages[it], wordFeatures)}
    }*/


    fun getFeatureVector(message: Message): Array<Float> {
        val k = nonWordFeatures.size
        val l = wordFeatures.size
        return getFeatures(k, l, message, wordFeatures)
    }


}