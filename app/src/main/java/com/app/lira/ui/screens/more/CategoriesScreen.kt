package com.app.lira.ui.screens.more

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.app.lira.data.CategoryStyle
import com.app.lira.data.repository.CategoryRepository
import com.app.lira.data.repository.CategoryRow
import com.app.lira.data.repository.TransactionRepository
import com.app.lira.ui.components.lira.*
import com.app.lira.ui.theme.CategoryPalette
import com.app.lira.ui.theme.LiraColors
import com.app.lira.ui.theme.LiraType
import kotlinx.coroutines.launch
import java.time.YearMonth

private val paletteNames = listOf("Groceries", "Eating out", "Coffee", "Transport", "Shopping", "Home & bills", "Health", "Fun", "Stays", "Other")

@Composable
fun CategoriesScreen(navController: NavHostController) {
    val scope = rememberCoroutineScope()
    var categories by remember { mutableStateOf<List<CategoryRow>?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var monthCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    // null = closed; id == -1 = new category
    var editing by remember { mutableStateOf<CategoryRow?>(null) }

    suspend fun reload() {
        CategoryRepository.getCategories()
            .onSuccess { rows -> categories = rows.sortedBy { it.name.lowercase() }; loadError = null; MoreData.publishCategories(rows) }
            .onFailure { loadError = it.message }
    }

    LaunchedEffect(Unit) {
        launch { reload() }
        launch {
            TransactionRepository.getTransactionsForMonth(YearMonth.now()).onSuccess { rows ->
                monthCounts = rows.filter { it.type == TransactionRepository.TYPE_PAYED }
                    .groupingBy { it.category?.name ?: "" }.eachCount()
            }
        }
    }

    LiraScreen {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            LiraTopBar("Categories", onBack = { navController.popBackStack() }) {
                IconCircleButton(Icons.Outlined.Add, "New category", { editing = CategoryRow(-1, "") }, background = Color.Transparent)
            }
            val list = categories
            when {
                list == null && loadError == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = LiraColors.TextSecondary, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }
                list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(Icons.Outlined.Category, "Couldn't load categories", loadError, actionLabel = "Try again", onAction = { scope.launch { reload() } })
                }
                list.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(Icons.Outlined.Category, "No categories yet", "Add the handful you spend on most — you can always change them.", actionLabel = "New category", onAction = { editing = CategoryRow(-1, "") })
                }
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(list, key = { it.id }) { cat ->
                        val look = CategoryStyle.look(cat.id, cat.name)
                        val n = monthCounts[cat.name] ?: 0
                        Row(
                            Modifier.fillMaxWidth().height(64.dp).liraClickable { editing = cat }.padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            CategoryIconBadge(look.icon, look.color, 36.dp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(cat.name, style = LiraType.BodyM.copy(fontWeight = FontWeight.Medium))
                                Text(
                                    when (n) { 0 -> "No entries this month"; 1 -> "1 entry this month"; else -> "$n entries this month" },
                                    style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextTertiary)
                                )
                            }
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Edit ${cat.name}", tint = LiraColors.TextTertiary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }

    editing?.let { target ->
        CategoryEditSheet(
            category = target.takeIf { it.id != -1 },
            all = categories.orEmpty(),
            onDismiss = { editing = null },
            onChanged = { scope.launch { reload() }; editing = null }
        )
    }
}

/** Name · colour · icon editor. Colour and icon are stored on-device (CategoryStyle). */
@Composable
private fun CategoryEditSheet(category: CategoryRow?, all: List<CategoryRow>, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(category?.name ?: "") }
    val initial = remember { CategoryStyle.selection(category?.id, category?.name) }
    var touched by remember { mutableStateOf(category != null) }
    var colorIdx by remember { mutableStateOf(initial.first) }
    var iconKey by remember { mutableStateOf(initial.second) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var entryCount by remember { mutableStateOf<Int?>(null) }

    // New categories follow the name ("Coffee" → cup, brown) until the user picks something.
    LaunchedEffect(name) {
        if (!touched) {
            val (c, i) = CategoryStyle.selection(null, name)
            colorIdx = c; iconKey = i
        }
    }
    LaunchedEffect(category?.id) {
        category?.let { MoreData.countEntriesInCategory(it.id).onSuccess { n -> entryCount = n } }
    }

    val color = CategoryPalette.all.getOrElse(colorIdx) { CategoryPalette.Other }

    LiraSheet(onDismiss = { if (!saving) onDismiss() }, title = if (category == null) "New category" else "Edit category") {
        if (confirmDelete && category != null) {
            val n = entryCount ?: 0
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Delete “${category.name}”? " + when (n) {
                        0 -> "Nothing uses it yet."
                        1 -> "Its 1 entry moves to Other."
                        else -> "Its $n entries move to Other."
                    } + " This can't be undone.",
                    style = LiraType.BodyM.copy(color = LiraColors.TextSecondary)
                )
                if (error != null) MessageLine(error!!)
                PrimaryButton("Delete category", {
                    scope.launch {
                        saving = true
                        error = null
                        MoreData.deleteCategoryMovingEntries(category.id, all)
                            .onSuccess { CategoryStyle.remove(category.id); onChanged() }
                            .onFailure { error = it.message }
                        saving = false
                    }
                }, Modifier.fillMaxWidth(), loading = saving)
                GhostButton("Cancel", { confirmDelete = false; error = null }, Modifier.fillMaxWidth())
            }
            return@LiraSheet
        }

        Column(
            Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CategoryIconBadge(CategoryStyle.icons.firstOrNull { it.key == iconKey }?.vector ?: Icons.Outlined.Category, color, 44.dp)
                LiraTextField(
                    value = name,
                    onValueChange = { if (it.length <= 32) { name = it; error = null } },
                    placeholder = "e.g. Coffee",
                    label = "Name",
                    modifier = Modifier.weight(1f)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Colour", style = LiraType.Label.copy(fontSize = 12.sp, color = LiraColors.TextSecondary))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    CategoryPalette.all.forEachIndexed { i, c ->
                        val selected = i == colorIdx
                        Box(
                            Modifier.size(34.dp)
                                .then(if (selected) Modifier.border(2.dp, LiraColors.TextPrimary, CircleShape) else Modifier)
                                .padding(4.dp).clip(CircleShape).background(c)
                                .liraClickable { colorIdx = i; touched = true }
                                .semantics { contentDescription = paletteNames[i] + " colour" + if (selected) ", selected" else "" }
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Icon", style = LiraType.Label.copy(fontSize = 12.sp, color = LiraColors.TextSecondary))
                CategoryStyle.icons.chunked(6).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { ic ->
                            val selected = ic.key == iconKey
                            Box(
                                Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp))
                                    .background(if (selected) color.copy(alpha = 0.18f) else LiraColors.Surface2)
                                    .then(if (selected) Modifier.border(1.5.dp, LiraColors.TextPrimary, RoundedCornerShape(14.dp)) else Modifier)
                                    .liraClickable { iconKey = ic.key; touched = true }
                                    .semantics { contentDescription = ic.key + " icon" + if (selected) ", selected" else "" },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(ic.vector, null, tint = if (selected) color else LiraColors.TextSecondary, modifier = Modifier.size(22.dp))
                            }
                        }
                        repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            if (error != null) MessageLine(error!!)

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (category != null) {
                    Row(
                        Modifier.height(52.dp).clip(CircleShape).liraClickable { error = null; confirmDelete = true }.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Outlined.DeleteOutline, null, tint = LiraColors.TextSecondary, modifier = Modifier.size(20.dp))
                        Text("Delete", style = LiraType.BodyM.copy(fontWeight = FontWeight.SemiBold, color = LiraColors.TextSecondary))
                    }
                }
                PrimaryButton(
                    if (category == null) "Add category" else "Save",
                    onClick = {
                        val trimmed = name.trim()
                        when {
                            trimmed.isEmpty() -> error = "Give it a name."
                            all.any { it.id != category?.id && it.name.trim().equals(trimmed, ignoreCase = true) } -> error = "You already have “$trimmed”."
                            else -> scope.launch {
                                saving = true
                                error = null
                                val result = if (category == null) {
                                    CategoryRepository.addCategory(trimmed).map { it.id }
                                } else if (trimmed != category.name) {
                                    CategoryRepository.updateCategory(category.id, trimmed).map { it.id }
                                } else Result.success(category.id)
                                result
                                    .onSuccess { id -> CategoryStyle.set(id, colorIdx, iconKey); onChanged() }
                                    .onFailure { error = it.message ?: "Couldn't save the category." }
                                saving = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = name.isNotBlank(),
                    loading = saving
                )
            }
            if (category != null) {
                val n = entryCount
                if (n != null && n > 0) {
                    Text(
                        if (n == 1) "Deleting moves its 1 entry to Other." else "Deleting moves its $n entries to Other.",
                        style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextTertiary),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
