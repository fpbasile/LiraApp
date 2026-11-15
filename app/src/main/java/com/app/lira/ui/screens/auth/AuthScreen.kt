package com.app.lira.ui.screens.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.app.lira.R
import com.app.lira.data.AppPrefs
import com.app.lira.data.CurrencyPreference
import com.app.lira.data.repository.AuthRepository
import com.app.lira.navigation.Routes
import com.app.lira.ui.components.lira.*
import com.app.lira.ui.screens.more.MessageLine
import com.app.lira.ui.theme.LiraColors
import com.app.lira.ui.theme.LiraType
import com.app.lira.ui.theme.NumberFont
import com.app.lira.util.Money
import kotlinx.coroutines.launch

private enum class AuthStage { Welcome, LogIn, SignUp }

/**
 * Welcome → sign up / log in. A fresh install opens on the welcome; after a log-out it opens on
 * "Log in". Sign-up leads to onboarding; sign-in goes Home if the account already has data.
 */
@Composable
fun AuthScreen(navController: NavHostController) {
    var stage by remember {
        mutableStateOf(if (AuthFlow.startAtLogin) AuthStage.LogIn else AuthStage.Welcome).also { AuthFlow.startAtLogin = false }
    }
    // Kept here so they survive switching between "Log in" and "Create account".
    var email by rememberSaveable { mutableStateOf("") }
    var notice by remember { mutableStateOf<String?>(null) }
    var askOffline by remember { mutableStateOf(false) }
    BackHandler(enabled = stage != AuthStage.Welcome) { stage = AuthStage.Welcome; notice = null }

    LiraScreen {
        AnimatedContent(
            targetState = stage,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
            label = "auth"
        ) { s ->
            when (s) {
                AuthStage.Welcome -> WelcomeStage(
                    onGetStarted = { stage = AuthStage.SignUp },
                    onHaveAccount = { stage = AuthStage.LogIn },
                    onOffline = { askOffline = true }
                )
                else -> CredentialsStage(
                    navController = navController,
                    signUp = s == AuthStage.SignUp,
                    email = email,
                    onEmailChange = { email = it },
                    notice = notice,
                    onNotice = { notice = it },
                    onSwitch = { stage = if (s == AuthStage.SignUp) AuthStage.LogIn else AuthStage.SignUp }
                )
            }
        }
    }
    if (askOffline) {
        OfflineSheet(
            onConfirm = {
                askOffline = false
                AppPrefs.offlineMode = true
                AppPrefs.onboardingDone = false
                com.app.lira.data.local.LocalDb.ensureSeeded()
                navController.navigate(Routes.ONBOARDING) { popUpTo(0) { inclusive = true } }
            },
            onDismiss = { askOffline = false }
        )
    }
}

