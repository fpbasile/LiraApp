package com.app.lira.ui.screens.travel

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.lira.data.AppPrefs
import com.app.lira.data.CurrencyPreference
import com.app.lira.data.repository.CurrencyConverterRepository
import com.app.lira.ui.components.lira.IconCircleButton
import com.app.lira.ui.components.lira.liraClickable
import com.app.lira.ui.theme.LiraColors
import com.app.lira.ui.theme.LiraType
import com.app.lira.ui.theme.NumberFont
import com.app.lira.util.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Every currency the app knows about: the ~380 codes from the live currency API, with names
 * filled in from the platform where the API has none. Works offline from the JDK's ISO list.
 */
object CurrencyCatalog {
    @Volatile private var remote: Map<String, String>? = null
    @Volatile private var countryIndex: Map<String, List<String>>? = null

    /** ISO 4217 codes the platform knows, code → localized name. Always available. */
    val offline: Map<String, String> by lazy {
        java.util.Currency.getAvailableCurrencies()
            .associate { it.currencyCode to it.getDisplayName(Locale.getDefault()) }
            .toSortedMap()
    }

    /** Best list available right now, without waiting on the network. */
    fun current(): Map<String, String> = remote ?: offline

    /** Loads the full list once (cached for the process), falling back to [offline]. */
    suspend fun load(): Map<String, String> {
        remote?.let { return it }
        val fetched = CurrencyConverterRepository.getSupportedCurrencies().getOrNull()
        if (fetched.isNullOrEmpty()) return offline
        val merged = (offline.keys + fetched.keys).associateWith { code ->
            offline[code]?.takeIf { it.isNotBlank() && it != code }
                ?: fetched[code]?.takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() }
                ?: code
        }.toSortedMap()
        remote = merged
        return merged
    }

    /** Display name, e.g. "Japanese Yen". */
    fun name(code: String): String =
        current()[code.uppercase()] ?: runCatching {
            java.util.Currency.getInstance(code.uppercase()).getDisplayName(Locale.getDefault())
        }.getOrDefault(code.uppercase())

    /** The symbol if it differs from the code ("¥"), else null. */
    fun symbolOrNull(code: String): String? = Money.symbol(code).takeIf { !it.equals(code, ignoreCase = true) }

    /** Short glyph for round badges: symbol when there is one, else the code. */
    fun glyph(code: String): String = symbolOrNull(code) ?: code.uppercase()

    /** Currency code → country names that use it (for "Name, code or country" search). */
    suspend fun countries(): Map<String, List<String>> {
        countryIndex?.let { return it }
        val built = withContext(Dispatchers.Default) {
            val display = Locale.getDefault()
            Locale.getAvailableLocales()
                .filter { it.country.length == 2 }
                .mapNotNull { loc ->
                    runCatching { java.util.Currency.getInstance(loc)?.currencyCode }.getOrNull()
                        ?.let { it to loc.getDisplayCountry(display) }
                }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, v) -> v.filter { it.isNotBlank() }.distinct() }
        }
        countryIndex = built
        return built
    }
}

/** Pinned currencies shown first in the picker. Falls back to home + a few sensible ones until edited. */
private fun pinnedFor(selected: String): List<String> {
    val stored = AppPrefs.pinnedCurrencies
    if (stored.isNotEmpty()) return stored
    return listOf(CurrencyPreference.code, selected.uppercase(), "USD", "EUR").filter { it.isNotBlank() }.distinct().take(4)
}

private sealed interface PickerItem {
    val key: String
    data class Header(val text: String, val hint: String? = null, override val key: String) : PickerItem
    data class Row(val code: String, val name: String, override val key: String) : PickerItem
}

