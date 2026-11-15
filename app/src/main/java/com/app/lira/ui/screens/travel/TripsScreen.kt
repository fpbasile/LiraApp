package com.app.lira.ui.screens.travel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.app.lira.data.model.Trip
import com.app.lira.data.repository.TripRepository
import com.app.lira.navigation.Routes
import com.app.lira.ui.components.lira.EmptyState
import com.app.lira.ui.components.lira.IconCircleButton
import com.app.lira.ui.components.lira.LiraCard
import com.app.lira.ui.components.lira.LiraScreen
import com.app.lira.ui.components.lira.LiraSheet
import com.app.lira.ui.components.lira.LiraTopBar
import com.app.lira.ui.components.lira.ListRow
import com.app.lira.ui.components.lira.SecondaryButton
import com.app.lira.ui.components.lira.SectionLabel
import com.app.lira.ui.theme.CategoryPalette
import com.app.lira.ui.theme.LiraColors
import com.app.lira.ui.theme.LiraType
import com.app.lira.ui.theme.NumberFont
import kotlinx.coroutines.launch

/** Travel tint (the Stays hue) — marks the active trip. */
internal val TravelTint = CategoryPalette.Stays

/** Trips list (More › Trips): start, open, activate, end and delete trips. Empty-Trips board when none. */
@Composable
fun TripsScreen(navController: NavHostController) {
    val context = LocalContext.current
    remember { TripLocalStore.init(context) }
    val scope = rememberCoroutineScope()

    var trips by remember { mutableStateOf<List<Trip>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var actionsFor by remember { mutableStateOf<Trip?>(null) }
    var deleting by remember { mutableStateOf<Trip?>(null) }
    var deleteBusy by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        TripRepository.getTrips()
            .onSuccess { list ->
                trips = list
                error = null
                if (TripMode.autoEndExpired(list)) TripRepository.getTrips().onSuccess { trips = it }
            }
            .onFailure { e -> error = e.message }
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }

    fun setActive(trip: Trip, active: Boolean) {
        scope.launch {
            TripRepository.setActiveTrip(if (active) trip.id else null).onFailure { e -> error = e.message }
            refresh()
        }
    }

    val active = trips.firstOrNull { it.is_active }
    val others = trips.filter { !it.is_active }

    LiraScreen {
        Column(Modifier.fillMaxSize()) {
            LiraTopBar("Trips", onBack = { navController.popBackStack() }) {
                if (trips.isNotEmpty()) {
                    IconCircleButton(Icons.Outlined.Add, "Start a trip", { navController.navigate(Routes.TRIP_START) }, background = Color.Transparent)
                }
            }
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = LiraColors.TextSecondary, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                }
                trips.isEmpty() && error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        Icons.Outlined.Luggage, "Couldn't load your trips", error,
                        actionLabel = "Try again", onAction = { loading = true; scope.launch { refresh() } }
                    )
                }
                trips.isEmpty() -> Box(Modifier.fillMaxSize().padding(bottom = 56.dp), contentAlignment = Alignment.Center) {
                    EmptyState(
                        Icons.Outlined.Luggage,
                        "No trips yet",
                        "Start one when you land — Lira switches to the local currency for you.",
                        actionLabel = "Start a trip",
                        onAction = { navController.navigate(Routes.TRIP_START) }
                    )
                }
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                    if (error != null) item {
                        Text(error!!, style = LiraType.BodyS, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                    }
                    if (active != null) item(key = "active") {
                        ActiveTripCard(
                            active,
                            onOpen = { active.id?.let { navController.navigate(Routes.tripDetail(it)) } },
                            onEnd = { setActive(active, false) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    if (others.isNotEmpty()) {
                        item(key = "h_others") { SectionLabel(if (active != null) "Other trips" else "Your trips", Modifier.padding(top = 8.dp)) }
                        items(others, key = { it.id ?: it.name.hashCode() }) { trip ->
                            TripRow(
                                trip,
                                onOpen = { trip.id?.let { navController.navigate(Routes.tripDetail(it)) } },
                                onMore = { actionsFor = trip }
                            )
                        }
                    }
                    item(key = "tools") {
                        SectionLabel("Tools", Modifier.padding(top = 16.dp))
                        ListRow(
                            "Currency converter",
                            subtitle = "Live rates for 380 currencies",
                            leading = { RoundGlyph(icon = { Icon(Icons.Outlined.CurrencyExchange, null, tint = LiraColors.TextSecondary, modifier = Modifier.size(18.dp)) }) },
                            trailing = { Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = LiraColors.TextTertiary, modifier = Modifier.size(18.dp)) },
                            onClick = { navController.navigate(Routes.CURRENCY_CONVERTER) }
                        )
                    }
                }
            }
        }
    }

    actionsFor?.let { trip ->
        LiraSheet(onDismiss = { actionsFor = null }, title = com.app.lira.util.Flags.tripLabel(trip.name)) {
            ListRow(
                "Open",
                leading = { Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = LiraColors.TextSecondary) },
                onClick = { actionsFor = null; trip.id?.let { navController.navigate(Routes.tripDetail(it)) } }
            )
            ListRow(
                if (trip.is_active) "End trip" else "Make active",
                subtitle = if (trip.is_active) null else "Home switches to ${trip.currencies.firstOrNull() ?: "its currency"}",
                leading = { Icon(if (trip.is_active) Icons.Outlined.Stop else Icons.Outlined.PlayArrow, null, tint = LiraColors.TextSecondary) },
                onClick = { actionsFor = null; setActive(trip, !trip.is_active) }
            )
            ListRow(
                "Delete trip",
                subtitle = "Entries stay in your history",
                leading = { Icon(Icons.Outlined.DeleteOutline, null, tint = LiraColors.TextSecondary) },
                onClick = { actionsFor = null; deleting = trip; deleteError = null }
            )
        }
    }

    deleting?.let { trip ->
        ConfirmSheet(
            title = "Delete “${trip.name}”?",
            body = "The trip goes away. Its entries stay in your history, just unlinked from it.",
            confirmLabel = "Delete trip",
            loading = deleteBusy,
            error = deleteError,
            onDismiss = { if (!deleteBusy) deleting = null },
            onConfirm = {
                val id = trip.id ?: return@ConfirmSheet
                deleteBusy = true
                scope.launch {
                    TripRepository.deleteTrip(id)
                        .onSuccess { TripLocalStore.remove(id); deleting = null; refresh() }
                        .onFailure { e -> deleteError = e.message }
                    deleteBusy = false
                }
            }
        )
    }
}

