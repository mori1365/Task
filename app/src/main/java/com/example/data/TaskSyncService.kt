package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object TaskSyncService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private const val BASE_URL = "https://kvdb.io"

    /**
     * Creates a new unique bucket on KVdb and returns the bucket ID.
     */
    suspend fun createOnlineTeam(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(BASE_URL)
                .post("".toRequestBody("text/plain".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()?.trim()
                    if (!body.isNullOrEmpty()) {
                        Result.success(body)
                    } else {
                        Result.failure(Exception("Empty response from server"))
                    }
                } else {
                    Result.failure(Exception("Server returned error code: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Performs a professional Two-Way Sync between local tasks and remote tasks.
     * 1. Fetches remote tasks from KVdb.
     * 2. Merges local and remote tasks (deduplicating by title + category).
     * 3. Pushes the merged list back up to KVdb.
     * 4. Returns the merged list of tasks to update Room.
     */
    suspend fun syncTwoWay(bucketId: String, localTasks: List<Task>): Result<List<Task>> = withContext(Dispatchers.IO) {
        try {
            val fetchRequest = Request.Builder()
                .url("$BASE_URL/buckets/$bucketId/keys/tasks")
                .get()
                .build()

            var remoteTasks: List<Task> = emptyList()

            client.newCall(fetchRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val parsed = TaskShareHelper.importTasksFromCode(body)
                    if (parsed != null) {
                        remoteTasks = parsed
                    }
                } else if (response.code != 404) {
                    // 404 is fine (it just means the key doesn't exist yet / new workspace)
                    return@withContext Result.failure(Exception("Failed to fetch cloud tasks: ${response.code}"))
                }
            }

            // MERGE STRATEGY: Combine both lists
            // Map task signature to task
            val mergedMap = mutableMapOf<String, Task>()

            // First add all local tasks
            for (task in localTasks) {
                val key = getTaskSignature(task)
                mergedMap[key] = task
            }

            // Then merge remote tasks
            for (remoteTask in remoteTasks) {
                val key = getTaskSignature(remoteTask)
                val existingLocal = mergedMap[key]
                if (existingLocal == null) {
                    // New task from teammate, add it (id reset to 0 to generate new primary key on save)
                    mergedMap[key] = remoteTask.copy(id = 0)
                } else {
                    // Conflict resolution: if either teammate or local marked it completed, keep it completed
                    val isCompleted = existingLocal.isCompleted || remoteTask.isCompleted
                    val mergedTask = existingLocal.copy(
                        description = if (existingLocal.description.length >= remoteTask.description.length) existingLocal.description else remoteTask.description,
                        priority = if (getPriorityRank(existingLocal.priority) >= getPriorityRank(remoteTask.priority)) existingLocal.priority else remoteTask.priority,
                        isCompleted = isCompleted,
                        dueDate = existingLocal.dueDate ?: remoteTask.dueDate
                    )
                    mergedMap[key] = mergedTask
                }
            }

            val finalMergedList = mergedMap.values.toList()

            // Push final merged list back to cloud for other teammates to pull
            val jsonToUpload = TaskShareHelper.serializeToJson(finalMergedList)
            val uploadRequest = Request.Builder()
                .url("$BASE_URL/buckets/$bucketId/keys/tasks")
                .put(jsonToUpload.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(uploadRequest).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(finalMergedList)
                } else {
                    Result.failure(Exception("Failed to push merged tasks online Error ${response.code}"))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private fun getTaskSignature(task: Task): String {
        return "${task.title.trim().lowercase()}###${task.category.trim().lowercase()}"
    }

    private fun getPriorityRank(priority: String): Int {
        return when (priority.lowercase()) {
            "high" -> 3
            "medium" -> 2
            else -> 1
        }
    }
}
