package de.matul.rephrasor

import kotlin.math.max
import kotlin.math.min

class TokenRange {
    private var sealed = false
    private val sb = StringBuilder()
    var fromIndex = Integer.MAX_VALUE
        get() = field
    var endIndex = -1
        get() = field

    fun clear() {
        if(sealed) {
            throw IllegalStateException("Cannot clear sealed range")
        }
        sb.clear()
        fromIndex = -1
        endIndex = Integer.MAX_VALUE
    }

    fun appendWithSpace(token: Diffing.Token) {
        if(sealed) {
            throw IllegalStateException("Cannot append to sealed range")
        }
        if(sb.isNotEmpty()) {
            sb.append(" ")
        }
        sb.append(token.text)
        fromIndex = min(fromIndex, token.from)
        endIndex = max(endIndex, token.end)
    }

    fun seal() {
        sealed = true
    }

    override fun toString(): String {
        return sb.toString()
    }

    fun withSpaces(): TokenRange {
        val result = TokenRange()
        result.sb.append(" ").append(sb).append(" ")
        result.fromIndex = fromIndex
        result.endIndex = endIndex
        return result
    }
}

object Diffing {
    sealed class EditAction
    object DELETE : EditAction()
    data class INSERT(val token: Token) : EditAction()
    data class REPLACE(val token: Token) : EditAction()
    data class COPY(val token: Token) : EditAction()

    data class Token(val from: Int, val text: String) {
        val len get() = text.length
        val end get() = from + len - 1
        val after get() = from + len

        operator fun get(idx: Int): Char {
            return text[idx]
        }
        override fun toString(): String {
            return text + "@$from"
        }
    }

    fun tokenize(text: String): List<Token> {
        val result = mutableListOf<Token>()
        val sb = StringBuilder()
        for (pos in 0 until text.length) {
            if(Character.isWhitespace(text[pos])) {
                if(sb.isNotEmpty()) {
                    result.add(Token(pos - sb.length, sb.toString()))
                    sb.clear()
                }
            } else {
                sb.append(text[pos])
            }
        }
        if(sb.isNotEmpty()) {
            result.add(Token(text.length - sb.length, sb.toString()))
        }
        return result;
    }

    fun toSentences(tokens: List<Token>): List<List<Token>> {
        val result = mutableListOf<List<Token>>()
        val sentence = mutableListOf<Token>()
        for(token in tokens) {
            sentence.add(token)
            if(".:?!".indexOf(token[token.len - 1]) >= 0) {
                if(sentence.isNotEmpty()) {
                    result.add(sentence.toList())
                    sentence.clear()
                }
            }
        }
        if(sentence.isNotEmpty()) {
            result.add(sentence)
        }
        return result
    }

    fun minEditDistanceWithActions(
        list1: List<Token>,
        list2: List<Token>
    ): List<EditAction> {
        val len1 = list1.size
        val len2 = list2.size

        val dp = Array(len1 + 1) { IntArray(len2 + 1) }
        val actions = Array(len1 + 1) { Array(len2 + 1) { DELETE as EditAction } }

        dp[0][0] = 0

        for (i in 1..len1) {
            dp[i][0] = i
            actions[i][0] = DELETE
        }

        for (j in 1..len2) {
            dp[0][j] = j
            actions[0][j] = INSERT(list2[j - 1])
        }

        for (i in 1..len1) {
            for (j in 1..len2) {
                if (list1[i - 1].text == list2[j - 1].text) {
                    dp[i][j] = dp[i - 1][j - 1]
                    actions[i][j] = COPY(list2[j - 1])
                } else {
                    val insert = dp[i][j - 1] + 1
                    val delete = dp[i - 1][j] + 1
                    val replace = dp[i - 1][j - 1] + 1

                    dp[i][j] = minOf(insert, delete, replace)

                    actions[i][j] = when (dp[i][j]) {
                        insert -> INSERT(list2[j - 1])
                        delete -> DELETE
                        else ->  // EditAction.REPLACE to "${list1[i - 1]} with ${list2[j - 1]}"
                            REPLACE(list2[j - 1])
                    }
                }
            }
        }

        val resultActions = mutableListOf<EditAction>()
        var i = len1
        var j = len2

        while (i > 0 || j > 0) {
            resultActions.add(actions[i][j])
            val action = actions[i][j]
            when {
                action is INSERT -> j--
                action is DELETE -> i--
                action is REPLACE || action is COPY -> {
                    i--
                    j--
                }
            }
        }

        resultActions.reverse()
        return resultActions
    }

    data class TokenAction(val tokenCount: Int, val replacement: TokenRange?) {
        override fun toString(): String {
            if(replacement == null) {
                return "$tokenCount/COPY"
            } else {
                return "$tokenCount/'$replacement.'"
            }
        }
    }

    fun consolidateActions(actions: List<EditAction>): List<TokenAction> {
        val result = mutableListOf<TokenAction>()
        var tokenCount = 0
        var replacement = TokenRange()
        var lastType: EditAction? = null

        for (action in actions) {
            when  {
                action is COPY -> {
                    if (lastType != null && lastType !is COPY) {
                        replacement.seal()
                        result.add(TokenAction(tokenCount, replacement))
                        tokenCount = 1
                        replacement = TokenRange()
                    } else {
                        tokenCount++
                    }
                }

                action is INSERT -> {
                    if (lastType != null && lastType is COPY) {
                        result.add(TokenAction(tokenCount, null))
                        tokenCount = 0
                    }

                    replacement.appendWithSpace(action.token)
                }

                action is DELETE -> {
                    if (lastType != null && lastType is COPY) {
                        result.add(TokenAction(tokenCount, null))
                        tokenCount = 1
                    } else {
                        tokenCount++
                    }
                }

                action is REPLACE -> {
                    if (lastType != null && lastType is COPY) {
                        result.add(TokenAction(tokenCount, null))
                        tokenCount = 1
                    } else {
                        tokenCount++;
                    }
                    replacement.appendWithSpace(action.token)
                }
            }
            lastType = action
        }

        if(tokenCount >= 0) {
            if(lastType != null && lastType is COPY)
                result.add(TokenAction(tokenCount, null))
            else
                result.add(TokenAction(tokenCount, replacement))
        }

        return result
    }
}
