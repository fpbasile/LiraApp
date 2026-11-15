package com.app.lira.ui.screens.more

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.app.lira.data.CurrencyPreference
import com.app.lira.data.AppPrefs
import com.app.lira.data.CsvImport
import com.app.lira.data.local.DataMode
import com.app.lira.data.local.LocalDb
import com.app.lira.data.HomeDataCache
import com.app.lira.data.model.Recurring
import com.app.lira.data.remote.SupabaseClient
import com.app.lira.data.repository.AccountRepository
import com.app.lira.data.repository.AuthRepository
import com.app.lira.data.repository.CategoryRepository
import com.app.lira.data.repository.RecurringRepository
import com.app.lira.data.repository.SpendingLimitRepository
import com.app.lira.data.repository.TransactionRepository
import com.app.lira.data.repository.TripRepository
import com.app.lira.navigation.Routes
import com.app.lira.ui.components.lira.*
import com.app.lira.ui.screens.auth.AuthFlow
import com.app.lira.ui.screens.travel.CurrencyPickerSheet
import com.app.lira.ui.theme.LiraColors
import com.app.lira.ui.theme.LiraType
import com.app.lira.util.Money
import com.app.lira.widget.LiraWidgetReceiver
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class MoreSheet { None, Account, Currency, DeleteAccount, Widget, GoOnline }

