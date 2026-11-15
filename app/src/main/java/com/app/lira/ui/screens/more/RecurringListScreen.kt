package com.app.lira.ui.screens.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.app.lira.data.CategoryStyle
import com.app.lira.data.CurrencyPreference
import com.app.lira.data.model.Recurring
import com.app.lira.data.repository.CategoryRepository
import com.app.lira.data.repository.CategoryRow
import com.app.lira.data.repository.RecurringRepository
import com.app.lira.data.repository.TransactionRepository
import com.app.lira.navigation.Routes
import com.app.lira.ui.components.lira.*
import com.app.lira.ui.theme.LiraColors
import com.app.lira.ui.theme.LiraType
import com.app.lira.ui.theme.NumberFont
import com.app.lira.util.Money
import com.app.lira.data.AppPrefs
import com.app.lira.data.HomeStatsRepository
import com.app.lira.data.HomeTxn
import com.app.lira.data.SubscriptionCandidate
import com.app.lira.data.SubscriptionDetector
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
fun RecurringListScreen(navController: NavHostController) {
    val scope = rememberCoroutineScope()
    val code = CurrencyPreference.code
    var entries by remember { mutableStateOf<List<Recurring>?>(null) }
    var categories by remember { mutableStateOf<List<CategoryRow>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var categoriesLoaded by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf<List<HomeTxn>?>(null) }
    var detected by remember { mutableStateOf<List<SubscriptionCandidate>?>(null) }
    var handled by remember { mutableStateOf<Set<String>>(emptySet()) }
    var addingKey by remember { mutableStateOf<String?>(null) }
    var confirmation by remember { mutableStateOf<String?>(null) }
    var suggestionError by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        RecurringRepository.getRecurring()
            .onSuccess { entries = it; loadError = null }
            .onFailure { loadError = it.message }
    }

    // Reload whenever we come back from the editor.
    LaunchedEffect(Unit) {
        launch { reload() }
        launch {
            CategoryRepository.getCategories().onSuccess { categories = it }
            categoriesLoaded = true
        }
        launch { history = HomeStatsRepository.recent(400).getOrNull() ?: emptyList() }
    }

    // Subscription detection: once per screen open, off the main thread, after the inputs are in.
    LaunchedEffect(history != null && entries != null && categoriesLoaded) {
        val txns = history ?: return@LaunchedEffect
        val existing = entries ?: return@LaunchedEffect
        if (!categoriesLoaded || detected != null) return@LaunchedEffect
        val names = categories.associate { it.id to it.name }
        val dismissed = AppPrefs.dismissedSubscriptions
        detected = withContext(Dispatchers.Default) {
            runCatching {
                SubscriptionDetector.detect(txns, existing, code, LocalDate.now(), names, dismissed)
                    // Recurring items post in the home currency, so only suggest those.
                    .filter { it.currency.equals(code, ignoreCase = true) }
            }.getOrDefault(emptyList())
        }
    }
    LaunchedEffect(confirmation) {
        if (confirmation != null) { delay(4000); confirmation = null }
    }

    val suggestions = detected.orEmpty().filter { it.key !in handled }.take(3)
    fun addSuggestion(c: SubscriptionCandidate) {
        if (addingKey != null) return
        addingKey = c.key
        scope.launch {
            RecurringRepository.addRecurring(
                name = c.name, amount = c.amount, startDate = maxOf(c.nextExpected, java.time.LocalDate.now().plusDays(1)).toString(), interval = "monthly",
                occurrences = null, type = TransactionRepository.TYPE_PAYED, categoryId = c.categoryId
            ).onSuccess {
                handled = handled + c.key
                suggestionError = null
                confirmation = "${c.name} is now recurring · first on ${Dates.short(maxOf(c.nextExpected, java.time.LocalDate.now().plusDays(1)))}"
                reload()
            }.onFailure { suggestionError = it.message ?: "Couldn't add it. Try again." }
            addingKey = null
        }
    }
    fun dismissSuggestion(c: SubscriptionCandidate) {
        AppPrefs.dismissedSubscriptions = AppPrefs.dismissedSubscriptions + c.key
        handled = handled + c.key
    }
    val suggestionBlock: @Composable () -> Unit = {
        SubscriptionSuggestions(suggestions, confirmation, suggestionError, addingKey, categories, { addSuggestion(it) }, { dismissSuggestion(it) })
    }

    val today = LocalDate.now()
    LiraScreen {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            LiraTopBar("Recurring", onBack = { navController.popBackStack() }) {
                IconCircleButton(Icons.Outlined.Add, "New recurring", { navController.navigate(Routes.recurringEdit()) }, background = Color.Transparent)
            }
            val list = entries
            when {
                list == null && loadError == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = LiraColors.TextSecondary, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }
                list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(Icons.Outlined.Repeat, "Couldn't load recurring items", loadError, actionLabel = "Try again", onAction = { scope.launch { reload() } })
                }
                list.isEmpty() -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    suggestionBlock()
                    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                        EmptyState(
                            Icons.Outlined.Repeat, "Nothing recurring yet",
                            "Add rent, subscriptions or your salary once — Lira logs them for you when they're due.",
                            actionLabel = "Add recurring", onAction = { navController.navigate(Routes.recurringEdit()) }
                        )
                    }
                }
                else -> {
                    val withNext = list.map { it to Schedule.next(it, today) }
                    val payments = withNext.filter { it.first.type != TransactionRepository.TYPE_RECEIVED }
                        .sortedBy { it.second?.toEpochDay() ?: Long.MAX_VALUE }
                    val income = withNext.filter { it.first.type == TransactionRepository.TYPE_RECEIVED }
                        .sortedBy { it.second?.toEpochDay() ?: Long.MAX_VALUE }
                    val monthlyOut = payments.filter { it.second != null }.sumOf { Schedule.monthly(it.first.amount, it.first.interval) }
                    val monthlyIn = income.filter { it.second != null }.sumOf { Schedule.monthly(it.first.amount, it.first.interval) }

                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                        suggestionBlock()
                        val num = SpanStyle(fontFamily = NumberFont, fontWeight = FontWeight.SemiBold, color = LiraColors.TextPrimary, fontFeatureSettings = "tnum")
                        Text(
                            buildAnnotatedString {
                                withStyle(num) { append(Money.format(monthlyOut, code)) }
                                append(" a month in payments")
                                if (monthlyIn > 0) {
                                    append(" · ")
                                    withStyle(num) { append(Money.format(monthlyIn, code, trimZeros = true)) }
                                    append(" in")
                                }
                            },
                            style = LiraType.BodyM.copy(fontSize = 14.sp, color = LiraColors.TextSecondary),
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp)
                        )
                        if (payments.isNotEmpty()) {
                            SectionLabel("Payments · next to post", Modifier.padding(top = 8.dp))
                            GroupCard {
                                payments.forEach { (r, next) ->
                                    RecurringRow(r, next, categories, code) { r.id?.let { navController.navigate(Routes.recurringEdit(it)) } }
                                }
                            }
                        }
                        if (income.isNotEmpty()) {
                            SectionLabel("Income", Modifier.padding(top = 10.dp))
                            GroupCard {
                                income.forEach { (r, next) ->
                                    RecurringRow(r, next, categories, code) { r.id?.let { navController.navigate(Routes.recurringEdit(it)) } }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecurringRow(r: Recurring, next: LocalDate?, categories: List<CategoryRow>, code: String, onClick: () -> Unit) {
    val isIncome = r.type == TransactionRepository.TYPE_RECEIVED
    val start = runCatching { LocalDate.parse(r.start_date) }.getOrNull() ?: LocalDate.now()
    val ends = Schedule.final(start, r.interval, r.occurrences)
    val cadence = Schedule.cadence(r.interval, start) + (ends?.let { " · ends " + Dates.long(it) } ?: "")
    Row(
        Modifier.fillMaxWidth().heightIn(min = 68.dp).liraClickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isIncome) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(LiraColors.Surface3), contentAlignment = Alignment.Center) {
                Text(r.name.trim().take(1).uppercase().ifEmpty { "+" }, style = LiraType.BodyL.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold))
            }
        } else {
            val catName = categories.firstOrNull { it.id == r.category_id }?.name
            val look = CategoryStyle.look(r.category_id, catName ?: r.name)
            CategoryIconBadge(look.icon, look.color, 40.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(r.name, style = LiraType.BodyM.copy(fontWeight = FontWeight.Medium, color = if (next == null) LiraColors.TextTertiary else LiraColors.TextPrimary), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(cadence, style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextTertiary), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                buildAnnotatedString {
                    if (isIncome) withStyle(SpanStyle(color = if (next == null) LiraColors.TextTertiary else LiraColors.Lichen)) { append("+") }
                    append(Money.format(r.amount, code))
                },
                style = LiraType.AmountXs.copy(color = if (next == null) LiraColors.TextTertiary else LiraColors.TextPrimary)
            )
            Text(next?.let { Dates.relative(it) } ?: "Ended", style = LiraType.BodyS.copy(fontSize = 12.sp))
        }
    }
}

