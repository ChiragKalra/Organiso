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
    private var wordFeatures: Array<String>
    private var mappedWordFeatures: Array<Int>

    init {
        wordFeatures = getWordFeatures()
        val sorted = wordFeatures.clone().sortedArray()
        val hashMap = HashMap<String, Int>()
        for ((index, word) in wordFeatures.withIndex()) {
            hashMap[word] = index
        }
        mappedWordFeatures = Array(wordFeatures.size) {
            hashMap[sorted[it]]!!
        }
        wordFeatures = sorted
    }

    private fun getWordFeatures(): Array<String> {
        val fileStr = mContext.assets.open("words.csv").bufferedReader().use{
            it.readText()
        }
        return fileStr.split("\r\n").dropLast(1).toTypedArray()
    }

    fun getFeatureVector(message: Message) : Array<Float> {
        val k = nonWordFeatures.size
        val l = wordFeatures.size
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

        for (word in text.split(" ", ",", ".", "-", "!", "?", "(", ")", "\'", "\"")) {
            val ind = wordFeatures.binarySearch(word)
            if (ind > -1) {
                val i = mappedWordFeatures[ind]
                features[k + i] = 1f
            }
            features[5] += 1f
        }
        features[5] /= 150f
        return features
    }

    fun getFeaturesLength() =
        nonWordFeatures.size + wordFeatures.size


}