@Composable
fun MoreScreen(navController: NavHostController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val homeCode = CurrencyPreference.code

    val offline = DataMode.isLocal
    val user = remember { if (offline) null else SupabaseClient.client.auth.currentUserOrNull() }
    val email = user?.email.orEmpty()
    val displayName = remember(email) {
        email.substringBefore('@').split('.', '_', '-').firstOrNull { it.isNotBlank() }
            ?.replaceFirstChar { it.uppercase() } ?: "You"
    }
    val since = remember(user) {
        runCatching {
            LocalDate.parse(user?.createdAt.toString().take(10))
                .format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
        }.getOrNull()
    }

    var dailyLimit by remember { mutableStateOf<Double?>(HomeDataCache.dailyLimit) }
    var weeklyLimit by remember { mutableStateOf(HomeDataCache.weekLimit) }
    var monthlyLimit by remember { mutableStateOf(HomeDataCache.monthLimit) }
    var categoryCount by remember { mutableStateOf<Int?>(HomeDataCache.categories.size.takeIf { it > 0 }) }
    var recurring by remember { mutableStateOf<List<Recurring>?>(null) }
    var activeTrip by remember { mutableStateOf<String?>(null) }
    var tripsLoaded by remember { mutableStateOf(false) }
    var streak by remember { mutableStateOf(0) }

    var sheet by remember { mutableStateOf(MoreSheet.None) }
    var editingLimit by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        launch { SpendingLimitRepository.getDailyLimit().onSuccess { dailyLimit = it; HomeDataCache.dailyLimit = it } }
        launch { SpendingLimitRepository.getLimit(SpendingLimitRepository.PERIOD_WEEKLY).onSuccess { weeklyLimit = it; HomeDataCache.weekLimit = it } }
        launch { SpendingLimitRepository.getLimit(SpendingLimitRepository.PERIOD_MONTHLY).onSuccess { monthlyLimit = it; HomeDataCache.monthLimit = it } }
        launch { CategoryRepository.getCategories().onSuccess { categoryCount = it.size; MoreData.publishCategories(it) } }
        launch { RecurringRepository.getRecurring().onSuccess { recurring = it } }
        launch {
            TripRepository.getTrips().onSuccess { trips -> activeTrip = trips.firstOrNull { it.is_active }?.name }
            tripsLoaded = true
        }
        launch { TransactionRepository.getAllTransactions(400).onSuccess { streak = MoreData.streak(it) } }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            TransactionRepository.getAllTransactions(100_000).onSuccess { rows ->
                val ok = runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(MoreData.toCsv(rows, homeCode).toByteArray()) }
                }.isSuccess
                message = if (ok) "Exported ${rows.size} entries." else "Couldn't write the file."
            }.onFailure { message = it.message }
            busy = false
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            message = "Importing…"
            val text = runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                }
            }.getOrNull()
            message = if (text == null) "Couldn't read that file." else {
                CsvImport.import(text, homeCode).fold(
                    onSuccess = { r ->
                        CategoryRepository.getCategories().onSuccess { categoryCount = it.size; MoreData.publishCategories(it) }
                        TransactionRepository.getAllTransactions(400).onSuccess { streak = MoreData.streak(it) }
                        com.app.lira.navigation.HomeEvents.dataChanged()
                        r.summary()
                    },
                    onFailure = { it.message ?: "Couldn't import that file." }
                )
            }
            busy = false
        }
    }

    fun signedOut() {
        MoreData.clearLocalUserState()
        AuthFlow.startAtLogin = true
        navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
    }

    LiraScreen {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(bottom = FloatingNavHeight).navigationBarsPadding()
        ) {
            LiraTopBar("More")

            // Profile
            Row(
                Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp)).background(LiraColors.Surface1)
                    .liraClickable { sheet = if (offline) MoreSheet.GoOnline else MoreSheet.Account }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(LiraColors.Surface3), contentAlignment = Alignment.Center) {
                    Text(displayName.take(1), style = LiraType.TitleM)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(if (offline) "Offline" else displayName, style = LiraType.BodyL.copy(fontWeight = FontWeight.SemiBold))
                    val sub = if (offline) "On this phone only · nothing backed up" else listOfNotNull(
                        if (streak > 0) "$streak-day streak" else null,
                        since?.let { if (streak > 0) "since $it" else "Since $it" }
                    ).joinToString(" · ").ifEmpty { email }
                    Text(sub, style = LiraType.BodyS.copy(color = LiraColors.TextTertiary))
                }
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Account", tint = LiraColors.TextTertiary)
            }

            if (message != null) {
                MessageLine(message!!, Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp))
            }

            // Spending limits
            SectionLabel("Spending limits", Modifier.padding(top = 10.dp))
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LimitTile("Daily", dailyLimit, homeCode, Modifier.weight(1f)) { editingLimit = SpendingLimitRepository.PERIOD_DAILY }
                LimitTile("Weekly", weeklyLimit, homeCode, Modifier.weight(1f)) { editingLimit = SpendingLimitRepository.PERIOD_WEEKLY }
                LimitTile("Monthly", monthlyLimit, homeCode, Modifier.weight(1f)) { editingLimit = SpendingLimitRepository.PERIOD_MONTHLY }
            }

            // Money
            SectionLabel("Money", Modifier.padding(top = 10.dp))
            GroupCard {
                NavRow(Icons.Outlined.Payments, "Home currency", "$homeCode · ${Money.symbol(homeCode)}") { sheet = MoreSheet.Currency }
                NavRow(Icons.Outlined.Category, "Categories", categoryCount?.toString()) { navController.navigate(Routes.MANAGE_CATEGORIES) }
                val activeCount = recurring?.count { Schedule.next(it) != null }
                NavRow(Icons.Outlined.Repeat, "Recurring", activeCount?.let { if (it == 0) "None" else "$it active" }) { navController.navigate(Routes.RECURRING) }
                NavRow(Icons.Outlined.Flight, "Trips", if (!tripsLoaded) null else activeTrip ?: "None active") { navController.navigate(Routes.TRIPS) }
                NavRow(Icons.Outlined.CurrencyExchange, "Currency converter") { navController.navigate(Routes.CURRENCY_CONVERTER) }
            }

            // Account
            SectionLabel("Account", Modifier.padding(top = 10.dp))
            GroupCard {
                NavRow(Icons.Outlined.Widgets, "Home screen widget") {
                    val mgr = AppWidgetManager.getInstance(context)
                    val requested = runCatching {
                        mgr.isRequestPinAppWidgetSupported &&
                            mgr.requestPinAppWidget(ComponentName(context, LiraWidgetReceiver::class.java), null, null)
                    }.getOrDefault(false)
                    if (!requested) sheet = MoreSheet.Widget
                }
                NavRow(Icons.Outlined.FileDownload, "Export entries (CSV)") {
                    if (!busy) exportLauncher.launch("lira-entries-${LocalDate.now()}.csv")
                }
                NavRow(Icons.Outlined.FileUpload, "Import entries (CSV)") {
                    if (!busy) importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/vnd.ms-excel", "*/*"))
                }
                if (offline) {
                    NavRow(Icons.Outlined.CloudUpload, "Back up with an account") { sheet = MoreSheet.GoOnline }
                } else NavRow(Icons.AutoMirrored.Outlined.Logout, "Log out", chevron = false) {
                    if (busy) return@NavRow
                    scope.launch {
                        busy = true
                        AuthRepository.signOut()
                            .onSuccess { signedOut() }
                            .onFailure { message = it.message ?: "Couldn't log out." }
                        busy = false
                    }
                }
                NavRow(Icons.Outlined.DeleteOutline, if (offline) "Delete all data" else "Delete account", chevron = false, titleColor = LiraColors.TextSecondary) {
                    deleteError = null
                    sheet = MoreSheet.DeleteAccount
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    // ── Sheets ──
    editingLimit?.let { period ->
        val current = when (period) {
            SpendingLimitRepository.PERIOD_DAILY -> dailyLimit
            SpendingLimitRepository.PERIOD_WEEKLY -> weeklyLimit
            else -> monthlyLimit
        }
        LimitSheet(
            period = period,
            current = current,
            currencyCode = homeCode,
            onDismiss = { editingLimit = null },
            onSaved = { value ->
                when (period) {
                    SpendingLimitRepository.PERIOD_DAILY -> { dailyLimit = value; HomeDataCache.dailyLimit = value }
                    SpendingLimitRepository.PERIOD_WEEKLY -> { weeklyLimit = value; HomeDataCache.weekLimit = value }
                    else -> { monthlyLimit = value; HomeDataCache.monthLimit = value }
                }
                editingLimit = null
            }
        )
    }

    when (sheet) {
        MoreSheet.Currency -> CurrencyPickerSheet(
            selected = homeCode,
            onSelect = { CurrencyPreference.select(it); sheet = MoreSheet.None },
            onDismiss = { sheet = MoreSheet.None }
        )
        MoreSheet.Account -> AccountSheet(email = email, onDismiss = { sheet = MoreSheet.None })
        MoreSheet.GoOnline -> ConfirmSheet(
            title = "Back up with an account",
            body = "Create an account or log in. Your offline entries are copied into it, then removed from this phone's offline storage. Trips, recurring items and limits are set up again in the account.",
            confirmLabel = "Continue",
            loading = false,
            error = null,
            onConfirm = {
                sheet = MoreSheet.None
                AppPrefs.migrateOffline = true
                AppPrefs.offlineMode = false
                MoreData.clearLocalUserState()
                navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
            },
            onDismiss = { sheet = MoreSheet.None }
        )
        MoreSheet.DeleteAccount -> ConfirmSheet(
            title = if (offline) "Delete all data?" else "Delete account?",
            body = if (offline) "This permanently deletes every entry, trip, recurring item and limit stored on this phone. Nothing is backed up, so it can't be undone."
            else "This permanently deletes all your entries, recurring items and limits, then logs you out. It can't be undone.",
            confirmLabel = "Delete my data",
            loading = busy,
            error = deleteError,
            onConfirm = {
                scope.launch {
                    busy = true
                    deleteError = null
                    AccountRepository.deleteAccountData()
                        .onSuccess {
                            sheet = MoreSheet.None
                            if (offline) {
                                AppPrefs.offlineMode = false
                                MoreData.clearLocalUserState()
                                navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
                            } else signedOut()
                        }
                        .onFailure { deleteError = it.message ?: "Couldn't delete your data." }
                    busy = false
                }
            },
            onDismiss = { sheet = MoreSheet.None }
        )
        MoreSheet.Widget -> LiraSheet(onDismiss = { sheet = MoreSheet.None }, title = "Home screen widget") {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Touch and hold an empty spot on your home screen, tap Widgets, then find Lira. It shows what's left today and logs in one tap.",
                    style = LiraType.BodyM.copy(color = LiraColors.TextSecondary)
                )
                PrimaryButton("Got it", { sheet = MoreSheet.None }, Modifier.fillMaxWidth())
            }
        }
        MoreSheet.None -> Unit
    }
}

@Composable
private fun LimitTile(label: String, value: Double?, code: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(LiraColors.Surface2).liraClickable(onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextTertiary))
        if (value != null) Text(Money.format(value, code, trimZeros = true), style = LiraType.AmountS, maxLines = 1)
        else Text("Not set", style = LiraType.AmountS.copy(color = LiraColors.TextTertiary, fontSize = 18.sp), maxLines = 1)
    }
}

/** Limit editor: quick-add style amount + keypad in a sheet. */
@Composable
private fun LimitSheet(period: String, current: Double?, currencyCode: String, onDismiss: () -> Unit, onSaved: (Double) -> Unit) {
    val scope = rememberCoroutineScope()
    val digits = Money.minorDigits(currencyCode)
    var raw by remember { mutableStateOf(current?.let { rawFromAmount(it, digits) } ?: "") }
    var fresh by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val value = raw.toDoubleOrNull() ?: 0.0
    val word = when (period) {
        SpendingLimitRepository.PERIOD_DAILY -> "day"
        SpendingLimitRepository.PERIOD_WEEKLY -> "week"
        else -> "month"
    }
    val title = period.replaceFirstChar { it.uppercase() } + " limit"

    LiraSheet(onDismiss = { if (!saving) onDismiss() }, title = title) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AmountDisplay(raw, currencyCode, maxSize = 72)
            AmountCaption(
                if (period == SpendingLimitRepository.PERIOD_DAILY) "Home shows what's left of it every time you open Lira."
                else "What you're happy to spend in a $word."
            )
            if (error != null) MessageLine(error!!, Modifier.padding(horizontal = 20.dp))
        }
        Keypad(
            onKey = { key ->
                error = null
                raw = if (fresh && key !is KeypadKey.Delete) applyKey("", key, digits) else applyKey(raw, key, digits)
                fresh = false
            },
            topAction = { m -> TallKey(Icons.Outlined.Close, "Cancel", m, onClick = { if (!saving) onDismiss() }) },
            bottomAction = { m ->
                SaveKey(m, ready = value > 0 && !saving) {
                    scope.launch {
                        saving = true
                        SpendingLimitRepository.setLimit(period, value)
                            .onSuccess { onSaved(value) }
                            .onFailure { error = it.message ?: "Couldn't save the limit." }
                        saving = false
                    }
                }
            },
            modifier = Modifier.padding(bottom = 8.dp),
            showDecimal = digits > 0
        )
    }
}

