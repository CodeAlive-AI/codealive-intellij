package com.smallcloud.codify.panes.gptchat

class State {
    data class QuestionAnswer(val question: String, var answer: String = "", var code: String = "")

    private val conversations_: MutableList<QuestionAnswer> = mutableListOf()

    val conversations: List<QuestionAnswer>
        get() = conversations_.toList()

    fun pushQuestion(question: String) {
        conversations_.add(QuestionAnswer(question))
    }

    fun pushCode(code: String) {
        conversations_.last().code += code
    }

    fun pushAnswer(answer: String) {
        conversations_.last().answer += answer
    }

    fun lastAnswer(): String {
        return conversations_.last().answer
    }

    fun clear() {
        conversations_.clear()
    }

}