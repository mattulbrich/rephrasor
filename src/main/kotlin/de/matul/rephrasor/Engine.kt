package de.matul.rephrasor

import com.cjcrafter.openai.OpenAI
import com.cjcrafter.openai.chat.ChatMessage.Companion.toSystemMessage
import com.cjcrafter.openai.chat.ChatMessage.Companion.toUserMessage
import com.cjcrafter.openai.chat.chatRequest
import com.cjcrafter.openai.openAI
import java.util.concurrent.TimeUnit
import java.util.prefs.Preferences
import okhttp3.OkHttpClient
import java.util.Properties

class Engine {

    companion object {
        val knownProviders = mapOf(
            "kit" to "https://ki-toolbox.scc.kit.edu",
            "openai" to "https://api.openai.com")
    }

    var currentProvider: String =
        Preferences.userNodeForPackage(Engine::class.java).get("provider", "openai")
        set(value) {
            field = value
            Preferences.userNodeForPackage(Engine::class.java).put("provider", value)
            client = makeOpenAIClient()
        }

    private fun key(): String {
        return Preferences.userNodeForPackage(Engine::class.java).get("$currentProvider-key", "<undefined>")
    }

    var alignForSentences: Boolean =
        Preferences.userNodeForPackage(Engine::class.java).getBoolean("align-for-sentences", false)
        set(value) {
            field = value
            Preferences.userNodeForPackage(Engine::class.java).putBoolean("align-for-sentences", value)
        }

    private var client = makeOpenAIClient()

    private fun makeOpenAIClient(): OpenAI {
        return openAI {
            apiKey(key())
            baseUrl(knownProviders[currentProvider] ?: throw IllegalArgumentException("Unknown provider: $currentProvider"))
            
            client(OkHttpClient().newBuilder().readTimeout(120, TimeUnit.SECONDS).build())
        }
    }

    var fakeAnswer: String? = null

    private val preambles = loadPreambles()
    val knownPreambles = preambles.keys.toList().sorted()

    fun getPreamble(command: String): String {
        return preambles[command] ?: throw IllegalArgumentException("Unknown command: $command")
    }

    fun callAI(command: String, context: String, input: String, preambleOverride: String?): String {
        val fakeAnswer = fakeAnswer
        if(fakeAnswer != null) {
            return fakeAnswer
        }

        val preamble = preambleOverride ?: getPreamble(command)

        val request = chatRequest {
            fun addMessage(message: com.cjcrafter.openai.chat.ChatMessage) {
                println("Adding message ${message.role}:\n${message.content}")
                this.addMessage(message)
            }
            model("gpt-4o-mini")
            addMessage(preamble.toSystemMessage())
            addMessage(context.toSystemMessage())
            addMessage(input.toUserMessage())
        }
        val response = client.createChatCompletion(request)
        return response.choices.last().message.content!!
    }
}

fun loadPreambles(): Map<String, String> {
    val properties = Properties()
    val inputStream = Engine::class.java.getResourceAsStream("preambles.properties")
    properties.load(inputStream)
    return properties.entries.associate { it.key.toString() to it.value.toString() }
}