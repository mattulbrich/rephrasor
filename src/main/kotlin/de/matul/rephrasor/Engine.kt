package de.matul.rephrasor

import com.cjcrafter.openai.chat.ChatMessage.Companion.toSystemMessage
import com.cjcrafter.openai.chat.ChatMessage.Companion.toUserMessage
import com.cjcrafter.openai.chat.chatRequest
import com.cjcrafter.openai.openAI
import java.util.concurrent.TimeUnit
import java.util.prefs.Preferences
import okhttp3.OkHttpClient
import java.util.Properties

class Engine {

    private val key by lazy {
        Preferences.userNodeForPackage(Engine::class.java).get("openai-key", "<undefined>")
    }

    val openai = openAI {
        apiKey(key)
        client(OkHttpClient().newBuilder().readTimeout(120, TimeUnit.SECONDS).build())
    }

    var fakeAnswer: String? = null

    private val preambles = loadPreambles()
    val knownPreambles = preambles.keys.toList().sorted()

    fun callAI(command: String, input: String): String {
        val fakeAnswer = fakeAnswer
        if(fakeAnswer != null) {
            return fakeAnswer
        }

        val request = chatRequest {
            model("gpt-4o-mini")
            addMessage(preambles[command]!!.toSystemMessage())
            addMessage(input.toUserMessage())
        }
        val response = openai.createChatCompletion(request)
        return response.choices.last().message.content!!
    }
}

fun loadPreambles(): Map<String, String> {
    val properties = Properties()
    val inputStream = Engine::class.java.getResourceAsStream("preambles.properties")
    properties.load(inputStream)
    return properties.entries.associate { it.key.toString() to it.value.toString() }
}