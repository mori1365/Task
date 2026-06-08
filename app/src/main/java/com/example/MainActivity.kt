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

        setContent {
            MyApplicationTheme {
                val viewModel: TaskViewModel by viewModels {
                    TaskViewModel.Factory(repository)
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

@Composable
fun TaskAppScreen(viewModel: TaskViewModel) {
    // Dynamic Persian/English bilingual toggling
    var isFarsi by remember { mutableStateOf(true) }

    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val filteredTasks by viewModel.filteredTasks.collectAsStateWithLifecycle()
    val filterStatus by viewModel.filterStatus.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }

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
                            Text(
                                text = if (isFarsi) "تسک‌های من" else "My Tasks",
                                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF21005D) // Vibrant Palette Royal Purple Heading
                            )
                            Text(
                                text = if (isFarsi) "امروز، ۱۸ خرداد" else "Today, June 8",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            text = { Text(if (isFarsi) "همه (${tasks.size})" else "All (${tasks.size})", fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = filterStatus == TaskViewModel.FilterStatus.PENDING,
                            onClick = { viewModel.setFilterStatus(TaskViewModel.FilterStatus.PENDING) },
                            text = { Text(if (isFarsi) "در دست انجام (${tasks.count { !it.isCompleted }})" else "Pending (${tasks.count { !it.isCompleted }})", fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = filterStatus == TaskViewModel.FilterStatus.COMPLETED,
                            onClick = { viewModel.setFilterStatus(TaskViewModel.FilterStatus.COMPLETED) },
                            text = { Text(if (isFarsi) "انجام شده (${tasks.count { it.isCompleted }})" else "Completed (${tasks.count { it.isCompleted }})", fontWeight = FontWeight.Bold) }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // CATEGORY SLIDABLE ROW CHIPS
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // "All" Category Chip
                        item {
                            FilterChip(
                                selected = selectedCategory == null,
                                onClick = { viewModel.setSelectedCategory(null) },
                                label = { Text(text = if (isFarsi) "همه دسته‌ها" else "All Categories", fontWeight = FontWeight.Medium) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.GridView,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        // List of Categories
                        items(CategoriesList) { cat ->
                            val isSelected = selectedCategory == cat.id
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setSelectedCategory(cat.id) },
                                label = { Text(if (isFarsi) cat.nameFa else cat.nameEn, fontWeight = FontWeight.Medium) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = cat.icon,
                                        contentDescription = null,
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else cat.color,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // TASK LIST CONTAINER
                    Box(modifier = Modifier.weight(1f)) {
                        if (filteredTasks.isEmpty()) {
                            // BEAUTIFUL EMPTY STATE PRESENTATION
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(120.dp)
                                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = if (searchQuery.isNotEmpty() || selectedCategory != null) Icons.Default.FilterListOff else Icons.Default.AssignmentTurnedIn,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(64.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(18.dp))
                                Text(
                                    text = if (searchQuery.isNotEmpty()) {
                                        if (isFarsi) "موردی یافت نشد!" else "No search matches"
                                    } else if (selectedCategory != null) {
                                        if (isFarsi) "کار جدیدی در این دسته‌بندی نیست" else "No tasks in this category"
                                    } else {
                                        if (isFarsi) "لیست کارها خالی است" else "List is currently empty"
                                    },
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (searchQuery.isNotEmpty()) {
                                        if (isFarsi) "عبارت دیگری را امتحان کنید یا فیلترها را بردارید" else "Try looking for another term or reset selections!"
                                    } else if (isFarsi) {
                                        "از دکمه پایین صفحه (+) برای اضافه کردن اولین کار استفاده کنید"
                                    } else {
                                        "Tap the floating add button (+) below to begin listing your goals!"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                if (tasks.isNotEmpty() && (searchQuery.isNotEmpty() || selectedCategory != null || filterStatus != TaskViewModel.FilterStatus.ALL)) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    TextButton(onClick = {
                                        viewModel.setSearchQuery("")
                                        viewModel.setSelectedCategory(null)
                                        viewModel.setFilterStatus(TaskViewModel.FilterStatus.ALL)
                                    }) {
                                        Text(if (isFarsi) "پاک کردن همه فیلترها" else "Reset All Filters")
                                    }
                                }
                            }
                        } else {
                            // RENDER TASKS CARDS LIST
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("task_list"),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 80.dp)
                            ) {
                                items(filteredTasks, key = { it.id }) { task ->
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

        // CUSTOM TASK ADD CREATOR DIALOG
        if (showAddDialog) {
            TaskAddDialog(
                isFarsi = isFarsi,
                onDismiss = { showAddDialog = false },
                onAdd = { title, desc, cat, priority, dueDate ->
                    viewModel.addTask(title, desc, cat, priority, dueDate)
                    showAddDialog = false
                }
            )
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
    // Dynamic border color depending on priority
    val priorityColor = when (task.priority.lowercase()) {
        "high" -> Color(0xFFEF4444)
        "medium" -> Color(0xFFF59E0B)
        else -> Color(0xFF10B981)
    }

    // Category info resolution
    val catInfo = CategoriesList.firstOrNull { it.id == task.category } ?: CategoriesList[0]

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task_item_${task.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (task.isCompleted) {
                Color(0xFFE8DEF8).copy(alpha = 0.7f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (task.isCompleted) 0.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehindBorderEdge(priorityColor, strokeWidth = 5.dp, isRtl = isFarsi)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // STEP 1: CHECKBOX TICK BUTTON (Accurate touch target 48x48)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onToggle() }
                    .size(48.dp) // Accessibility Target Size
                    .testTag("task_checkbox_${task.id}")
            ) {
                Icon(
                    imageVector = if (task.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (task.isCompleted) "انجام شده" else "انجام نشده",
                    tint = MaterialTheme.colorScheme.primary, // #6750A4 central theme brand color
                    modifier = Modifier.size(26.dp)
                )
            }

            // STEP 2: BODY TEXT DETAILS
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null
                    ),
                    color = if (task.isCompleted) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (task.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (task.isCompleted) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // CHIPS LABEL SUB-ROW
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Category Badge Custom Tag
                    Row(
                        modifier = Modifier
                            .background(catInfo.color.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = catInfo.icon,
                            contentDescription = null,
                            tint = catInfo.color,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (isFarsi) catInfo.nameFa else catInfo.nameEn,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = catInfo.color,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Priority Pill Badge
                    val priorityLabel = when (task.priority.lowercase()) {
                        "high" -> if (isFarsi) "مهم" else "High"
                        "medium" -> if (isFarsi) "متوسط" else "Medium"
                        else -> if (isFarsi) "کم" else "Low"
                    }
                    Box(
                        modifier = Modifier
                            .background(priorityColor.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = priorityLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = priorityColor,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Due Date Badge
                    if (task.dueDate != null) {
                        val isOverdue = task.dueDate < System.currentTimeMillis() && !task.isCompleted
                        val badgeBg = if (isOverdue) Color(0xFFFEE2E2) else Color(0xFFE0F2FE)
                        val badgeColor = if (isOverdue) Color(0xFFEF4444) else Color(0xFF0284C7)
                        Row(
                            modifier = Modifier
                                .background(badgeBg, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Event,
                                contentDescription = null,
                                tint = badgeColor,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = (if (isFarsi) "سررسید: " else "Due: ") + formatDueDate(task.dueDate, isFarsi),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = badgeColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Relative Elapsed Timestamp Representation
                    Text(
                        text = getRelativeTime(task.createdAt, isFarsi),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            // STEP 3: DELETE RECYCLE TRASH ACTION (Target size 48x48)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onDelete() }
                    .size(48.dp)
                    .testTag("delete_task_${task.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = if (isFarsi) "حذف تسک" else "Delete Task",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

// Special dynamic custom modifier to draw the left (or right if RTL) card status accent border
fun Modifier.drawBehindBorderEdge(color: Color, strokeWidth: androidx.compose.ui.unit.Dp, isRtl: Boolean): Modifier {
    return this.drawBehind {
        val width = strokeWidth.toPx()
        if (isRtl) {
            // Draw on right edge for RTL languages
            drawRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(size.width - width, 0f),
                size = androidx.compose.ui.geometry.Size(width, size.height)
            )
        } else {
            // Draw on left edge for LTR languages
            drawRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
                size = androidx.compose.ui.geometry.Size(width, size.height)
            )
        }
    }
}

fun formatDueDate(timestamp: Long, isFarsi: Boolean): String {
    val date = java.util.Date(timestamp)
    return if (isFarsi) {
        getPersianDate(date)
    } else {
        val sdf = SimpleDateFormat("MMM d, yyyy", Locale.US)
        sdf.format(date)
    }
}

fun getPersianDate(date: java.util.Date): String {
    val calendar = Calendar.getInstance()
    calendar.time = date
    val gYear = calendar.get(Calendar.YEAR)
    val gMonth = calendar.get(Calendar.MONTH) + 1
    val gDay = calendar.get(Calendar.DAY_OF_MONTH)
    
    val gDaysInMonth = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
    var gy = gYear - 1600
    var gm = gMonth - 1
    val gd = gDay - 1
    
    var gDayNo = 365 * gy + (gy + 3) / 4 - (gy + 99) / 100 + (gy + 399) / 400
    gDayNo += gDaysInMonth[gm]
    if (gm > 1 && ((gYear % 4 == 0 && gYear % 100 != 0) || (gYear % 400 == 0))) {
        gDayNo++
    }
    gDayNo += gd

    var jDayNo = gDayNo - 79
    val jNpm = jDayNo / 12053
    jDayNo %= 12053
    
    var jy = 979 + 33 * jNpm + 4 * (jDayNo / 1461)
    jDayNo %= 1461
    
    if (jDayNo >= 366) {
        jy += (jDayNo - 1) / 365
        jDayNo = (jDayNo - 1) % 365
    }
    
    var jm = 0
    var i = 0
    val jDaysInMonth = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)
    while (i < 12 && jDayNo >= jDaysInMonth[i]) {
        jDayNo -= jDaysInMonth[i]
        i++
    }
    jm = i + 1
    val jd = jDayNo + 1
    
    val pMonthName = when (jm) {
        1 -> "فروردین"
        2 -> "اردیبهشت"
        3 -> "خرداد"
        4 -> "تیر"
        5 -> "مرداد"
        6 -> "شهریور"
        7 -> "مهر"
        8 -> "آبان"
        9 -> "آذر"
        10 -> "دی"
        11 -> "بهمن"
        else -> "اسفند"
    }
    
    return "$jd $pMonthName $jy"
}


@Composable
fun TaskAddDialog(
    isFarsi: Boolean,
    onDismiss: () -> Unit,
    onAdd: (title: String, description: String, category: String, priority: String, dueDate: Long?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCat by remember { mutableStateOf("General") }
    var selectedPriority by remember { mutableStateOf("Medium") }
    var selectedDueDate by remember { mutableStateOf<Long?>(null) }

    var showError by remember { mutableStateOf(false) }

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
                    .padding(20.dp)
            ) {
                // Header Titles
                Text(
                    text = if (isFarsi) "افزودن کار جدید" else "Create New Task",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = if (isFarsi) TextAlign.Right else TextAlign.Left
                )
                Text(
                    text = if (isFarsi) "مشخصات تسک خود را در زیر وارد کنید" else "Fill in the parameters below",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    textAlign = if (isFarsi) TextAlign.Right else TextAlign.Left
                )

                // Task Title Field
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        if (it.isNotBlank()) showError = false
                    },
                    label = { Text(if (isFarsi) "عنوان کار" else "Task Title") },
                    isError = showError,
                    modifier = Modifier.fillMaxWidth().testTag("add_title_field"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                if (showError) {
                    Text(
                        text = if (isFarsi) "عنوان کار نمی‌تواند خالی باشد!" else "Title cannot be empty!",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Task Description Field
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(if (isFarsi) "توضیحات (اختیاری)" else "Description (Optional)") },
                    modifier = Modifier.fillMaxWidth().testTag("add_desc_field"),
                    minLines = 2,
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Category Grid Selection
                Text(
                    text = if (isFarsi) "دسته‌بندی:" else "Select Category:",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(CategoriesList) { cat ->
                        val isSelected = selectedCat == cat.id
                        val containerColor = if (isSelected) cat.color else cat.color.copy(alpha = 0.08f)
                        val textColor = if (isSelected) Color.White else cat.color

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(containerColor)
                                .clickable { selectedCat = cat.id }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
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

                Spacer(modifier = Modifier.height(16.dp))

                // Priority Row Selection
                Text(
                    text = if (isFarsi) "میزان اهمیت (اولویت):" else "Priority Label:",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Low", "Medium", "High").forEach { pr ->
                        val label = when (pr) {
                            "Low" -> if (isFarsi) "کم" else "Low"
                            "Medium" -> if (isFarsi) "متوسط" else "Medium"
                            else -> if (isFarsi) "فوری" else "High"
                        }
                        val baseColor = when (pr) {
                            "Low" -> Color(0xFF10B981)
                            "Medium" -> Color(0xFFF59E0B)
                            else -> Color(0xFFEF4444)
                        }
                        val isSelected = selectedPriority == pr
                        val containerColor = if (isSelected) baseColor else baseColor.copy(alpha = 0.08f)
                        val textColor = if (isSelected) Color.White else baseColor

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(containerColor)
                                .clickable { selectedPriority = pr }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = textColor,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Due Date Selection Section
                Text(
                    text = if (isFarsi) "تاریخ سررسید (اختیاری):" else "Due Date (Optional):",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))

                val context = LocalContext.current
                val calendar = Calendar.getInstance()
                val datePickerDialog = DatePickerDialog(
                    context,
                    { _, year, month, dayOfMonth ->
                        val selectedCal = Calendar.getInstance()
                        selectedCal.set(Calendar.YEAR, year)
                        selectedCal.set(Calendar.MONTH, month)
                        selectedCal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                        selectedCal.set(Calendar.HOUR_OF_DAY, 23)
                        selectedCal.set(Calendar.MINUTE, 59)
                        selectedCal.set(Calendar.SECOND, 59)
                        selectedDueDate = selectedCal.timeInMillis
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable { datePickerDialog.show() }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Event,
                        contentDescription = if (isFarsi) "انتخاب تاریخ" else "Select Date",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (selectedDueDate != null) {
                            formatDueDate(selectedDueDate!!, isFarsi)
                        } else {
                            if (isFarsi) "انتخاب تاریخ سررسید..." else "Choose due date..."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selectedDueDate != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.weight(1f)
                    )
                    if (selectedDueDate != null) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { selectedDueDate = null }
                                .padding(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = if (isFarsi) "پاک کردن" else "Clear",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (isFarsi) "انصراف" else "Cancel")
                    }

                    Button(
                        onClick = {
                            if (title.isBlank()) {
                                showError = true
                            } else {
                                onAdd(title, description, selectedCat, selectedPriority, selectedDueDate)
                            }
                        },
                        modifier = Modifier.weight(1f).testTag("add_task_dialog_confirm"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (isFarsi) "افزودن کار" else "Create")
                    }
                }
            }
        }
    }
}