private enum class AccountMode { Menu, Email, Password }

/** Account: who's signed in, change email, change password. */
@Composable
private fun AccountSheet(email: String, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(AccountMode.Menu) }
    var newEmail by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf<String?>(null) }

    LiraSheet(onDismiss = { if (!saving) onDismiss() }, title = when (mode) {
        AccountMode.Menu -> "Account"
        AccountMode.Email -> "Change email"
        AccountMode.Password -> "Change password"
    }) {
        Column(Modifier.fillMaxWidth().imePadding().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (mode) {
                AccountMode.Menu -> {
                    ListRow(
                        title = email.ifBlank { "Unknown" },
                        subtitle = "Signed in as",
                        leading = { Icon(Icons.Outlined.Mail, null, tint = LiraColors.TextSecondary, modifier = Modifier.size(20.dp)) }
                    )
                    if (done != null) MessageLine(done!!, Modifier.padding(horizontal = 20.dp), positive = true)
                    GroupCard(Modifier.padding(horizontal = 0.dp)) {
                        NavRow(Icons.Outlined.Mail, "Change email") { error = null; done = null; mode = AccountMode.Email }
                        NavRow(Icons.Outlined.Lock, "Change password") { error = null; done = null; mode = AccountMode.Password }
                    }
                }
                AccountMode.Email -> Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LiraTextField(
                        value = newEmail, onValueChange = { newEmail = it; error = null },
                        placeholder = "new@example.com", label = "New email",
                        leadingIcon = Icons.Outlined.Mail, keyboardType = KeyboardType.Email
                    )
                    Text("We'll send a confirmation link. The change only takes effect once you open it.", style = LiraType.BodyS)
                    if (error != null) MessageLine(error!!)
                    PrimaryButton("Send confirmation", {
                        val e = newEmail.trim()
                        if (e.isBlank() || !e.contains('@') || !e.contains('.')) { error = "Enter a valid email address."; return@PrimaryButton }
                        scope.launch {
                            saving = true
                            AuthRepository.updateEmail(e)
                                .onSuccess { newEmail = ""; done = "Check $e to confirm the change."; mode = AccountMode.Menu }
                                .onFailure { error = it.message ?: "Couldn't update your email." }
                            saving = false
                        }
                    }, Modifier.fillMaxWidth(), loading = saving)
                    GhostButton("Back", { mode = AccountMode.Menu }, Modifier.fillMaxWidth())
                }
                AccountMode.Password -> Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LiraTextField(
                        value = newPassword, onValueChange = { newPassword = it; error = null },
                        placeholder = "At least 6 characters", label = "New password",
                        leadingIcon = Icons.Outlined.Lock, password = true
                    )
                    LiraTextField(
                        value = confirmPassword, onValueChange = { confirmPassword = it; error = null },
                        placeholder = "Type it again", label = "Confirm password",
                        leadingIcon = Icons.Outlined.Lock, password = true
                    )
                    if (error != null) MessageLine(error!!)
                    PrimaryButton("Update password", {
                        when {
                            newPassword.length < 6 -> error = "Use at least 6 characters."
                            newPassword != confirmPassword -> error = "Those passwords don't match."
                            else -> scope.launch {
                                saving = true
                                AuthRepository.updatePassword(newPassword)
                                    .onSuccess { newPassword = ""; confirmPassword = ""; done = "Password updated."; mode = AccountMode.Menu }
                                    .onFailure { error = it.message ?: "Couldn't update your password." }
                                saving = false
                            }
                        }
                    }, Modifier.fillMaxWidth(), loading = saving)
                    GhostButton("Back", { mode = AccountMode.Menu }, Modifier.fillMaxWidth())
                }
            }
        }
    }
}
