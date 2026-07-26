package com.app.lira.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.app.lira.R
import com.app.lira.data.HomeDataCache
import com.app.lira.data.model.Category
import com.app.lira.data.repository.CategoryRepository
import com.app.lira.data.repository.toUiCategories
import com.app.lira.ui.components.AddCategoryDialog
import com.app.lira.ui.components.ConfirmationDialog
import com.app.lira.ui.components.EditCategoryDialog
import com.app.lira.ui.theme.*
import kotlinx.coroutines.launch

private val ErrorColor = Color(0xFFE53935)

@Composable
fun ManageCategoriesScreen(navController: NavController) {
    val scope = rememberCoroutineScope()

    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    var showAddDialog by remember { mutableStateOf(false) }
    var isSavingAdd by remember { mutableStateOf(false) }
    var addError by remember { mutableStateOf<String?>(null) }

    var categoryToEdit by remember { mutableStateOf<Category?>(null) }
    var isSavingEdit by remember { mutableStateOf(false) }
    var editError by remember { mutableStateOf<String?>(null) }

    var categoryToDelete by remember { mutableStateOf<Category?>(null) }
    var isDeleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        CategoryRepository.getCategories().onSuccess { rows ->
            categories = rows.toUiCategories()
            HomeDataCache.categories = categories
        }.onFailure { e ->
            loadError = e.message
        }
    }

    LaunchedEffect(Unit) {
        isLoading = true
        refresh()
        isLoading = false
    }

    if (showAddDialog) {
        AddCategoryDialog(
            isSaving = isSavingAdd,
            errorMessage = addError,
            onDismiss = { showAddDialog = false; addError = null },
            onConfirm = { name ->
                scope.launch {
                    isSavingAdd = true
                    addError = null
                    val result = CategoryRepository.addCategory(name)
                    isSavingAdd = false
                    result.onSuccess {
                        showAddDialog = false
                        refresh()
                    }.onFailure { e ->
                        addError = e.message ?: "Couldn't add category"
                    }
                }
            }
        )
    }

    categoryToEdit?.let { editing ->
        EditCategoryDialog(
            currentName = editing.name,
            isSaving = isSavingEdit,
            errorMessage = editError,
            onDismiss = { categoryToEdit = null; editError = null },
            onConfirm = { name ->
                scope.launch {
                    isSavingEdit = true
                    editError = null
                    val result = CategoryRepository.updateCategory(editing.id, name)
                    isSavingEdit = false
                    result.onSuccess {
                        categoryToEdit = null
                        refresh()
                    }.onFailure { e ->
                        editError = e.message ?: "Couldn't rename category"
                    }
                }
            }
        )
    }

    categoryToDelete?.let { deleting ->
        ConfirmationDialog(
            title = "Delete \"${deleting.name}\"?",
            message = deleteError
                ?: "This can't be undone. Transactions already using this category will keep referencing it.",
            confirmLabel = "Delete",
            isLoading = isDeleting,
            onDismiss = { categoryToDelete = null; deleteError = null },
            onConfirm = {
                scope.launch {
                    isDeleting = true
                    deleteError = null
                    val result = CategoryRepository.deleteCategory(deleting.id)
                    isDeleting = false
                    result.onSuccess {
                        categoryToDelete = null
                        refresh()
                    }.onFailure { e ->
                        deleteError = e.message ?: "Couldn't delete category"
                    }
                }
            }
        )
    }

    Scaffold(containerColor = LiraBackground) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            Box(Modifier.fillMaxWidth()) {
                Text(
                    text = "←",
                    color = LiraTextPrimary,
                    fontSize = 22.sp,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .clickable { navController.popBackStack() }
                )
                Text(
                    text = "Manage Categories",
                    color = LiraTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Categories", color = LiraTextPrimary, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.clickable { showAddDialog = true }
                ) {
                    Text(text = "Add", color = LiraTextPrimary.copy(alpha = 0.6f), fontSize = 13.sp)
                    Icon(
                        painter = painterResource(id = R.drawable.ic_add),
                        contentDescription = "Add Category",
                        tint = LiraTextPrimary.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = LiraTextPrimary)
                }
                loadError != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(loadError ?: "", color = ErrorColor, fontSize = 14.sp)
                }
                categories.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No categories yet", color = LiraTextSecondary, fontSize = 14.sp)
                }
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(categories, key = { it.id }) { category ->
                        CategoryRow(
                            category = category,
                            onEdit = { categoryToEdit = category },
                            onDelete = { categoryToDelete = category }
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(category: Category, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(Color(category.colorHex))
        )

        Spacer(Modifier.width(12.dp))

        Text(
            text = category.name,
            color = LiraTextPrimary,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = "Edit",
            color = LiraTextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable { onEdit() }
        )

        Spacer(Modifier.width(16.dp))

        Text(
            text = "Delete",
            color = ErrorColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable { onDelete() }
        )
    }
}