/** "Looks like a subscription" cards (max 3) plus a short confirmation after adding one. */
@Composable
private fun SubscriptionSuggestions(
    suggestions: List<SubscriptionCandidate>,
    confirmation: String?,
    error: String?,
    addingKey: String?,
    categories: List<CategoryRow>,
    onAdd: (SubscriptionCandidate) -> Unit,
    onDismiss: (SubscriptionCandidate) -> Unit
) {
    if (suggestions.isEmpty() && confirmation == null) return
    Column(
        Modifier.fillMaxWidth().animateContentSize().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (confirmation != null) {
            Row(
                Modifier.fillMaxWidth().clip(CircleShape).background(LiraColors.Surface3).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Outlined.Check, null, tint = LiraColors.Lichen, modifier = Modifier.size(18.dp))
                Text(confirmation, style = LiraType.BodyM.copy(fontSize = 14.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        suggestions.forEach { c ->
            val catName = categories.firstOrNull { it.id == c.categoryId }?.name
            val look = CategoryStyle.look(c.categoryId, catName ?: c.name)
            LiraCard {
                Text("Looks like a subscription", style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextTertiary))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CategoryIconBadge(look.icon, look.color, 40.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "${c.name} · ${Money.format(c.amount, c.currency)} every month · seen ${c.count}×",
                            style = LiraType.BodyM.copy(fontWeight = FontWeight.Medium), maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "Lira will log it for you each month · next ${Dates.short(maxOf(c.nextExpected, java.time.LocalDate.now().plusDays(1)))}",
                            style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextTertiary), maxLines = 2
                        )
                    }
                }
                if (error != null && addingKey == null) {
                    Text(error, style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextSecondary))
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Add as recurring", { onAdd(c) }, Modifier.weight(1f), loading = addingKey == c.key, enabled = addingKey == null || addingKey == c.key)
                    GhostButton("Not a subscription", { onDismiss(c) })
                }
            }
        }
    }
}
