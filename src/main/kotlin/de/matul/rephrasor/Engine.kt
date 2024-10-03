package de.matul.rephrasor

import com.cjcrafter.openai.chat.ChatMessage.Companion.toSystemMessage
import com.cjcrafter.openai.chat.ChatMessage.Companion.toUserMessage
import com.cjcrafter.openai.chat.chatRequest
import com.cjcrafter.openai.openAI
import java.util.prefs.Preferences

class Engine {

    private val key by lazy {
        Preferences.userNodeForPackage(Engine::class.java).get("openai-key", "<undefined>")
    }

    val openai = openAI { apiKey(key) }

    private val preambles = loadPreambles()
    val knownPreambles = preambles.keys.toList()

    fun callAI(command: String, input: String): String {
        val request = chatRequest {
            model("gpt-4o-mini")
            addMessage(preambles[command]!!.toSystemMessage())
            addMessage(input.toUserMessage())
        }
        val response = openai.createChatCompletion(request)
        return response.choices.last().message.content!!
    }
}