/**
 * Full-height currency picker (Currency-Picker board): search by name, code or country,
 * pinned currencies first (long-press any row to pin or unpin), then every currency A–Z with a
 * letter rail. Selecting calls [onSelect] then [onDismiss] once the sheet has slid away, so
 * callers only need to flip their visibility flag in [onDismiss].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CurrencyPickerSheet(selected: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var all by remember { mutableStateOf(CurrencyCatalog.current()) }
    var countries by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var pinned by remember { mutableStateOf(pinnedFor(selected)) }
    var query by remember { mutableStateOf("") }
    val selectedCode = selected.uppercase()

    LaunchedEffect(Unit) { all = CurrencyCatalog.load() }
    LaunchedEffect(Unit) { countries = CurrencyCatalog.countries() }

    fun togglePin(code: String) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val next = if (code in pinned) pinned - code else pinned + code
        pinned = next
        AppPrefs.pinnedCurrencies = next
    }

    fun choose(code: String) {
        onSelect(code)
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    val rows: List<PickerItem> = remember(all, pinned, query, countries) {
        val q = query.trim()
        if (q.isEmpty()) {
            buildList {
                if (pinned.isNotEmpty()) {
                    add(PickerItem.Header("Pinned", "Long-press to pin", "h_pinned"))
                    pinned.forEach { add(PickerItem.Row(it, all[it] ?: CurrencyCatalog.name(it), "p_$it")) }
                }
                add(PickerItem.Header("All currencies · ${all.size}", key = "h_all"))
                all.forEach { (code, name) -> add(PickerItem.Row(code, name, "a_$code")) }
            }
        } else {
            val matches = all.entries.filter { (code, name) ->
                code.contains(q, ignoreCase = true) || name.contains(q, ignoreCase = true) ||
                    countries[code].orEmpty().any { it.contains(q, ignoreCase = true) }
            }.sortedWith(
                compareBy<Map.Entry<String, String>>(
                    { !it.key.equals(q, ignoreCase = true) },
                    { !it.key.startsWith(q, ignoreCase = true) },
                    { !it.value.startsWith(q, ignoreCase = true) },
                    { it.key }
                )
            )
            buildList {
                add(PickerItem.Header(if (matches.isEmpty()) "No matches" else "Results · ${matches.size}", key = "h_results"))
                matches.forEach { add(PickerItem.Row(it.key, it.value, "r_${it.key}")) }
            }
        }
    }

    val letters: List<Pair<Char, Int>> = remember(rows) {
        if (query.isNotBlank()) emptyList()
        else {
            val firstAll = rows.indexOfFirst { it.key == "h_all" }
            rows.withIndex()
                .filter { it.index > firstAll && it.value is PickerItem.Row }
                .map { (it.value as PickerItem.Row).code.first() to it.index }
                .distinctBy { it.first }
        }
    }

    val listState = rememberLazyListState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = LiraColors.Surface1,
        scrimColor = LiraColors.Scrim,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(36.dp, 4.dp).clip(CircleShape).background(LiraColors.TextDisabled)) }
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight()) {
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Choose currency", style = LiraType.TitleM, modifier = Modifier.weight(1f))
                IconCircleButton(Icons.Outlined.Close, "Close", onClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                }, background = Color.Transparent, tint = LiraColors.TextSecondary)
            }
            SearchField(query, { query = it }, Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp))

            Box(Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(rows, key = { it.key }) { item ->
                        when (item) {
                            is PickerItem.Header -> Row(
                                Modifier.fillMaxWidth().padding(start = 20.dp, end = 32.dp, top = 14.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(item.text.uppercase(), style = LiraType.Caption, modifier = Modifier.weight(1f))
                                if (item.hint != null) Text(item.hint, style = LiraType.BodyS.copy(fontSize = 11.sp, color = LiraColors.TextTertiary))
                            }
                            is PickerItem.Row -> CurrencyRow(
                                code = item.code,
                                name = item.name,
                                selected = item.code == selectedCode,
                                pinned = item.code in pinned,
                                onClick = { choose(item.code) },
                                onLongClick = { togglePin(item.code) }
                            )
                        }
                    }
                }
                if (letters.size > 1) {
                    LetterRail(
                        letters = letters.map { it.first },
                        onLetter = { c ->
                            val idx = letters.firstOrNull { it.first == c }?.second ?: return@LetterRail
                            scope.launch { listState.scrollToItem(idx) }
                        },
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)
                    )
                }
            }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = LiraType.BodyM,
        cursorBrush = SolidColor(LiraColors.TextPrimary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(LiraColors.Surface2)
                    .then(if (focused) Modifier.border(1.dp, LiraColors.TextPrimary, RoundedCornerShape(24.dp)) else Modifier)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Outlined.Search, null, tint = LiraColors.TextTertiary, modifier = Modifier.size(18.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text("Name, code or country", style = LiraType.BodyM.copy(color = LiraColors.TextTertiary))
                    inner()
                }
                if (value.isNotEmpty()) {
                    Icon(
                        Icons.Outlined.Close, "Clear search", tint = LiraColors.TextSecondary,
                        modifier = Modifier.size(18.dp).liraClickable { onChange("") }
                    )
                }
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CurrencyRow(
    code: String,
    name: String,
    selected: Boolean,
    pinned: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().height(56.dp)
            .combinedClickable(
                interactionSource = source,
                indication = null,
                role = Role.Button,
                onLongClickLabel = if (pinned) "Unpin" else "Pin",
                onLongClick = onLongClick,
                onClick = onClick
            )
            .semantics { contentDescription = "$code, $name" + if (selected) ", selected" else "" }
            .padding(start = 20.dp, end = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            com.app.lira.util.Flags.forCurrency(code) ?: "",
            style = LiraType.BodyL.copy(fontSize = 22.sp),
            modifier = Modifier.width(30.dp), maxLines = 1
        )
        Text(
            code, style = LiraType.BodyM.copy(fontFamily = NumberFont, fontWeight = FontWeight.SemiBold),
            modifier = Modifier.width(48.dp), maxLines = 1
        )
        Text(name, style = LiraType.BodyM, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        CurrencyCatalog.symbolOrNull(code)?.let {
            Text(it, style = LiraType.BodyS.copy(fontSize = 14.sp, color = LiraColors.TextTertiary), maxLines = 1)
        }
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            when {
                selected -> Icon(Icons.Outlined.Check, null, tint = LiraColors.TextPrimary, modifier = Modifier.size(20.dp))
                pinned -> Icon(Icons.Outlined.PushPin, null, tint = LiraColors.TextDisabled, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun LetterRail(letters: List<Char>, onLetter: (Char) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        letters.forEach { c ->
            Box(
                Modifier.size(width = 20.dp, height = 16.dp).liraClickable { onLetter(c) },
                contentAlignment = Alignment.Center
            ) {
                Text(c.toString(), style = LiraType.Caption.copy(fontSize = 10.sp, letterSpacing = 0.sp, color = LiraColors.TextTertiary))
            }
        }
    }
}