@Composable
private fun OfflineSheet(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    LiraSheet(onDismiss = onDismiss, title = "Use Lira offline?") {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Everything stays on this phone. Nothing is backed up: if you uninstall Lira, clear its data or lose your phone, your entries are gone.",
                style = LiraType.BodyM.copy(color = LiraColors.TextSecondary)
            )
            Text(
                "You can export a CSV any time from More, and import it into an account later.",
                style = LiraType.BodyM.copy(color = LiraColors.TextSecondary)
            )
            PrimaryButton("Continue offline", onConfirm, Modifier.fillMaxWidth())
            GhostButton("Back", onDismiss, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Wordmark(height: Int = 40) {
    Image(painterResource(R.drawable.lira_logo), contentDescription = "Lira", modifier = Modifier.height(height.dp))
}

@Composable
private fun WelcomeStage(onGetStarted: () -> Unit, onHaveAccount: () -> Unit, onOffline: () -> Unit) {
    val code = CurrencyPreference.code
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight)
                .navigationBarsPadding().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(Modifier.height(1.dp))
            Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
                Wordmark()
                Text(
                    "Log a purchase in three seconds.",
                    style = LiraType.TitleL.copy(fontFamily = NumberFont, fontSize = 40.sp, lineHeight = 43.sp, letterSpacing = (-0.025).em)
                )
                Text(
                    "Type what you spent, tap a category. Lira shows what’s left — not what’s gone.",
                    style = LiraType.BodyL.copy(color = LiraColors.TextSecondary)
                )
                // A still of the core loop: what's left, and the save confirmation.
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(LiraColors.Surface1).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BudgetRing(0.7f, size = 40.dp, stroke = 4.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(Money.format(18.0, code, trimZeros = true), style = LiraType.AmountS.copy(color = LiraColors.Lichen))
                                Text("left today", style = LiraType.BodyM.copy(color = LiraColors.TextSecondary))
                            }
                            Text("of " + Money.format(60.0, code, trimZeros = true), style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextTertiary))
                        }
                    }
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(LiraColors.Surface3)) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(Modifier.size(28.dp).clip(CircleShape).background(LiraColors.Lichen), contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Check, null, tint = LiraColors.Base, modifier = Modifier.size(16.dp))
                            }
                            Text(
                                "Coffee · ${Money.format(3.20, code)} saved",
                                style = LiraType.Label.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                                modifier = Modifier.weight(1f)
                            )
                            Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.AutoMirrored.Outlined.Undo, null, tint = LiraColors.TextPrimary, modifier = Modifier.size(16.dp))
                                Text("Undo", style = LiraType.Label.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
                            }
                        }
                        Box(Modifier.fillMaxWidth().height(2.dp).background(LiraColors.Line)) {
                            Box(Modifier.fillMaxWidth(0.6f).height(2.dp).background(LiraColors.TextSecondary))
                        }
                    }
                }
            }
            Column(Modifier.padding(top = 24.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PrimaryButton("Get started", onGetStarted, Modifier.fillMaxWidth())
                GhostButton("I already have an account", onHaveAccount, Modifier.fillMaxWidth())
                Text(
                    "Use without an account",
                    style = LiraType.Label.copy(color = LiraColors.TextTertiary),
                    modifier = Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(12.dp))
                        .liraClickable(onClick = onOffline).padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun CredentialsStage(
    navController: NavHostController,
    signUp: Boolean,
    email: String,
    onEmailChange: (String) -> Unit,
    notice: String?,
    onNotice: (String?) -> Unit,
    onSwitch: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showReset by remember { mutableStateOf(false) }

    fun go(route: String) {
        navController.navigate(route) {
            popUpTo(Routes.LOGIN) { inclusive = true }
            launchSingleTop = true
        }
    }

    fun submit() {
        val e = email.trim()
        when {
            e.isEmpty() || !e.contains('@') -> { error = "Enter your email address."; return }
            password.isEmpty() -> { error = "Enter your password."; return }
            signUp && password.length < 6 -> { error = "Use at least 6 characters for your password."; return }
        }
        scope.launch {
            loading = true
            error = null
            onNotice(null)
            if (signUp) {
                AuthRepository.signUp(e, password)
                    .onSuccess { signedIn ->
                        if (signedIn) {
                            if (AuthFlow.migrateOfflineIfNeeded()) {
                                AppPrefs.onboardingDone = true
                                go(Routes.HOME)
                            } else {
                                AppPrefs.onboardingDone = false
                                go(Routes.ONBOARDING)
                            }
                        } else {
                            // Project requires email confirmation: no session until the link is opened.
                            password = ""
                            onNotice("Account created. Open the link we sent to $e, then log in here.")
                            onSwitch()
                        }
                    }
                    .onFailure { error = it.message ?: "Couldn't create your account." }
            } else {
                AuthRepository.signIn(e, password)
                    .onSuccess { go(AuthFlow.routeAfterSignIn()) }
                    .onFailure { error = it.message ?: "Couldn't log you in." }
            }
            loading = false
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight)
                .navigationBarsPadding().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(Modifier.height(1.dp))
            Column(Modifier.padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Wordmark()
                Text(
                    if (signUp) "Make an account. Two quick questions after, then you're logging."
                    else "Log a purchase in three seconds. See what’s left at a glance.",
                    style = LiraType.BodyL.copy(fontSize = 17.sp, lineHeight = 25.sp, color = LiraColors.TextSecondary),
                    modifier = Modifier.widthIn(max = 300.dp)
                )
            }
            Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                LiraTextField(
                    value = email,
                    onValueChange = { onEmailChange(it); error = null },
                    placeholder = "you@example.com",
                    label = "Email",
                    leadingIcon = Icons.Outlined.Mail,
                    keyboardType = KeyboardType.Email
                )
                LiraTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    placeholder = if (signUp) "At least 6 characters" else "Your password",
                    label = "Password",
                    leadingIcon = Icons.Outlined.Lock,
                    password = !showPassword,
                    keyboardType = KeyboardType.Password,
                    trailing = {
                        IconCircleButton(
                            if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            if (showPassword) "Hide password" else "Show password",
                            { showPassword = !showPassword },
                            background = Color.Transparent,
                            tint = LiraColors.TextSecondary,
                            size = 36.dp
                        )
                    }
                )
                error?.let { MessageLine(it) }
                notice?.let { MessageLine(it, positive = true) }
                Spacer(Modifier.height(2.dp))
                PrimaryButton(if (signUp) "Create account" else "Log in", { submit() }, Modifier.fillMaxWidth(), loading = loading)
                Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    if (signUp) {
                        Text("Already have an account?", style = LiraType.BodyM.copy(fontSize = 14.sp, color = LiraColors.TextSecondary))
                        Text(
                            "Log in",
                            style = LiraType.BodyM.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.heightIn(min = 48.dp).wrapContentHeight().liraClickable { error = null; onSwitch() }
                        )
                    } else {
                        Text(
                            "Forgot password?",
                            style = LiraType.BodyM.copy(fontSize = 14.sp, color = LiraColors.TextSecondary),
                            modifier = Modifier.heightIn(min = 48.dp).wrapContentHeight().liraClickable { showReset = true }
                        )
                        Text(
                            "Create account",
                            style = LiraType.BodyM.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.heightIn(min = 48.dp).wrapContentHeight().liraClickable { error = null; onNotice(null); onSwitch() }
                        )
                    }
                }
            }
        }
    }

    if (showReset) {
        ResetPasswordSheet(initialEmail = email.trim(), onDismiss = { showReset = false })
    }
}

