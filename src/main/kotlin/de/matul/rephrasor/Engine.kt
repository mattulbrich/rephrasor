package de.matul.rephrasor

import com.cjcrafter.openai.chat.ChatMessage.Companion.toSystemMessage
import com.cjcrafter.openai.chat.ChatMessage.Companion.toUserMessage
import com.cjcrafter.openai.chat.chatRequest
import com.cjcrafter.openai.openAI
import java.util.concurrent.TimeUnit
import java.util.prefs.Preferences
import okhttp3.OkHttpClient

class Engine {

    private val key by lazy {
        Preferences.userNodeForPackage(Engine::class.java).get("openai-key", "<undefined>")
    }

    val openai = openAI {
        apiKey(key)
        client(OkHttpClient().newBuilder().readTimeout(100, TimeUnit.SECONDS).build())
    }

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

fun loadPreambles(): Map<String, String> {
    return mapOf(
        "Rephrase" to
                "You are an editor for a computer science journal. " +
                "You are an expert on Formal Methods in Computer Science, in particular in logics and deduction. " +
                "Your job is it to improve scientific quality and the language of text. " +
                "Do not add new content to the text, but rephrase text such that it sounds more like typical scientific texts of the formal methods domain. " +
                "You do not repeat the query presented to you. " +
                "You keep all latex or markdown annotations unchanged. " +
                "You prefer British over American English.",
        "Check" to "You are an editor for a computer science journal. " +
                "You are an expert on Formal Methods in Computer Science, in particular in logics and deduction. " +
                "Your job is it to improve scientific quality and the language of text. " +
                "Do not add new content to the text, but only spellcheck text and make sure the English grammar is correct. " +
                "You do not repeat the query presented to you. " +
                "You keep all latex or markdown annotations unchanged. " +
                "You prefer British over American English.",
        "Translate" to "You are an editor for a computer science journal. " +
                "You are an expert on Formal Methods in Computer Science, in particular in logics and deduction. " +
                "Your job is it to improve translate texts from other languages into English. " +
                "Do not add new content to the text, but translate the input into English. " +
                "You do not repeat the query presented to you. " +
                "You keep all latex or markdown annotations unchanged. " +
                "You prefer British over American English."
    )
}