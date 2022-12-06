package com.smallcloud.codify.utils

import org.apache.commons.lang.StringUtils


fun difference(currentText: String?, predictedText: String?, offset: Int): String? {
    return if ((currentText == null) or (predictedText == null)) {
        null
    } else {
        val startDiffIdx = StringUtils.indexOfDifference(currentText!!.substring(0, offset), predictedText!!)
        // User has made some changes before the request, drop the suggestion
        if (offset != startDiffIdx) {
            return null
        }
        // There are no differences between the response and request
        if (startDiffIdx == -1) {
            return null
        }

        val currentTextTail = currentText.substring(startDiffIdx)
        val predictedTextTail = predictedText.substring(startDiffIdx)
        if (currentTextTail == predictedTextTail) {
            return null
        }

        val endDiffIdx = predictedTextTail.indexOf(currentTextTail)
        if (endDiffIdx > 0) {
            predictedTextTail.substring(0, endDiffIdx)
        } else if ((endDiffIdx == -1) && currentText.isNotEmpty()) {
            null
        } else {
            predictedTextTail
        }
    }
}
