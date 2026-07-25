package com.app.lira.ui.screens

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.app.lira.R
import com.app.lira.data.model.Category
import com.app.lira.navigation.Routes
import com.app.lira.ui.components.*
import com.app.lira.ui.theme.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AddPayedScreen(navController: NavController) {
    var amount by remember { mutableStateOf(TextFieldValue("")) }
    var description by remember { mutableStateOf(TextFieldValue("")) }

    // CHANGED: Match the ID type (Int) used in your list
    var selectedCategoryId by remember { mutableStateOf<Int?>(null) }

    val categories = remember {
        listOf(
            Category(id = 1, name = "Food", colorHex = 0xFFE53935),
            Category(id = 2, name = "Bills", colorHex = 0xFF26C6DA),
            Category(id = 3, name = "Shopping", colorHex = 0xFF1E88E5),
            Category(id = 4, name = "Fun", colorHex = 0xFFC6FF00),
            Category(id = 5, name = "Health", colorHex = 0xFFFFB300),
            Category(id = 6, name = "Transport", colorHex = 0xFF26A69A),
            Category(id = 7, name = "Home", colorHex = 0xFFD81B60),
            Category(id = 8, name = "Tech", colorHex = 0xFF8E24AA),
            Category(id = 9, name = "Other", colorHex = 0xFF66BB6A),
            Category(id = 10, name = "Other2", colorHex = 0xFF66BB6A),

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
            Spacer(Modifier.height(16.dp))
            TopLogoBar()
            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Add payed",
                    color = LiraTextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    painter = painterResource(id = R.drawable.ic_switch),
                    contentDescription = "Switch",
                    tint = LiraTextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            AddPaidForm(
                amount = amount,
                onAmountChange = { amount = it },
                desc = description,
                onDescChange = { description = it },
                categories = categories,
                selectedCategoryId = selectedCategoryId,
                onCategorySelect = { id -> selectedCategoryId = id }
            )

            Spacer(Modifier.height(24.dp))
            OverviewCard(123.56, 0.7f)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AddPaidForm(
    amount: TextFieldValue,
    onAmountChange: (TextFieldValue) -> Unit,
    desc: TextFieldValue,
    onDescChange: (TextFieldValue) -> Unit,
    categories: List<Category>,
    selectedCategoryId: Int?,
    onCategorySelect: (Int) -> Unit
) {
    val currentTime = remember { LocalTime.now().format(DateTimeFormatter.ofPattern("HH : mm")) }
    val currentDate = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("d/M/yyyy")) }

    val categoryPages = categories.chunked(8)
    val pagerState = rememberPagerState(pageCount = { categoryPages.size })

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, LiraBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        LiraTextField(amount, onAmountChange, "Enter amount", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(Modifier.height(20.dp))

        // Category Header with "Add +"
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Category", color = LiraTextPrimary, fontWeight = FontWeight.Medium, fontSize = 16.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "Add", color = LiraTextPrimary.copy(alpha = 0.6f), fontSize = 13.sp)
                Icon(
                    painter = painterResource(id = R.drawable.ic_add),
                    contentDescription = "Add Category",
                    tint = LiraTextPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Horizontal Pager for categories
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { pageIndex ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    categoryPages[pageIndex].take(4).forEach { cat ->
                        CategoryChip(cat, cat.id == selectedCategoryId, { onCategorySelect(cat.id) }, Modifier.weight(1f))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    categoryPages[pageIndex].drop(4).take(4).forEach { cat ->
                        CategoryChip(cat, cat.id == selectedCategoryId, { onCategorySelect(cat.id) }, Modifier.weight(1f))
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

        Spacer(Modifier.height(20.dp))
        Text("Time / Date", color = LiraTextPrimary, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LiraReadOnlyField(currentTime, Modifier.weight(1f))
            LiraReadOnlyField(currentDate, Modifier.weight(1f))
        }

        Spacer(Modifier.height(20.dp))
        Text("Description", color = LiraTextPrimary, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))
        LiraTextField(desc, onDescChange, "(Optional)",keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text
        )
        )

        Spacer(Modifier.height(24.dp))

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val addInteraction = remember { MutableInteractionSource() }
            val isPressed by addInteraction.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (isPressed) 0.95f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "btn_scale"
            )
            Button(
                onClick = { /* Logic */ },
                interactionSource = addInteraction,
                modifier = Modifier
                    .width(200.dp)
                    .height(50.dp)
                    .graphicsLayer(scaleX = scale, scaleY = scale),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("ADD", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun TopLogoBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
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
                .clickable { /* Handle settings click */ }
        )
    }
}

@Composable
private fun OverviewCard(spent: Double, progress: Float) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Overview",
            color = LiraTextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
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
                Spacer(Modifier.height(4.dp))
                Text(
                    text = String.format("$%.2f", spent),
                    color = LiraTextPrimary,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(20.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // The Red Progress Bar
                    LinearProgressIndicator(
                        progress = { progress },
                        color = LiraProgressBarIndicator,
                        trackColor = LiraProgressBarTrack,
                        modifier = Modifier
                            .weight(1f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )

                    // The 70% Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(LiraProgressTextBackground)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}