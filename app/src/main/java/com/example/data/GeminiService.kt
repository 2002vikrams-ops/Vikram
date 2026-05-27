package com.example.data

import android.util.Log
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import com.example.BuildConfig

@JsonClass(generateAdapter = true)
data class GeminiPart(
    @Json(name = "text") val text: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    @Json(name = "parts") val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    @Json(name = "contents") val contents: List<GeminiContent>,
    @Json(name = "systemInstruction") val systemInstruction: GeminiContent? = null
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    @Json(name = "content") val content: GeminiContent? = null
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    @Json(name = "candidates") val candidates: List<GeminiCandidate>? = null
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiClient {
    private const val TAG = "GeminiClient"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    val service: GeminiApiService = retrofit.create(GeminiApiService::class.java)

    /**
     * Generates a dynamic chat response from Gemini 3.5 Flash.
     * Includes a system instruction to instruct the AI with the contact's personality, bio, and tone.
     * Gracefully falls back to beautiful pre-written simulation replies if the API Key is unconfigured
     * or a network failure occurs.
     */
    suspend fun generateChatReply(
        contactName: String,
        personalityPrompt: String,
        history: List<Pair<String, String>>, // SenderId to Text
        userPrompt: String
    ): String {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read GEMINI_API_KEY from BuildConfig: ${e.message}")
            ""
        }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY" || apiKey.contains("PLACEHOLDER")) {
            Log.w(TAG, "Gemini API key is unconfigured. Falling back to offline simulator engine.")
            return getSimulatedFallback(contactName, userPrompt)
        }

        try {
            // Build conversation content
            // We map the last 10 exchanges for context window safety
            val contents = mutableListOf<GeminiContent>()
            history.takeLast(10).forEach { item ->
                val textWithRole = if (item.first == "user") {
                    "User: ${item.second}"
                } else {
                    "$contactName: ${item.second}"
                }
                contents.add(GeminiContent(parts = listOf(GeminiPart(text = textWithRole))))
            }
            // Add the latest user prompt
            contents.add(GeminiContent(parts = listOf(GeminiPart(text = "User: $userPrompt"))))

            val systemInstruction = GeminiContent(parts = listOf(
                GeminiPart(text = "$personalityPrompt. " +
                        "Keep your responses friendly, concise, natural (typically 1-3 sentences) suited for an instant messaging chat thread. " +
                        "Do not include markdowns, bold prefixes or structural JSON unless asked. " +
                        "Act completely as $contactName.")
            ))

            val request = GeminiRequest(
                contents = contents,
                systemInstruction = systemInstruction
            )

            val response = service.generateContent(apiKey, request)
            val replyText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!replyText.isNullOrBlank()) {
                return replyText.trim()
            }
            return getSimulatedFallback(contactName, userPrompt)
        } catch (e: Exception) {
            Log.e(TAG, "Gemini API call failed, falling back to simulated output.", e)
            return getSimulatedFallback(contactName, userPrompt)
        }
    }

    private fun getSimulatedFallback(contactName: String, prompt: String): String {
        val lower = prompt.lowercase()
        return when (contactName) {
            "Gemini AI" -> {
                when {
                    lower.contains("hello") || lower.contains("hi") || lower.contains("hey") ->
                        "Hey there! I am Gemini AI, your companion in this social sandbox. What's on your mind today?"
                    lower.contains("game") || lower.contains("play") ->
                        "I'm always down to play! Head over to the Games Hub tab. We can play Tic-Tac-Toe together, or you can test your memory with Card Match! Want me to start a game?"
                    lower.contains("call") ->
                        "Sure! Send me a calling request by tapping the Audio or Video icon in the top bar, and we'll connect instantly."
                    lower.contains("snap") || lower.contains("photo") || lower.contains("pic") ->
                        "Let's trade! You can send me a drawing or mock snap using the media button (+ icon), and I'll cherish it!"
                    else -> "That sounds fascinating! Even in offline/simulated mode, I'm here to support you. We can chat about coding, games, calling, or anything else you'd like!"
                }
            }
            "Alice (Gamer)" -> {
                when {
                    lower.contains("hello") || lower.contains("hi") || lower.contains("hey") ->
                        "GG! Alice here, ready to squad up! 😎 Up for some gaming chat, or are we hopping into Tic-Tac-Toe?"
                    lower.contains("game") || lower.contains("play") ->
                        "Oh, you want to game? Tic-Tac-Toe in this app is super sleek! challenge me in the Games Hub! I won't go easy on you! 👾"
                    lower.contains("video") || lower.contains("call") ->
                        "Let's hop on a call and coordinate! Tap the call button so we can discuss our strategy."
                    lower.contains("snap") || lower.contains("photo") ->
                        "Check out my new gaming rig setup! Just send me a snap of yours too, love to see it!"
                    else -> "Woah, interesting play! Let's conquer the leaderboard together! Chat with me anytime or let's start a game."
                }
            }
            "Bob (Snap Enthusiast)" -> {
                when {
                    lower.contains("hello") || lower.contains("hi") || lower.contains("hey") ->
                        "Hey! What's up? Shutter speed is set, let's trade some awesome snaps today! 📸"
                    lower.contains("snap") || lower.contains("photo") || lower.contains("pic") ->
                        "Snaps are my absolute favorite! Try drawing a custom masterpiece on the canvas (tap + -> Snap Canvas) and send it. I'll review your artistic exposure!"
                    lower.contains("call") ->
                        "I love video calling because we can show off our surroundings in real-time! Give me a call right now!"
                    lower.contains("game") || lower.contains("play") ->
                        "I play casual games when I'm waiting for golden hour! Tic-Tac-Toe or Memory Match is great for quick reflexes."
                    else -> "Nice perspective! Send me some cool visual concepts in the chat!"
                }
            }
            "Charlie (Fitness Coach)" -> {
                when {
                    lower.contains("hello") || lower.contains("hi") || lower.contains("hey") ->
                        "Hey champion! Ready to stay active today? Let's keep those fitness streaks high! 🏃‍♂️💨"
                    lower.contains("game") || lower.contains("play") ->
                        "A sharp mind goes with a fit body! Card Memory Match is absolute fire for checking cognitive coordination. Let's do a round!"
                    lower.contains("call") ->
                        "Let's do an audio call to review your wellness metrics and routine! Ring me up!"
                    lower.contains("snap") || lower.contains("photo") ->
                        "Send me a snap showing your morning shake or training environment! Visual tracking keeps us accountable."
                    else -> "Keep putting in the work! Positive vibes only. What's your goal for the rest of today?"
                }
            }
            else -> "Hey there! Always awesome connecting with you. Let's keep chatting, making calls, or playing awesome games!"
        }
    }
}
