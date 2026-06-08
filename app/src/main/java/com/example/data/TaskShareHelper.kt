package com.example.data

import android.util.Base64
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object TaskShareHelper {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val taskListType = Types.newParameterizedType(List::class.java, Task::class.java)
    private val jsonAdapter = moshi.adapter<List<Task>>(taskListType)

    /**
     * Converts a list of Tasks into a URL-safe Base64 encoded transfer string.
     */
    fun exportTasksToCode(tasks: List<Task>): String {
        return try {
            val json = jsonAdapter.toJson(tasks)
            Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.NO_WRAP or Base64.URL_SAFE)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    /**
     * Parses an encoded transfer string or raw JSON string back into a list of Tasks.
     */
    fun importTasksFromCode(code: String): List<Task>? {
        if (code.isBlank()) return null
        val trimmed = code.trim()
        
        // 1. Try decoding as Base64 first
        try {
            val decodedBytes = Base64.decode(trimmed, Base64.NO_WRAP or Base64.URL_SAFE)
            val json = String(decodedBytes, Charsets.UTF_8)
            val result = jsonAdapter.fromJson(json)
            if (result != null) return result
        } catch (e: Exception) {
            // Fall back or proceed
        }

        // 2. Try parsing directly as raw JSON in case they copy-pasted raw JSON
        try {
            val result = jsonAdapter.fromJson(trimmed)
            if (result != null) return result
        } catch (e: Exception) {
            // Fail
        }

        // 3. Try parsing with standard Base64 default flags if URL_SAFE failed
        try {
            val decodedBytes = Base64.decode(trimmed, Base64.DEFAULT)
            val json = String(decodedBytes, Charsets.UTF_8)
            val result = jsonAdapter.fromJson(json)
            if (result != null) return result
        } catch (e: Exception) {
            // Fail
        }

        return null
    }
}