@Composable
private fun ResetPasswordSheet(initialEmail: String, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf(initialEmail) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var sent by remember { mutableStateOf(false) }
    LiraSheet(onDismiss = { if (!sending) onDismiss() }, title = "Reset password") {
        Column(Modifier.imePadding().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (sent) {
                MessageLine("If there's an account for $email, a reset link is on its way.", positive = true)
                PrimaryButton("Done", onDismiss, Modifier.fillMaxWidth())
            } else {
                Text("We'll email you a link to choose a new password.", style = LiraType.BodyM.copy(color = LiraColors.TextSecondary))
                LiraTextField(
                    value = email, onValueChange = { email = it; error = null },
                    placeholder = "you@example.com", leadingIcon = Icons.Outlined.Mail, keyboardType = KeyboardType.Email
                )
                error?.let { MessageLine(it) }
                PrimaryButton("Send reset link", {
                    val e = email.trim()
                    if (!e.contains('@')) { error = "Enter your email address."; return@PrimaryButton }
                    scope.launch {
                        sending = true
                        AuthFlow.sendPasswordReset(e)
                            .onSuccess { sent = true }
                            .onFailure { error = it.message }
                        sending = false
                    }
                }, Modifier.fillMaxWidth(), loading = sending)
            }
        }
    }
}
