package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.app.DatePickerDialog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.widget.Toast
import com.example.data.TaskShareHelper
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Task
import com.example.data.TaskDatabase
import com.example.data.TaskRepository
import com.example.ui.TaskViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Local Room construction
        val database = TaskDatabase.getDatabase(this)
        val repository = TaskRepository(database.taskDao())

        val sharedPrefs = getSharedPreferences("TaskFlowPrefs", MODE_PRIVATE)
        val initialTeamId = sharedPrefs.getString("connected_team_id", null)

        setContent {
            MyApplicationTheme {
                val viewModel: TaskViewModel by viewModels {
                    TaskViewModel.Factory(repository)
                }

                LaunchedEffect(Unit) {
                    if (initialTeamId != null) {
                        viewModel.setConnectedTeamId(initialTeamId)
                        viewModel.syncOnline { _, _ -> }
                    }
                }

                TaskAppScreen(viewModel)
            }
        }
    }
}

// Custom category data model with bilingual support and coloring
data class CategoryItem(
    val id: String,
    val nameEn: String,
    val nameFa: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color
)

val CategoriesList = listOf(
    CategoryItem("General", "General", "عمومی", Icons.AutoMirrored.Filled.List, Color(0xFF64748B)),
    CategoryItem("Personal", "Personal", "شخصی", Icons.Default.Person, Color(0xFF3B82F6)),
    CategoryItem("Work", "Work", "کاری", Icons.Default.Work, Color(0xFFAC5CF6)),
    CategoryItem("Shopping", "Shopping", "خرید", Icons.Default.ShoppingCart, Color(0xFFF59E0B)),
    CategoryItem("Health", "Wellness", "سلامتی", Icons.Default.Favorite, Color(0xFF10B981))
)

// Simple relative date formatter
fun getRelativeTime(timestamp: Long, isFarsi: Boolean): String {
    val diff = System.currentTimeMillis() - timestamp
    if (diff < 60000) {
        return if (isFarsi) "همین الان" else "Just now"
    }
    val minutes = diff / 60000
    if (minutes < 60) {
        return if (isFarsi) "$minutes دقیقه پیش" else "${minutes}m ago"
    }
    val hours = minutes / 60
    if (hours < 24) {
        return if (isFarsi) "$hours ساعت پیش" else "${hours}h ago"
    }
    val days = hours / 24
    return if (isFarsi) "$days روز پیش" else "${days}d ago"
}

fun formatDueDate(timestamp: Long, isFarsi: Boolean): String {
    val sdf = java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.US)
    val dateStr = sdf.format(java.util.Date(timestamp))
    return if (isFarsi) "تا $dateStr" else "Due: $dateStr"
}

