package de.matul.rephrasor.de.matul.rephrasor

object Diffing {
    enum class EditAction {
        INSERT, DELETE, REPLACE, COPY
    }

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
            if(Character.isSpaceChar(text[pos])) {
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
    ): List<Pair<EditAction, String>> {
        val len1 = list1.size
        val len2 = list2.size

        val dp = Array(len1 + 1) { IntArray(len2 + 1) }
        val actions = Array(len1 + 1) { Array(len2 + 1) { EditAction.COPY to "" } }

        for (i in 0..len1) {
            dp[i][0] = i
            actions[i][0] = EditAction.DELETE to (list1.getOrNull(i - 1)?.text ?: "")
        }

        for (j in 0..len2) {
            dp[0][j] = j
            actions[0][j] = EditAction.INSERT to (list2.getOrNull(j - 1)?.text ?: "")
        }

        for (i in 1..len1) {
            for (j in 1..len2) {
                if (list1[i - 1].text == list2[j - 1].text) {
                    dp[i][j] = dp[i - 1][j - 1]
                    actions[i][j] = EditAction.COPY to list1[i - 1].text
                } else {
                    val insert = dp[i][j - 1] + 1
                    val delete = dp[i - 1][j] + 1
                    val replace = dp[i - 1][j - 1] + 1

                    dp[i][j] = minOf(insert, delete, replace)

                    actions[i][j] = when (dp[i][j]) {
                        insert -> EditAction.INSERT to list2[j - 1].text
                        delete -> EditAction.DELETE to list1[i - 1].text
                        else ->  // EditAction.REPLACE to "${list1[i - 1]} with ${list2[j - 1]}"
                            EditAction.REPLACE to list2[j - 1].text
                    }
                }
            }
        }

        val resultActions = mutableListOf<Pair<EditAction, String>>()
        var i = len1
        var j = len2

        while (i > 0 || j > 0) {
            resultActions.add(actions[i][j])
            when (actions[i][j].first) {
                EditAction.INSERT -> j--
                EditAction.DELETE -> i--
                EditAction.REPLACE, EditAction.COPY -> {
                    i--
                    j--
                }
            }
        }

        resultActions.reverse()
        return resultActions
    }

    data class TokenAction(val tokenCount: Int, val replacement: String?) {
        override fun toString(): String {
            if(replacement == null) {
                return "$tokenCount/COPY"
            } else {
                return "$tokenCount/'$replacement'"
            }
        }
    }

    fun consolidateActions(actions: List<Pair<EditAction, String>>): List<TokenAction> {
        val result = mutableListOf<TokenAction>()
        var tokenCount = 0
        var replacement = StringBuilder()
        var lastType: EditAction? = null

        for (action in actions) {
            when (action.first) {
                EditAction.COPY -> {
                    if (lastType != null && lastType != EditAction.COPY) {
                        result.add(TokenAction(tokenCount, replacement.toString()))
                        tokenCount = 1
                        replacement.clear()
                    } else {
                        tokenCount++
                    }
                }

                EditAction.INSERT -> {
                    if (lastType == EditAction.COPY) {
                        result.add(TokenAction(tokenCount, null))
                        tokenCount = 0
                    }

                    replacement.appendWithSpace(action.second)
                }

                EditAction.DELETE -> {
                    if (lastType == EditAction.COPY) {
                        result.add(TokenAction(tokenCount, null))
                        tokenCount = 1
                    } else {
                        tokenCount++
                    }
                }

                EditAction.REPLACE -> {
                    if (lastType == EditAction.COPY) {
                        result.add(TokenAction(tokenCount, null))
                        tokenCount = 1
                    } else {
                        tokenCount++;
                    }
                    replacement.appendWithSpace(action.second)
                }
            }
            lastType = action.first
        }

        if(tokenCount >= 0) {
            if(lastType == EditAction.COPY)
                result.add(TokenAction(tokenCount, null))
            else
                result.add(TokenAction(tokenCount, replacement.toString()))
        }

        return result
    }
}

private fun StringBuilder.appendWithSpace(second: String) {
    if(this.isNotEmpty()) {
        this.append(" ")
    }
    this.append(second)
}
