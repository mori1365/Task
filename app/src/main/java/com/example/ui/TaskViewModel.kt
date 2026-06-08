package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.Task
import com.example.data.TaskRepository
import com.example.data.TaskSyncService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TaskViewModel(private val repository: TaskRepository) : ViewModel() {

    enum class FilterStatus { ALL, PENDING, COMPLETED }

    private val _connectedTeamId = MutableStateFlow<String?>(null)
    val connectedTeamId = _connectedTeamId.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    fun setConnectedTeamId(teamId: String?) {
        _connectedTeamId.value = teamId
    }

    private val _filterStatus = MutableStateFlow(FilterStatus.ALL)
    val filterStatus = _filterStatus.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null) // null means all
    val selectedCategory = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    // Base tasks stream from Room DB
    val tasks: StateFlow<List<Task>> = repository.allTasks
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Reactive composition of tasks with search terms, categories, and status filters
    val filteredTasks: StateFlow<List<Task>> = combine(
        tasks,
        _filterStatus,
        _selectedCategory,
        _searchQuery
    ) { allTasks, status, category, query ->
        allTasks.filter { task ->
            val matchesStatus = when (status) {
                FilterStatus.ALL -> true
                FilterStatus.PENDING -> !task.isCompleted
                FilterStatus.COMPLETED -> task.isCompleted
            }
            val matchesCategory = if (category == null) true else task.category == category
            val matchesSearch = if (query.isEmpty()) true else {
                task.title.contains(query, ignoreCase = true) ||
                task.description.contains(query, ignoreCase = true)
            }
            matchesStatus && matchesCategory && matchesSearch
        }.sortedWith(
            compareBy<Task> { it.isCompleted }
                .thenByDescending {
                    when (it.priority.lowercase()) {
                        "high" -> 3
                        "medium" -> 2
                        else -> 1
                    }
                }
                .thenBy { it.dueDate ?: Long.MAX_VALUE }
                .thenByDescending { it.createdAt }
        )
    }
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun setFilterStatus(status: FilterStatus) {
        _filterStatus.value = status
    }

    fun setSelectedCategory(category: String?) {
        _selectedCategory.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun addTask(title: String, description: String, category: String, priority: String, dueDate: Long? = null) {
        viewModelScope.launch {
            if (title.isNotBlank()) {
                val newTask = Task(
                    title = title.trim(),
                    description = description.trim(),
                    category = category,
                    priority = priority,
                    isCompleted = false,
                    dueDate = dueDate
                )
                repository.insertTask(newTask)
                if (!_connectedTeamId.value.isNullOrBlank()) {
                    syncOnline { _, _ -> }
                }
            }
        }
    }

    fun toggleTaskCompletion(task: Task) {
        viewModelScope.launch {
            val updatedTask = task.copy(isCompleted = !task.isCompleted)
            repository.updateTask(updatedTask)
            if (!_connectedTeamId.value.isNullOrBlank()) {
                syncOnline { _, _ -> }
            }
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            repository.deleteTask(task)
            if (!_connectedTeamId.value.isNullOrBlank()) {
                syncOnline { _, _ -> }
            }
        }
    }

    fun clearAllTasks() {
        viewModelScope.launch {
            repository.clearAllTasks()
            if (!_connectedTeamId.value.isNullOrBlank()) {
                syncOnline { _, _ -> }
            }
        }
    }

    fun importSharedTasks(tasksToImport: List<Task>) {
        viewModelScope.launch {
            tasksToImport.forEach { task ->
                val exists = tasks.value.any {
                    it.title.equals(task.title, ignoreCase = true) &&
                    it.category.equals(task.category, ignoreCase = true)
                }
                if (!exists) {
                    val taskToInsert = task.copy(id = 0) // Reset ID of task to autogenerate on local SQLite
                    repository.insertTask(taskToInsert)
                }
            }
        }
    }

    fun createTeamOnline(onResult: (String?, String?) -> Unit) {
        viewModelScope.launch {
            _isSyncing.value = true
            val result = TaskSyncService.createOnlineTeam()
            _isSyncing.value = false
            result.fold(
                onSuccess = { newTeamId ->
                    _connectedTeamId.value = newTeamId
                    // Trigger immediate initial upload/sync of existing local tasks to the cloud!
                    syncOnline { success, error ->
                        onResult(newTeamId, error)
                    }
                },
                onFailure = { error ->
                    onResult(null, error.message ?: "Unknown error")
                }
            )
        }
    }

    fun syncOnline(onResult: (Boolean, String?) -> Unit) {
        val teamId = _connectedTeamId.value
        if (teamId.isNullOrBlank()) {
            onResult(false, "No active connected team")
            return
        }
        viewModelScope.launch {
            _isSyncing.value = true
            val result = TaskSyncService.syncTwoWay(teamId, tasks.value)
            _isSyncing.value = false
            result.fold(
                onSuccess = { mergedList ->
                    replaceAndSyncAllTasks(mergedList)
                    onResult(true, null)
                },
                onFailure = { error ->
                    onResult(false, error.message ?: "Unknown error")
                }
            )
        }
    }

    private suspend fun replaceAndSyncAllTasks(syncedTasks: List<Task>) {
        repository.clearAllTasks()
        syncedTasks.forEach { task ->
            repository.insertTask(task.copy(id = 0))
        }
    }

    class Factory(private val repository: TaskRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TaskViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return TaskViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
