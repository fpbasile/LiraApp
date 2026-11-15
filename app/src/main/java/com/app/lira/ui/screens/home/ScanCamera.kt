package com.app.lira.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.app.lira.ui.components.lira.liraClickable
import com.app.lira.ui.theme.LiraColors
import com.app.lira.ui.theme.LiraType

/**
 * Scan · camera (Scan-Camera board). The app has no in-app camera stack, so this is the framing
 * screen: the shutter hands off to the system camera, the gallery button picks an existing photo.
 * Either way the photo comes back to Home's pre-filled review.
 */
@Composable
internal fun ScanCameraScreen(onShutter: () -> Unit, onGallery: () -> Unit, onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(Modifier.fillMaxSize().background(Color(0xFF2A2826))) {
            // Receipt illustration + corner guides.
            Box(Modifier.fillMaxWidth().fillMaxHeight().padding(bottom = 220.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(292.dp, 458.dp)) {
                    Column(
                        Modifier.align(Alignment.Center).size(228.dp, 380.dp).rotate(-3f).clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFE9E6DD)).padding(horizontal = 22.dp, vertical = 26.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        listOf(0.4f, 0.6f, 0f, 0.7f, 0.55f, 0.65f, 0.48f, 0f, 0.35f, 0.8f).forEach { w ->
                            Box(Modifier.fillMaxWidth(w.coerceAtLeast(0.001f)).height(6.dp).clip(CircleShape).background(if (w == 0f) Color.Transparent else Color(0xFFB9B6AE)))
                        }
                        Spacer(Modifier.weight(1f))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Box(Modifier.fillMaxWidth(0.3f).height(10.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF6E6B64)))
                            Box(Modifier.fillMaxWidth(0.34f).height(10.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF6E6B64)))
                        }
                    }
                    Corner(Modifier.align(Alignment.TopStart), 0f)
                    Corner(Modifier.align(Alignment.TopEnd), 90f)
                    Corner(Modifier.align(Alignment.BottomEnd), 180f)
                    Corner(Modifier.align(Alignment.BottomStart), 270f)
                }
            }

            Column(Modifier.fillMaxWidth().statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).liraClickable(onClick = onClose)
                            .semantics { contentDescription = "Close camera" },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Outlined.Close, null, tint = LiraColors.TextPrimary, modifier = Modifier.size(22.dp)) }
                }
                Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Lay it flat in good light — fit the whole receipt",
                        style = LiraType.Label.copy(fontSize = 14.sp),
                        modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }

            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(LiraColors.Base).navigationBarsPadding()
                    .height(220.dp).padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Box(
                        Modifier.size(52.dp).clip(CircleShape).background(LiraColors.Surface2).liraClickable(onClick = onGallery)
                            .semantics { contentDescription = "Pick from gallery" },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Outlined.PhotoLibrary, null, tint = LiraColors.TextPrimary, modifier = Modifier.size(22.dp)) }
                    Box(
                        Modifier.size(80.dp).border(3.dp, LiraColors.TextPrimary, CircleShape).clip(CircleShape).liraClickable(onClick = onShutter)
                            .semantics { contentDescription = "Take photo" },
                        contentAlignment = Alignment.Center
                    ) { Box(Modifier.size(64.dp).clip(CircleShape).background(LiraColors.TextPrimary)) }
                    Spacer(Modifier.width(52.dp))
                }
                Text("Lira reads the merchant, amount, date and category", style = LiraType.BodyS.copy(color = LiraColors.TextTertiary, fontWeight = FontWeight.Normal))
            }
        }
    }
}

@Composable
private fun Corner(modifier: Modifier, degrees: Float) {
    Box(modifier.size(36.dp).rotate(degrees)) {
        Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(topStart = 10.dp)).background(LiraColors.TextPrimary))
        Box(Modifier.height(3.dp).fillMaxWidth().clip(RoundedCornerShape(topStart = 10.dp)).background(LiraColors.TextPrimary))
    }
}
