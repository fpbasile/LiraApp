package com.app.lira.ui.screens

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.app.lira.R
import com.app.lira.data.HomeDataCache
import com.app.lira.data.model.Category
import com.app.lira.data.repository.CategoryRepository
import com.app.lira.data.repository.CategoryColorPalette
import com.app.lira.data.repository.DailyLimitRepository
import com.app.lira.data.repository.TransactionRepository
import com.app.lira.data.repository.toUiCategories
import com.app.lira.navigation.Routes
import com.app.lira.ui.components.*
import com.app.lira.ui.theme.*
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AddPayedScreen(navController: NavController) {
    val scope = rememberCoroutineScope()

    var amount by remember { mutableStateOf(TextFieldValue("")) }
    var description by remember { mutableStateOf(TextFieldValue("")) }
    var fromWho by remember { mutableStateOf(TextFieldValue("")) }
    var isReceivedMode by remember { mutableStateOf(false) }

    // CHANGED: Match the ID type (Int) used in your list
    var selectedCategoryId by remember { mutableStateOf<Int?>(null) }

    // Categories must come from the DB — transactions.category_id is a foreign key
    // into "category", so any client-only placeholder id would fail that constraint.
    // Seeded from HomeDataCache (prefetched in NavGraph) so there's no empty flash on first paint.
    var categories by remember { mutableStateOf(HomeDataCache.categories) }

    suspend fun refreshCategories() {
        CategoryRepository.getCategories().onSuccess { rows ->
            categories = rows.toUiCategories()
            HomeDataCache.categories = categories
        }
    }

    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var isSavingCategory by remember { mutableStateOf(false) }
    var addCategoryError by remember { mutableStateOf<String?>(null) }

    var isSavingTransaction by remember { mutableStateOf(false) }
    var transactionError by remember { mutableStateOf<String?>(null) }

    var todaySpent by remember { mutableStateOf(HomeDataCache.todaySpent) }
    var todayReceived by remember { mutableStateOf(HomeDataCache.todayReceived) }
    var dailyLimit by remember { mutableStateOf(HomeDataCache.dailyLimit) }

    suspend fun refreshOverview() {
        TransactionRepository.getTodaySpent().onSuccess { todaySpent = it; HomeDataCache.todaySpent = it }
        TransactionRepository.getTodayReceived().onSuccess { todayReceived = it; HomeDataCache.todayReceived = it }
        DailyLimitRepository.getDailyLimit().onSuccess { dailyLimit = it; HomeDataCache.dailyLimit = it }
    }

    LaunchedEffect(Unit) {
        refreshOverview()
        refreshCategories()
    }

    val onSubmitTransaction: () -> Unit = {
        val parsedAmount = amount.text.toDoubleOrNull()
        if (parsedAmount == null || parsedAmount <= 0.0) {
            transactionError = "Enter a valid amount"
        } else if (!isReceivedMode && selectedCategoryId == null) {
            transactionError = "Pick a category"
        } else {
            scope.launch {
                isSavingTransaction = true
                transactionError = null
                val result = TransactionRepository.addTransaction(
                    amount = parsedAmount,
                    type = if (isReceivedMode) TransactionRepository.TYPE_RECEIVED else TransactionRepository.TYPE_PAYED,
                    categoryId = selectedCategoryId,
                    source = if (isReceivedMode) fromWho.text else null,
                    description = description.text
                )
                isSavingTransaction = false
                result.onSuccess {
                    amount = TextFieldValue("")
                    description = TextFieldValue("")
                    fromWho = TextFieldValue("")
                    selectedCategoryId = null
                    refreshOverview()
                }.onFailure { e ->
                    transactionError = e.message ?: "Couldn't save"
                }
            }
        }
    }

    if (showAddCategoryDialog) {
        AddCategoryDialog(
            isSaving = isSavingCategory,
            errorMessage = addCategoryError,
            onDismiss = {
                showAddCategoryDialog = false
                addCategoryError = null
            },
            onConfirm = { name ->
                scope.launch {
                    isSavingCategory = true
                    addCategoryError = null
                    val result = CategoryRepository.addCategory(name)
                    isSavingCategory = false
                    result.onSuccess { row ->
                        // The "category" table has no color column, so the color is a
                        // purely local/UI concern, assigned client-side from the palette.
                        val colorHex = CategoryColorPalette[categories.size % CategoryColorPalette.size]
                        categories = categories + Category(id = row.id, name = row.name, colorHex = colorHex)
                        showAddCategoryDialog = false
                    }.onFailure { e ->
                        addCategoryError = e.message ?: "Couldn't add category"
                    }
                }
            }
        )
    }

    Scaffold(
        containerColor = LiraBackground,
        bottomBar = {
            LiraBottomNavBar(
                currentRoute = Routes.ADD_PAYED,
                onNavigate = { route ->
                    navController.navigate(route) {
                        // This prevents multiple copies of the same screen on the backstack
                        launchSingleTop = true
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 15.dp)
                .verticalScroll(rememberScrollState())
                .imePadding()
        ) {
            TopLogoBar(onSettingsClick = { navController.navigate(Routes.SETTINGS) })
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isReceivedMode) "Add Received" else "Add Paid",
                    color = LiraTextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    painter = painterResource(id = R.drawable.ic_switch),
                    contentDescription = "Switch",
                    tint = LiraTextPrimary,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable {
                            isReceivedMode = !isReceivedMode
                            transactionError = null
                        }
                )
            }

            Spacer(Modifier.height(8.dp))

            TransactionForm(
                isReceivedMode = isReceivedMode,
                amount = amount,
                onAmountChange = { amount = it },
                desc = description,
                onDescChange = { description = it },
                categories = categories,
                selectedCategoryId = selectedCategoryId,
                onCategorySelect = { id -> selectedCategoryId = id },
                onAddCategoryClick = { showAddCategoryDialog = true },
                fromWho = fromWho,
                onFromWhoChange = { fromWho = it },
                isSavingTransaction = isSavingTransaction,
                transactionError = transactionError,
                onSubmitTransaction = onSubmitTransaction
            )

            Spacer(Modifier.height(24.dp))
            OverviewCard(
                spent = todaySpent,
                received = todayReceived,
                limit = dailyLimit,
                onClick = { navController.navigate(Routes.TRANSACTIONS) }
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransactionForm(
    isReceivedMode: Boolean,
    amount: TextFieldValue,
    onAmountChange: (TextFieldValue) -> Unit,
    desc: TextFieldValue,
    onDescChange: (TextFieldValue) -> Unit,
    categories: List<Category>,
    selectedCategoryId: Int?,
    onCategorySelect: (Int) -> Unit,
    onAddCategoryClick: () -> Unit,
    fromWho: TextFieldValue,
    onFromWhoChange: (TextFieldValue) -> Unit,
    isSavingTransaction: Boolean,
    transactionError: String?,
    onSubmitTransaction: () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (isReceivedMode) 180f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "form_flip"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .border(1.dp, LiraBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12 * density
            }
    ) {
        if (rotation <= 90f) {
            PaidFormFields(
                amount = amount,
                onAmountChange = onAmountChange,
                desc = desc,
                onDescChange = onDescChange,
                categories = categories,
                selectedCategoryId = selectedCategoryId,
                onCategorySelect = onCategorySelect,
                onAddCategoryClick = onAddCategoryClick,
                isSavingTransaction = isSavingTransaction,
                transactionError = transactionError,
                onSubmitTransaction = onSubmitTransaction
            )
        } else {
            Box(modifier = Modifier.graphicsLayer { rotationY = 180f }) {
                ReceivedFormFields(
                    amount = amount,
                    onAmountChange = onAmountChange,
                    categories = categories,
                    selectedCategoryId = selectedCategoryId,
                    onCategorySelect = onCategorySelect,
                    onAddCategoryClick = onAddCategoryClick,
                    fromWho = fromWho,
                    onFromWhoChange = onFromWhoChange,
                    desc = desc,
                    onDescChange = onDescChange,
                    isSavingTransaction = isSavingTransaction,
                    transactionError = transactionError,
                    onSubmitTransaction = onSubmitTransaction
                )
            }
        }
    }
}

@Composable
private fun PaidFormFields(
    amount: TextFieldValue,
    onAmountChange: (TextFieldValue) -> Unit,
    desc: TextFieldValue,
    onDescChange: (TextFieldValue) -> Unit,
    categories: List<Category>,
    selectedCategoryId: Int?,
    onCategorySelect: (Int) -> Unit,
    onAddCategoryClick: () -> Unit,
    isSavingTransaction: Boolean,
    transactionError: String?,
    onSubmitTransaction: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        LiraTextField(amount, onAmountChange, "Enter amount", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(Modifier.height(20.dp))

        CategoryPickerSection(categories, selectedCategoryId, onCategorySelect, onAddCategoryClick)

        Spacer(Modifier.height(20.dp))
        TimeDateSection()
        Spacer(Modifier.height(20.dp))
        DescriptionSection(desc, onDescChange)
        Spacer(Modifier.height(24.dp))
        AddActionButton(isSavingTransaction, transactionError, onSubmitTransaction)
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun ReceivedFormFields(
    amount: TextFieldValue,
    onAmountChange: (TextFieldValue) -> Unit,
    categories: List<Category>,
    selectedCategoryId: Int?,
    onCategorySelect: (Int) -> Unit,
    onAddCategoryClick: () -> Unit,
    fromWho: TextFieldValue,
    onFromWhoChange: (TextFieldValue) -> Unit,
    desc: TextFieldValue,
    onDescChange: (TextFieldValue) -> Unit,
    isSavingTransaction: Boolean,
    transactionError: String?,
    onSubmitTransaction: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        LiraTextField(amount, onAmountChange, "Enter amount", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(Modifier.height(20.dp))

        CategoryPickerSection(categories, selectedCategoryId, onCategorySelect, onAddCategoryClick, isOptional = true)

        Spacer(Modifier.height(20.dp))
        Text("From who", color = LiraTextPrimary, fontWeight = FontWeight.Medium, fontSize = 16.sp)
        Spacer(Modifier.height(12.dp))
        LiraTextField(fromWho, onFromWhoChange, "Who sent it?", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text))

        Spacer(Modifier.height(20.dp))
        TimeDateSection()
        Spacer(Modifier.height(20.dp))
        DescriptionSection(desc, onDescChange)
        Spacer(Modifier.height(24.dp))
        AddActionButton(isSavingTransaction, transactionError, onSubmitTransaction)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryPickerSection(
    categories: List<Category>,
    selectedCategoryId: Int?,
    onCategorySelect: (Int) -> Unit,
    onAddCategoryClick: () -> Unit,
    isOptional: Boolean = false
) {
    val categoryRows = categories.chunked(3)
    val categoryPages = categoryRows.chunked(2)
    val pagerState = rememberPagerState(pageCount = { categoryPages.size })

    Column {
        // Category Header with "Add +"
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Category", color = LiraTextPrimary, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                if (isOptional) {
                    Text("(optional)", color = LiraTextPrimary.copy(alpha = 0.5f), fontSize = 13.sp)
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.clickable { onAddCategoryClick() }
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

        // Horizontal Pager for categories (3 columns, 2 rows per page)
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { pageIndex ->
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                categoryPages[pageIndex].forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        row.forEach { cat ->
                            CategoryChip(cat, cat.id == selectedCategoryId, { onCategorySelect(cat.id) }, Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Pager Indicators (animated pills)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            repeat(pagerState.pageCount) { iteration ->
                val isActive = pagerState.currentPage == iteration
                val color by animateColorAsState(
                    targetValue = if (isActive) Color.White else LiraBorder,
                    animationSpec = tween(250),
                    label = "dot_color"
                )
                val width by animateDpAsState(
                    targetValue = if (isActive) 16.dp else 6.dp,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "dot_width"
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color)
                        .size(width = width, height = 6.dp)
                )
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun TimeDateSection() {
    val currentTime = remember { LocalTime.now().format(DateTimeFormatter.ofPattern("HH : mm")) }
    val currentDate = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("d/M/yyyy")) }

    Text("Time / Date", color = LiraTextPrimary, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(12.dp))
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LiraReadOnlyField(currentTime, Modifier.weight(1f))
        Text("/", color = LiraTextPrimary, fontSize = 16.sp)
        LiraReadOnlyField(currentDate, Modifier.weight(1f))
    }
}

@Composable
private fun DescriptionSection(desc: TextFieldValue, onDescChange: (TextFieldValue) -> Unit) {
    Text("Description", color = LiraTextPrimary, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(12.dp))
    LiraTextField(desc, onDescChange, "(Optional)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text))
}

@Composable
private fun AddActionButton(
    isSaving: Boolean,
    errorMessage: String?,
    onClick: () -> Unit
) {
    // clickable()'s built-in press indication is deliberately delayed inside a
    // scrollable container (to disambiguate from a fling), so a normal quick tap
    // never shows the scale animation. Detecting the press ourselves fires instantly.
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "btn_scale"
    )

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .width(150.dp)
                .height(40.dp)
                .graphicsLayer(scaleX = scale, scaleY = scale)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .pointerInput(isSaving) {
                    detectTapGestures(
                        onPress = {
                            if (isSaving) return@detectTapGestures
                            isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        },
                        onTap = { if (!isSaving) onClick() }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color.Black,
                    strokeWidth = 2.dp
                )
            } else {
                Text("ADD", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        errorMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = Color(0xFFE53935), fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun AddCategoryDialog(
    isSaving: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(TextFieldValue("")) }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        containerColor = LiraBackground,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text("New Category", color = LiraTextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column {
                LiraTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "Category name",
                    modifier = Modifier.height(58.dp),
                    fontSize = 18.sp
                )
                errorMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = Color(0xFFE53935), fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.text.isNotBlank()) onConfirm(name.text.trim()) },
                enabled = !isSaving && name.text.isNotBlank()
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = LiraTextPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Add", color = LiraTextPrimary, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("Cancel", color = LiraTextSecondary)
            }
        }
    )
}

@Composable
private fun TopLogoBar(onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 0.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The Logo
        Image(
            painter = painterResource(id = R.drawable.lira_logo),
            contentDescription = "Lira Logo",
            modifier = Modifier.height(35.dp)
        )

        // The Settings Icon
        Icon(
            painter = painterResource(id = R.drawable.ic_settings),
            contentDescription = "Settings",
            tint = LiraTextPrimary,
            modifier = Modifier
                .size(24.dp)
                .clickable { onSettingsClick() }
        )
    }
}

@Composable
private fun OverviewCard(spent: Double, received: Double, limit: Double, onClick: () -> Unit) {
    val progress = if (limit > 0.0) (spent / limit).toFloat().coerceIn(0f, 1f) else 0f

    Column(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Text(
            text = "Overview",
            color = LiraTextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            border = BorderStroke(1.dp, LiraBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    text = "Today's Spending",
                    color = LiraTextPrimary.copy(alpha = 0.8f),
                    fontSize = 16.sp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = String.format("€%.2f", spent),
                    color = LiraTextPrimary,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(4.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    color = LiraProgressBarIndicator,
                    trackColor = LiraProgressBarTrack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                )

                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Received today",
                        color = LiraTextPrimary.copy(alpha = 0.6f),
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = String.format("+€%.2f", received),
                        color = Color(0xFF4CAF50),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}