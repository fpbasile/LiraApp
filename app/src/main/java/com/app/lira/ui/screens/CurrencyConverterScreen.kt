package com.app.lira.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.app.lira.data.repository.CurrencyConverterRepository
import com.app.lira.data.repository.ExchangeRateResult
import com.app.lira.ui.components.LiraTextField
import com.app.lira.ui.theme.*
import kotlinx.coroutines.delay

private val ErrorColor = Color(0xFFE53935)

@Composable
fun CurrencyConverterScreen(navController: NavController) {
    var currencies by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isLoadingCurrencies by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    var amount by remember { mutableStateOf(TextFieldValue("1")) }
    var fromCode by remember { mutableStateOf("EUR") }
    var toCode by remember { mutableStateOf("USD") }
    var result by remember { mutableStateOf<ExchangeRateResult?>(null) }
    var isConverting by remember { mutableStateOf(false) }
    var convertError by remember { mutableStateOf<String?>(null) }

    var pickerFor by remember { mutableStateOf<CurrencyField?>(null) }

    LaunchedEffect(Unit) {
        isLoadingCurrencies = true
        CurrencyConverterRepository.getSupportedCurrencies()
            .onSuccess { currencies = it }
            .onFailure { e -> loadError = e.message }
        isLoadingCurrencies = false
    }

    LaunchedEffect(amount.text, fromCode, toCode) {
        val parsed = amount.text.toDoubleOrNull()
        if (parsed == null || parsed <= 0.0) {
            result = null
            convertError = null
            return@LaunchedEffect
        }
        delay(400) // debounce so we don't fire a request on every keystroke
        isConverting = true
        convertError = null
        CurrencyConverterRepository.convert(parsed, fromCode, toCode)
            .onSuccess { result = it }
            .onFailure { e -> convertError = e.message; result = null }
        isConverting = false
    }

    pickerFor?.let { field ->
        CurrencyPickerDialog(
            currencies = currencies,
            selected = if (field == CurrencyField.FROM) fromCode else toCode,
            onDismiss = { pickerFor = null },
            onSelect = { code ->
                if (field == CurrencyField.FROM) fromCode = code else toCode = code
                pickerFor = null
            }
        )
    }

    Scaffold(containerColor = LiraBackground) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
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
                    text = "Currency Converter",
                    color = LiraTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            Spacer(Modifier.height(28.dp))

            when {
                isLoadingCurrencies -> Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = LiraTextPrimary)
                }
                loadError != null -> Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text(loadError ?: "", color = ErrorColor, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
                else -> {
                    AmountCard(
                        label = "Amount",
                        amount = amount,
                        onAmountChange = { amount = it },
                        currencyCode = fromCode,
                        currencyName = currencies[fromCode] ?: "",
                        editable = true,
                        onCurrencyClick = { pickerFor = CurrencyField.FROM }
                    )

                    Spacer(Modifier.height(12.dp))

                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(LiraBorder.copy(alpha = 0.4f))
                                .clickable {
                                    val temp = fromCode
                                    fromCode = toCode
                                    toCode = temp
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("⇅", color = LiraTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    AmountCard(
                        label = "Converted",
                        amount = TextFieldValue(
                            if (isConverting) "…" else result?.rates?.get(toCode)?.let { String.format("%.2f", it) } ?: "—"
                        ),
                        onAmountChange = {},
                        currencyCode = toCode,
                        currencyName = currencies[toCode] ?: "",
                        editable = false,
                        onCurrencyClick = { pickerFor = CurrencyField.TO }
                    )

                    Spacer(Modifier.height(16.dp))

                    convertError?.let {
                        Text(it, color = ErrorColor, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                    }

                    result?.let { r ->
                        val rate = r.rates[toCode]
                        if (rate != null && r.amount > 0) {
                            Text(
                                text = "1 $fromCode = ${String.format("%.4f", rate / r.amount)} $toCode",
                                color = LiraTextSecondary,
                                fontSize = 13.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Rates as of ${r.date} · European Central Bank",
                                color = LiraTextSecondary.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

internal enum class CurrencyField { FROM, TO }

@Composable
private fun AmountCard(
    label: String,
    amount: TextFieldValue,
    onAmountChange: (TextFieldValue) -> Unit,
    currencyCode: String,
    currencyName: String,
    editable: Boolean,
    onCurrencyClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, color = LiraTextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (editable) {
                LiraTextField(
                    value = amount,
                    onValueChange = onAmountChange,
                    placeholder = "0.00",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    fontSize = 22.sp
                )
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .border(1.dp, LiraBorder, RoundedCornerShape(25.dp))
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(amount.text, color = LiraTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }

            Box(
                modifier = Modifier
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(25.dp))
                    .background(LiraBorder.copy(alpha = 0.4f))
                    .clickable { onCurrencyClick() }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(currencyCode, color = LiraTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (currencyName.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(currencyName, color = LiraTextSecondary.copy(alpha = 0.7f), fontSize = 12.sp)
        }
    }
}

@Composable
internal fun CurrencyPickerDialog(
    currencies: Map<String, String>,
    selected: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    var query by remember { mutableStateOf(TextFieldValue("")) }
    val filtered = remember(currencies, query.text) {
        currencies.entries
            .filter {
                query.text.isBlank() ||
                    it.key.contains(query.text, ignoreCase = true) ||
                    it.value.contains(query.text, ignoreCase = true)
            }
            .sortedBy { it.key }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LiraBackground,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text("Select Currency", color = LiraTextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column {
                LiraTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search"
                )
                Spacer(Modifier.height(12.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(filtered, key = { it.key }) { (code, name) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(code) }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(code, color = LiraTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(name, color = LiraTextSecondary, fontSize = 12.sp)
                            }
                            if (code == selected) {
                                Text("✓", color = LiraTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = LiraTextSecondary)
            }
        }
    )
}