@Composable
fun TaskAppScreen(viewModel: TaskViewModel) {
    // Dynamic Persian/English bilingual toggling
    var isFarsi by remember { mutableStateOf(true) }

    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val filteredTasks by viewModel.filteredTasks.collectAsStateWithLifecycle()
    val filterStatus by viewModel.filterStatus.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val connectedTeamId by viewModel.connectedTeamId.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var showCollabDialog by remember { mutableStateOf(false) }

    // Dynamic layout direction mapping
    val layoutDirection = if (isFarsi) LayoutDirection.Rtl else LayoutDirection.Ltr

    // Task stat calculations
    val totalCount = tasks.size
    val completedCount = tasks.count { it.isCompleted }
    val completionRatio = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f
    val animateProgress by animateFloatAsState(targetValue = completionRatio, label = "progress")

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        Scaffold(
            modifier = Modifier.fillMaxSize().testTag("app_scaffold"),
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(8.dp).testTag("add_task_fab")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = if (isFarsi) "افزودن کار جدید" else "Add New Task",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(innerPadding)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    // UPPER APP BAR & LANGUAGE TOGGLER
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isFarsi) "تسک‌های من" else "My Tasks",
                                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF21005D) // Vibrant Palette Royal Purple Heading
                                )
                                if (connectedTeamId != null) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFFE8F5E9))
                                            .border(1.dp, Color(0xFF81C784), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF4CAF50))
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = connectedTeamId?.take(6) ?: "",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = Color(0xFF2E7D32)
                                            )
                                        }
                                    }
                                }
                            }
                            Text(
                                text = if (isFarsi) "امروز، ۱۸ خرداد" else "Today, June 8",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Persian / English Lang & Collaboration Toggle Custom Badges
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Colleague Connection & Task Export/Import Button
                            IconButton(
                                onClick = { showCollabDialog = true },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .size(44.dp)
                                    .testTag("collab_dialog_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Groups,
                                    contentDescription = if (isFarsi) "همکاری با همکاران" else "Colleague Connection",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Persian / English Lang Toggle Badge
                            IconButton(
                                onClick = { isFarsi = !isFarsi },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .size(44.dp)
                                    .testTag("lang_toggle_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = "تغییر زبان - Translate App",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    // HERO PROGRESS DASHBOARD CARD
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isFarsi) "میزان پیشرفت شما" else "Your Daily Progress",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isFarsi) {
                                        "تاکنون $completedCount کار از کل $totalCount کار انجام شده است"
                                    } else {
                                        "Completed $completedCount out of $totalCount tasks"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                // Motivational localized text
                                Text(
                                    text = if (totalCount == 0) {
                                        if (isFarsi) "هنوز دست‌بکاری نشده‌اید!" else "No tasks added yet!"
                                    } else if (completionRatio == 1f) {
                                        if (isFarsi) "فوق‌العاده است! همه کارها انجام شد! 🎉" else "Perfect! All tasks completed! 🎉"
                                    } else {
                                        if (isFarsi) "آفرین، همین‌طوری ادامه بدید! 💪" else "Keep pushing, you can do it! 💪"
                                    },
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            // Interactive circular indicator
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .padding(start = 12.dp)
                                    .size(72.dp)
                            ) {
                                CircularProgressIndicator(
                                    progress = { animateProgress },
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
                                    strokeWidth = 6.dp,
                                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                                Text(
                                    text = "${(animateProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // LIVE SEARCH BAR
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("task_search_input"),
                        placeholder = {
                            Text(
                                text = if (isFarsi) "جستجوی کارها..." else "Search tasks...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = if (isFarsi) "پاک کردن" else "Clear key"
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // STATUS FILTER SEGMENTED CONTROLS
                    TabRow(
                        selectedTabIndex = when (filterStatus) {
                            TaskViewModel.FilterStatus.ALL -> 0
                            TaskViewModel.FilterStatus.PENDING -> 1
                            TaskViewModel.FilterStatus.COMPLETED -> 2
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                        containerColor = MaterialTheme.colorScheme.surface,
                        indicator = { tabPositions ->
                            if (tabPositions.isNotEmpty()) {
                                TabRowDefaults.SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[when (filterStatus) {
                                        TaskViewModel.FilterStatus.ALL -> 0
                                        TaskViewModel.FilterStatus.PENDING -> 1
                                        TaskViewModel.FilterStatus.COMPLETED -> 2
                                    }]),
                                    color = MaterialTheme.colorScheme.primary,
                                    height = 3.dp
                                )
                            }
                        }
                    ) {
                        Tab(
                            selected = filterStatus == TaskViewModel.FilterStatus.ALL,
                            onClick = { viewModel.setFilterStatus(TaskViewModel.FilterStatus.ALL) },
                            text = { Text(if (isFarsi) "???" else "All") }
                        )
                        Tab(
                            selected = filterStatus == TaskViewModel.FilterStatus.PENDING,
                            onClick = { viewModel.setFilterStatus(TaskViewModel.FilterStatus.PENDING) },
                            text = { Text(if (isFarsi) "?? ?????" else "Pending") }
                        )
                        Tab(
                            selected = filterStatus == TaskViewModel.FilterStatus.COMPLETED,
                            onClick = { viewModel.setFilterStatus(TaskViewModel.FilterStatus.COMPLETED) },
                            text = { Text(if (isFarsi) "????? ???" else "Completed") }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // CATEGORY FILTER HORIZONTAL ROW
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // "All" Category Chip
                        item {
                            val isSelected = selectedCategory == null
                            val containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(containerColor)
                                    .clickable { viewModel.setSelectedCategory(null) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = if (isFarsi) "???" else "All",
                                    color = textColor,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }

                        items(CategoriesList) { cat ->
                            val isSelected = selectedCategory == cat.id
                            val containerColor = if (isSelected) cat.color else cat.color.copy(alpha = 0.08f)
                            val textColor = if (isSelected) Color.White else cat.color

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(containerColor)
                                    .clickable { viewModel.setSelectedCategory(cat.id) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = cat.icon,
                                        contentDescription = null,
                                        tint = textColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isFarsi) cat.nameFa else cat.nameEn,
                                        color = textColor,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // TASKS LIST LAZYCOLUMN
                    if (filteredTasks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(80.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.EventNote,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = if (isFarsi) "??? ???? ???? ???" else "No Tasks Found",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (isFarsi) "??? ????? ????? ???? ?? ??????? ?? ????? ????." else "Add a task or adjust filters to get started.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            items(
                                items = filteredTasks,
                                key = { it.id }
                            ) { task ->
                                TaskItemCard(
                                    task = task,
                                    isFarsi = isFarsi,
                                    onToggle = { viewModel.toggleTaskCompletion(task) },
                                    onDelete = { viewModel.deleteTask(task) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TaskItemCard(
    task: Task,
    isFarsi: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val cardBg = if (task.isCompleted) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    
    val titleColor = if (task.isCompleted) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    
    val descColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task_item_${task.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (task.isCompleted) {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (task.isCompleted) 0.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(
                        if (task.isCompleted) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Transparent
                        }
                    )
                    .border(
                        width = 2.dp,
                        color = if (task.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = CircleShape
                    )
                    .clickable { onToggle() },
                contentAlignment = Alignment.Center
            ) {
                if (task.isCompleted) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Completed",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            textDecoration = if (task.isCompleted) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                        ),
                        color = titleColor,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    
                    val priorityColor = when (task.priority.lowercase()) {
                        "high" -> Color(0xFFEF4444)
                        "medium" -> Color(0xFFF59E0B)
                        else -> Color(0xFF10B981)
                    }
                    val priorityName = when (task.priority.lowercase()) {
                        "high" -> if (isFarsi) "????" else "Urgent"
                        "medium" -> if (isFarsi) "?????" else "Medium"
                        else -> if (isFarsi) "??" else "Low"
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(priorityColor.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = priorityName,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = priorityColor
                        )
                    }
                }
                
                if (task.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = descColor,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                
                if (task.dueDate != null || task.createdAt > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (task.dueDate != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Event,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = formatDueDate(task.dueDate!!, isFarsi),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                )
                            }
                        }
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = getRelativeTime(task.createdAt, isFarsi),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = if (isFarsi) "??? ???" else "Delete Task",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun TaskCollabDialog(
    isFarsi: Boolean,
    viewModel: com.example.ui.TaskViewModel,
    onDismiss: () -> Unit
) {
    var selectedCat by remember { mutableStateOf<String?>(null) } // null means All
    var codeToImport by remember { mutableStateOf("") }
    var joinTeamInput by remember { mutableStateOf("") }
    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val sharedPrefs = remember { context.getSharedPreferences("TaskFlowPrefs", android.content.Context.MODE_PRIVATE) }

    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val connectedTeamId by viewModel.connectedTeamId.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()

    val tasksToShare = if (selectedCat == null) {
        tasks
    } else {
        tasks.filter { it.category == selectedCat }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(androidx.compose.foundation.rememberScrollState())
                    .padding(20.dp)
            ) {
                // Header Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Groups,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (isFarsi) "همکاری و اشتراک تیمی" else "Team Collaboration",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF21005D)
                        )
                        Text(
                            text = if (isFarsi) "کارهای خود را به همکارانتان منتقل و همگام کنید" else "Sync actions directly with colleagues",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Thin Divider line
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)).padding(vertical = 4.dp))
                Spacer(modifier = Modifier.height(12.dp))

                // ==========================================
                // SECTION 1: AUTOMATIC ONLINE CLOUD SYNC
                // ==========================================
                Text(
                    text = if (isFarsi) "☁️ اتصال و همگام‌سازی ابری (اتوماتیک)" else "☁️ Cloud Auto Sync (Instant)",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isFarsi) "بدون نیاز به کپی کردن کدهای طولانی، عضو یک گروه تیمی آنلاین شوید تا تسک‌ها خودکار در پس‌زمینه همسان و همگام شوند." 
                           else "Join an instant cloud workspace. Local changes will automatically merge and sync in the background.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(10.dp))

                if (connectedTeamId != null) {
                    // Connected Status UI
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF81C784))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF4CAF50))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isFarsi) "وضعیت: متصل به سرور" else "Status: Fully Connected",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFF2E7D32)
                                    )
                                }

                                TextButton(
                                    onClick = {
                                        sharedPrefs.edit().remove("connected_team_id").apply()
                                        viewModel.setConnectedTeamId(null)
                                        Toast.makeText(
                                            context,
                                            if (isFarsi) "اتصال ابری قطع شد. برنامه در حالت محلی کار می‌کند." else "Cloud disconnected. Using offline mode.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudOff,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isFarsi) "خروج از گروه" else "Disconnect",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.7f))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = if (isFarsi) "کد همگام‌سازی ابری تیم شما:" else "Your Team Cloud Code:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF33691E)
                                    )
                                    Text(
                                        text = connectedTeamId ?: "",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFF1B5E20)
                                    )
                                }

                                Row {
                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(connectedTeamId ?: ""))
                                            Toast.makeText(
                                                context,
                                                if (isFarsi) "کد کپی شد! برای اتصال همکاران بفرستید" else "Code copied! Send to teammates",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(Color.White)
                                            .size(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy code",
                                            tint = Color(0xFF2E7D32),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = {
                                            val shareText = if (isFarsi) {
                                                "هم‌تیمی عزیز! برای عضویت در گروه همگام‌سازی ابری کارهای تیمی ما، این کد را در بخش همکاری اتوماتیک برنامه چسبانده و متصل شوید:\n\n$connectedTeamId"
                                            } else {
                                                "Teammate! Connect to my real-time cloud workspace with this code:\n\n$connectedTeamId"
                                            }
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, shareText)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "Share via"))
                                        },
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(Color.White)
                                            .size(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Share",
                                            tint = Color(0xFF2E7D32),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = {
                                    viewModel.syncOnline { success, error ->
                                        if (success) {
                                            Toast.makeText(
                                                context,
                                                if (isFarsi) "لیست‌ها از فضای ابری بازخوانی و همسان‌سازی شد! 🎉" else "Lists fully cloud reconciled! 🎉",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                if (isFarsi) "خطا در همگام‌سازی: $error" else "Sync failed: $error",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20)),
                                enabled = !isSyncing
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Icon(imageVector = Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isFarsi) "بروزرسانی تیمی ابری دو طرفه" else "Sync Cloud Now (Two-Way)")
                                }
                            }
                        }
                    }
                } else {
                    // Disconnected / Offline state view
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isFarsi) "وضعیت: محلی / آفلاین" else "Status: Local Offline mode",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = {
                                    viewModel.createTeamOnline { newTeamId, error ->
                                        if (newTeamId != null) {
                                            sharedPrefs.edit().putString("connected_team_id", newTeamId).apply()
                                            Toast.makeText(
                                                context,
                                                if (isFarsi) "کد ابری جدید تیمی شما با موفقیت ساخته شد! 🎉" else "New cloud workspace code created! 🎉",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                if (isFarsi) "خطا در ایجاد گروه ابری: $error" else "Error creating cloud workspace: $error",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                enabled = !isSyncing
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Icon(imageVector = Icons.Default.CloudQueue, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isFarsi) "ایجاد و ساخت گروه آنلاین جدید" else "Create New Online Group")
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)))
                                Text(
                                    text = if (isFarsi) "اتصال به گروه همکار" else "JOIN COLLEAGUE'S GROUP",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                                Box(modifier = Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)))
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = joinTeamInput,
                                onValueChange = { joinTeamInput = it },
                                placeholder = { Text(if (isFarsi) "کد دریافتی از همکار را پیست کنید" else "Paste teammate's sync code...") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    if (joinTeamInput.isBlank()) {
                                        Toast.makeText(context, if (isFarsi) "کد نمی‌تواند خالی باشد!" else "Code cannot be empty!", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    val cleanInput = joinTeamInput.trim()
                                    viewModel.setConnectedTeamId(cleanInput)
                                    viewModel.syncOnline { success, error ->
                                        if (success) {
                                            sharedPrefs.edit().putString("connected_team_id", cleanInput).apply()
                                            Toast.makeText(
                                                context,
                                                if (isFarsi) "با موفقیت متصل و همگام شدید! 🎉" else "Connected and synchronized! 🎉",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            viewModel.setConnectedTeamId(null)
                                            Toast.makeText(
                                                context,
                                                if (isFarsi) "خطا در اتصال: $error" else "Connection failed: $error",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                enabled = !isSyncing
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Icon(imageVector = Icons.Default.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isFarsi) "اتصال و ادغام تسک‌ها با همکار" else "Join & Merge Teammate Sync")
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)))
                    Text(
                        text = if (isFarsi) "روش‌های جایگزین / دستی (کد محور)" else "Manual Export / Import (Fallback)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Box(modifier = Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)))
                }
                Spacer(modifier = Modifier.height(16.dp))

                // SECTION 2: MANUAL EXPORT / SHARE
                Text(
                    text = if (isFarsi) "۱. تولید و کپی دستی فایل کارهای تیم" else "1. Export Team Workspace",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isFarsi) "در صورت عدم دسترسی به سرور، می‌توانید کارها را به متون کدی تبدیل کرده و چت بفرستید:" else "Alternatively, convert specific workspaces to clean transfer codes to pass offline or via messengers:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Workspace horizontal chip row
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        val isSelected = selectedCat == null
                        val containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        val textColor = if (isSelected) Color.White else MaterialTheme.colorScheme.primary
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(containerColor)
                                .clickable { selectedCat = null }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = if (isFarsi) "همه تیم‌ها" else "All Teams",
                                color = textColor,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    items(CategoriesList) { cat ->
                        val isSelected = selectedCat == cat.id
                        val containerColor = if (isSelected) cat.color else cat.color.copy(alpha = 0.08f)
                        val textColor = if (isSelected) Color.White else cat.color

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(containerColor)
                                .clickable { selectedCat = cat.id }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = cat.icon,
                                    contentDescription = null,
                                    tint = textColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isFarsi) cat.nameFa else cat.nameEn,
                                    color = textColor,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isFarsi) {
                                    "تعداد کارهای آماده انتقال: ${tasksToShare.size} مورد"
                                } else {
                                    "Tasks ready to transfer: ${tasksToShare.size}"
                                },
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (tasksToShare.isEmpty()) {
                            Toast.makeText(
                                context,
                                if (isFarsi) "تسکی برای اشتراک‌گذاری در این بخش نیست!" else "No tasks in this segment to share!",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@Button
                        }

                        val encodedCode = TaskShareHelper.exportTasksToCode(tasksToShare)
                        if (encodedCode.isEmpty()) {
                            Toast.makeText(context, "Error generating code", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val titleText = if (isFarsi) "کارهای هم‌تیمی" else "Colleague items sync"
                        val pCategoryName = if (selectedCat == null) {
                            if (isFarsi) "همه تیم‌ها" else "All Workspaces"
                        } else {
                            CategoriesList.find { it.id == selectedCat }?.let { if (isFarsi) it.nameFa else it.nameEn } ?: selectedCat
                        }

                        val sb = StringBuilder()
                        sb.append("📋 $titleText\n")
                        sb.append("📌 " + (if (isFarsi) "تیم: " else "Workspace: ") + "$pCategoryName\n")
                        sb.append(if (isFarsi) "لیست تسک‌های ارسالی:\n" else "Shared list items:\n")
                        
                        tasksToShare.take(5).forEach { task ->
                            val status = if (task.isCompleted) "✓" else "☐"
                            val priorityEmoji = when (task.priority.lowercase()) {
                                "high" -> "🔴"
                                "medium" -> "🟡"
                                else -> "🟢"
                            }
                            sb.append("$status $priorityEmoji ${task.title}\n")
                        }
                        if (tasksToShare.size > 5) {
                            sb.append("... (" + (if (isFarsi) "و ${tasksToShare.size - 5} تسک دیگر" else "and ${tasksToShare.size - 5} more") + ")\n")
                        }

                        sb.append("\n" + (if (isFarsi) "جهت وارد کردن این تسک‌ها به برنامه خود، پیام را کپی کرده و در بخش همکاری برنامه همکارتان بگذارید:" else "Copy this message text, then clear and paste it in the import collaboration section of your app:") + "\n")
                        sb.append("=== START CODE ===\n")
                        sb.append(encodedCode)
                        sb.append("\n=== END CODE ===")

                        val sendText = sb.toString()
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(encodedCode))

                        // Sharing context intent calling
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, titleText)
                            putExtra(Intent.EXTRA_TEXT, sendText)
                        }
                        context.startActivity(Intent.createChooser(intent, if (isFarsi) "ارسال به همکاران" else "Send via"))
                        
                        Toast.makeText(
                            context,
                            if (isFarsi) "کد تیم کپی شد و آماده ارسال به همکاران است!" else "Team transfer code is copied!",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isFarsi) "تولید و ارسال اطلاعات تیم به همکاران" else "Generate & Share Team Workspace")
                }

                Spacer(modifier = Modifier.height(20.dp))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)).padding(vertical = 4.dp))
                Spacer(modifier = Modifier.height(16.dp))

                // SECTION 3: PASTE AND MERGE MANUALLY
                Text(
                    text = if (isFarsi) "۲. اتصال به تیم همکار بصورت متنی" else "2. Connect manually by text",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isFarsi) "پیامی که همکارتان براتون فرستاده را چسبانده و دکمه زیر را فشار دهید:" else "Paste the entire share message or code below:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = codeToImport,
                    onValueChange = { codeToImport = it },
                    placeholder = { Text(if (isFarsi) "کد یا پیام همکار را اینجا چسبانده و دکمه را بزنید..." else "Paste team code or message link...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (codeToImport.isBlank()) {
                            Toast.makeText(context, if (isFarsi) "کد نمی‌تواند خالی باشد!" else "Code cannot be empty!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        var cleanCode = codeToImport.trim()
                        if (cleanCode.contains("=== START CODE ===")) {
                            try {
                                val pre = cleanCode.split("=== START CODE ===")
                                if (pre.size > 1) {
                                    val post = pre[1].split("=== END CODE ===")
                                    if (post.isNotEmpty()) {
                                        cleanCode = post[0].trim()
                                    }
                                }
                            } catch (e: Exception) {
                                // Default back
                            }
                        }

                        val importedTasks = TaskShareHelper.importTasksFromCode(cleanCode)
                        if (importedTasks.isNullOrEmpty()) {
                            Toast.makeText(
                                context,
                                if (isFarsi) "قالب کد وارد شده صحیح نیست!" else "Invalid code structure!",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            viewModel.importSharedTasks(importedTasks)
                            Toast.makeText(
                                context,
                                if (isFarsi) "تعداد ${importedTasks.size} تسک با همکاران با موفقیت هماهنگ و ادغام شد! 🎉" else "Merged ${importedTasks.size} tasks with your team successfully! 🎉",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color.White)
                ) {
                    Icon(imageVector = Icons.Default.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isFarsi) "وارد کردن فیزیکی تسک‌های تیمی" else "Import & Merge Colleague Tasks")
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (isFarsi) "خروج و بازگشت" else "Close Sync Window")
                }
            }
        }
    }
}
