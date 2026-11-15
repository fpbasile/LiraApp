package com.app.lira.ui.screens.travel

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.app.lira.data.CurrencyPreference
import com.app.lira.data.repository.TripRepository
import com.app.lira.ui.components.lira.IconCircleButton
import com.app.lira.ui.components.lira.LiraScreen
import com.app.lira.ui.components.lira.PrimaryButton
import com.app.lira.ui.theme.LiraColors
import com.app.lira.ui.theme.LiraType
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

/**
 * Start a trip (Trip-Start board): name, currencies, dates, optional budget, home default.
 * Starting makes it the active trip (any other active trip ends) and returns to where the user
 * came from — Home then runs in travel mode.
 */
@Composable
fun TripStartScreen(navController: NavHostController) {
    val context = LocalContext.current
    remember { TripLocalStore.init(context) }
    val scope = rememberCoroutineScope()
    val home = CurrencyPreference.code
    val form = remember { TripFormState() }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun start() {
        form.validate()?.let { error = it; return }
        error = null
        saving = true
        scope.launch {
            TripRepository.addTrip(form.name.trim(), form.currencies)
                .onSuccess { trip ->
                    val id = trip.id
                    if (id != null) {
                        TripLocalStore.init(context)
                        TripLocalStore.set(id, form.extras())
                        TripRepository.setActiveTrip(id)
                    }
                    saving = false
                    navController.popBackStack()
                }
                .onFailure { e -> saving = false; error = e.message ?: "Couldn't start the trip" }
        }
    }

    LiraScreen {
        Column(Modifier.fillMaxSize().imePadding()) {
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Start a trip", style = LiraType.TitleM.copy(fontSize = 22.sp), modifier = Modifier.weight(1f))
                IconCircleButton(Icons.Outlined.Close, "Close", { navController.popBackStack() }, background = Color.Transparent, tint = LiraColors.TextSecondary)
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                TripForm(form, home)
                if (error != null) Text(error!!, style = LiraType.BodyS.copy(color = LiraColors.TextPrimary))
                PrimaryButton("Start trip", ::start, Modifier.fillMaxWidth(), loading = saving)
                val end = form.end
                Text(
                    if (end != null) "Ends on its own after ${end.format(DateTimeFormatter.ofPattern("d MMMM"))}. You can end it early anytime."
                    else "You can end it anytime from the trip.",
                    style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextTertiary),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}