@Composable
private fun ActiveTripCard(trip: Trip, onOpen: () -> Unit, onEnd: () -> Unit, modifier: Modifier = Modifier) {
    val extras = TripLocalStore.get(trip.id)
    LiraCard(modifier, onClick = onOpen, padding = PaddingValues(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.FlightTakeoff, null, tint = TravelTint, modifier = Modifier.size(16.dp))
            Text(activeLabel(trip), style = LiraType.Label.copy(fontWeight = FontWeight.SemiBold, color = TravelTint))
        }
        Text(com.app.lira.util.Flags.tripLabel(trip.name), style = LiraType.TitleM, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            listOfNotNull(formatTripDates(extras.start ?: TripMode.startDate(trip), extras.end), trip.currencies.joinToString(", ").ifEmpty { null })
                .joinToString(" · "),
            style = LiraType.BodyS.copy(color = LiraColors.TextTertiary)
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Open trip", onOpen, Modifier.weight(1f))
            SecondaryButton("End trip", onEnd, Modifier.weight(1f), textColor = LiraColors.TextSecondary)
        }
    }
}

internal fun activeLabel(trip: Trip): String {
    val day = TripMode.dayOf(trip)
    return when {
        day == null -> "Active trip"
        day.second != null -> "Active trip · Day ${day.first} of ${day.second}"
        else -> "Active trip · Day ${day.first}"
    }
}

@Composable
private fun TripRow(trip: Trip, onOpen: () -> Unit, onMore: () -> Unit) {
    val extras = TripLocalStore.get(trip.id)
    ListRow(
        trip.name,
        subtitle = listOfNotNull(
            formatTripDates(extras.start ?: TripMode.startDate(trip), extras.end),
            trip.currencies.joinToString(", ").ifEmpty { null }
        ).joinToString(" · "),
        leading = {
            val flag = com.app.lira.util.Flags.countriesIn(trip.name).firstOrNull()?.let { com.app.lira.util.Flags.emoji(it) }
            if (flag != null) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(LiraColors.Surface3), contentAlignment = Alignment.Center) {
                    Text(flag, style = LiraType.BodyL.copy(fontSize = 20.sp, lineHeight = 24.sp))
                }
            } else RoundGlyph(text = trip.currencies.firstOrNull()?.let { CurrencyCatalog.glyph(it) } ?: "\u00B7")
        },
        trailing = { IconCircleButton(Icons.Outlined.MoreVert, "Trip actions", onMore, background = Color.Transparent, tint = LiraColors.TextSecondary, size = 40.dp) },
        onClick = onOpen
    )
}

/** 36dp surface/3 circle holding a currency glyph or an icon. */
@Composable
internal fun RoundGlyph(text: String? = null, size: androidx.compose.ui.unit.Dp = 36.dp, icon: (@Composable () -> Unit)? = null) {
    Box(Modifier.size(size).clip(CircleShape).background(LiraColors.Surface3), contentAlignment = Alignment.Center) {
        if (icon != null) icon()
        else Text(
            text.orEmpty(),
            style = LiraType.BodyM.copy(fontFamily = NumberFont, fontWeight = FontWeight.SemiBold, fontSize = if ((text?.length ?: 0) > 2) 10.sp else 15.sp),
            maxLines = 1
        )
    }
}
