package com.example.data.groq

import android.util.Log
import com.example.data.model.NoteMode
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GroqNoteGenerationService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun generateNotes(
        apiKey: String,
        transcript: String,
        mode: NoteMode,
        model: String = "llama-3.3-70b-versatile",
        customInstructions: String = ""
    ): Result<String> = runCatching {
        if (apiKey.isBlank()) {
            throw IllegalArgumentException(
                "Groq API Key is not configured. Please open Settings and enter your Groq API key."
            )
        }

        if (transcript.isBlank()) {
            throw IllegalArgumentException("Cannot generate notes from an empty transcript.")
        }

        val systemPrompt = buildSystemPrompt(mode, customInstructions)
        val userPrompt = "Here is the verified audio transcript:\n\n\"\"\"\n$transcript\n\"\"\"\n\nPlease transform this into high-quality, structured notes following the system instructions."

        val messagesArray = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", userPrompt)
            })
        }

        val requestJson = JSONObject().apply {
            put("model", model.ifBlank { "llama-3.3-70b-versatile" })
            put("messages", messagesArray)
            put("temperature", 0.3)
            put("max_tokens", 4096)
        }

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val errorMsg = try {
                    JSONObject(responseBody).optJSONObject("error")?.optString("message") ?: response.message
                } catch (_: Exception) {
                    response.message
                }
                when (response.code) {
                    401 -> throw Exception("Invalid Groq API key. Check your key in Settings.")
                    429 -> throw Exception("Groq rate limit reached. Please wait a moment and try again.")
                    else -> throw Exception("Groq note generation error (${response.code}): $errorMsg")
                }
            }

            val json = JSONObject(responseBody)
            val choices = json.optJSONArray("choices")
            if (choices == null || choices.length() == 0) {
                throw Exception("Groq returned an empty note response.")
            }
            val content = choices.getJSONObject(0)
                .optJSONObject("message")
                ?.optString("content", "")
                ?.trim() ?: ""

            if (content.isEmpty()) {
                throw Exception("Groq generated empty note content.")
            }
            content
        }
    }

    private fun buildSystemPrompt(mode: NoteMode, customInstructions: String): String {
        val baseRules = """
You are an expert academic and professional note-taking assistant.
CRITICAL INTEGRITY & ACCURACY RULES:
1. Strict Factual Accuracy: Never invent, extrapolate, or hallucinate information that was not in the transcript.
2. If any segment of the transcript is unclear, mumbled, or ambiguous, explicitly tag it as [Ambiguous / Unclear in audio] instead of guessing.
3. Remove filler words, conversational chitchat, repetitions, and false starts while strictly retaining all technical terms, definitions, formulas, names, metrics, dates, and examples.
4. Output cleanly formatted Markdown with clear titles (#), section headings (##), subheadings (###), and bullet points.
5. Do NOT output conversational preamble (e.g. "Sure, here are your notes:"). Start directly with the title.
""".trimIndent()

        val modeGuidance = when (mode) {
            NoteMode.DETAILED -> """
FORMAT MODE: DETAILED COMPREHENSIVE NOTES
- Create a clear main Title (#)
- Executive Summary (2-3 sentences overview)
- Detailed Structured Sections (##) organized logically by topic
- Key Concept Definitions & Explanations (highlighting bold terms)
- Concrete Examples, Formulas, or Data mentioned in transcript
- Action Items & Key Takeaways section (## Action Items)
"""
            NoteMode.REVISION -> """
FORMAT MODE: SHORT REVISION NOTES
- High-yield summary designed for quick 2-minute review
- Top 5 core takeaways at the top
- Ultra-concise bulleted breakdown of essentials
- Quick reference definitions table or list
"""
            NoteMode.BULLETS -> """
FORMAT MODE: BULLET-POINT SUMMARY
- Clean, nested bulleted hierarchy
- Grouped into relevant thematic topics
- Direct, scannable, punchy statements
"""
            NoteMode.EXAM -> """
FORMAT MODE: EXAM PREPARATION NOTES
- High-Priority Exam Topics (# Exam Focus)
- Key Definitions & Formulas to Memorize
- Practice Questions & Answers derived directly from the content
- Common Pitfalls or Distinctions mentioned in audio
"""
            NoteMode.MEETING -> """
FORMAT MODE: MEETING MINUTES & ACTION ITEMS
- Meeting Objective & Context
- Key Decisions Reached
- Detailed Discussion Highlights by Topic
- Action Items Table/List with [Owner], [Task], and [Deadline] if mentioned in transcript
"""
            NoteMode.CUSTOM -> """
FORMAT MODE: CUSTOM INSTRUCTIONS
Adhere strictly to the user's customized formatting and emphasis preferences:
${customInstructions.ifBlank { "Structure into clear headings and actionable bullet points." }}
"""
        }

        return "$baseRules\n\n$modeGuidance"
    }
}
