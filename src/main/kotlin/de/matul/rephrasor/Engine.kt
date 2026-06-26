package de.matul.rephrasor

import com.cjcrafter.openai.OpenAI
import com.cjcrafter.openai.OpenAIImpl
import com.cjcrafter.openai.chat.ChatMessage.Companion.toSystemMessage
import com.cjcrafter.openai.chat.ChatMessage.Companion.toUserMessage
import com.cjcrafter.openai.chat.chatRequest
import com.cjcrafter.openai.openAI
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.TimeUnit
import java.util.prefs.Preferences
import okhttp3.OkHttpClient
import java.util.Properties
import java.nio.charset.StandardCharsets

// curl -X GET "https://ki-toolbox.scc.kit.edu/api/v1/models" -H "Authorization: Bearer sk-0f640707711e4a26bf79145ab7653aa2" -H "Content-Type: application/json"

data class ModelInfo(val provider: String, val baseUrl: String, val modelName: String?, val key: String)
@Serializable
data class ModelRespInfo(val id: String, val created: Long, val owned_by: String)
@Serializable
data class ModelListResponse(val data: List<ModelRespInfo>)

class Engine {

    companion object {
        val node = Preferences.userNodeForPackage(Engine::class.java)
        fun computeProviders(): Map<String, ModelInfo> {
                return generateSequence(1) { it + 1 } // Infinite stream of 1, 2, 3...
                    .takeWhile { node.get("provider-$it", null) != null }      // Stops the stream when the integer reaches 6
                    .map {
                        val provider = Preferences.userNodeForPackage(Engine::class.java).get("provider-$it", null)
                            ?: error("Missing name for provider $it")
                        val baseUrl = Preferences.userNodeForPackage(Engine::class.java).get("baseUrl-$it", null)
                            ?: error("Missing baseUrl for provider $provider")
                        val modelName = Preferences.userNodeForPackage(Engine::class.java).get("modelName-$it", null)
                        val key = Preferences.userNodeForPackage(Engine::class.java).get("key-$it", null)
                            ?: error("Missing key for provider $provider")
                        provider to ModelInfo(provider, baseUrl, modelName, key)
                    }.toMap()
        }
        var knownProviders = computeProviders()
        fun saveProviders(providers: List<ModelInfo>) {
            val node = Preferences.userNodeForPackage(Engine::class.java)
            // Clear old providers
            var i = 1
            while (node.get("provider-$i", null) != null) {
                node.remove("provider-$i")
                node.remove("baseUrl-$i")
                node.remove("modelName-$i")
                node.remove("key-$i")
                i++
            }
            // Save new providers
            for ((index, provider) in providers.withIndex()) {
                val i = index + 1
                node.put("provider-$i", provider.provider)
                node.put("baseUrl-$i", provider.baseUrl)
                if(provider.modelName != null && provider.modelName.isNotBlank()) {
                    node.put("modelName-$i", provider.modelName)
                }
                node.put("key-$i", provider.key)
            }
            knownProviders = computeProviders()
        }
    }

    var currentProvider: String =
        Preferences.userNodeForPackage(Engine::class.java).get("provider", "<unknown>")
        set(value) {
            field = value
            Preferences.userNodeForPackage(Engine::class.java).put("provider", value)
        }

    var currentModel: String
        get() {
            val current = knownProviders[currentProvider] ?: throw IllegalArgumentException("Unknown provider: $currentProvider")
            return current.modelName ?: "<unknown>"
        }
        set(value) {
            val current = knownProviders[currentProvider] ?: throw IllegalArgumentException("Unknown provider: $currentProvider")
            val new = if(value.isBlank()) {
                knownProviders + (currentProvider to current.copy(modelName = null))
            } else {
                knownProviders + (currentProvider to current.copy(modelName = value))
            }
            saveProviders(new.values.toList())
        }

    var alignForSentences: Boolean =
        Preferences.userNodeForPackage(Engine::class.java).getBoolean("align-for-sentences", false)
        set(value) {
            field = value
            Preferences.userNodeForPackage(Engine::class.java).putBoolean("align-for-sentences", value)
        }

    fun makeOpenAIClient(): OpenAI {
        val current = knownProviders[currentProvider] ?: throw IllegalArgumentException("Unknown provider: $currentProvider")
        return openAI {
            apiKey(current.key)
            baseUrl(current.baseUrl)
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
            val current = knownProviders[currentProvider] ?: throw IllegalArgumentException("Unknown provider: $currentProvider")
            val modelName = current.modelName ?: throw IllegalArgumentException("Model name not specified for provider: $currentProvider")
            model(modelName)
            addMessage(preamble.toSystemMessage())
            addMessage(context.toSystemMessage())
            addMessage(input.toUserMessage())
        }

        val client = makeOpenAIClient()
        val response = client.createChatCompletion(request)
        return response.choices.last().message.content!!
    }

    fun listModels(): List<String> {
        val provider = knownProviders[currentProvider] ?: throw IllegalArgumentException("Unknown provider: $currentProvider")
        val url = provider.baseUrl.removeSuffix("/") + "/v1/models"
        val key = provider.key
        val request = okhttp3.Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer " + key)
            .get()
            .build()

        val client = okhttp3.OkHttpClient()
        val response = client.newCall(request).execute()

        if(response.code != 200) {
            throw RuntimeException("Failed to list models: ${response.code} ${response.message}")
        }

        val body = response.body?.string() ?: return emptyList()

        val json = Json {
            ignoreUnknownKeys = true
        }

        val modelList = json.parseToJsonElement(body).jsonObject

        return modelList["data"]?.jsonArray?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content } ?: emptyList()
        //return listOf() // modelList.data.map { it.id }
    }
}

fun loadPreambles(): Map<String, String> {
    val properties = Properties()
    val inputStream = Engine::class.java.getResourceAsStream("preambles.properties")
    requireNotNull(inputStream) { "Missing resource: preambles.properties" }
    inputStream.reader(StandardCharsets.UTF_8).use { reader ->
        properties.load(reader)
    }
    return properties.entries.associate { it.key.toString() to it.value.toString() }
}
