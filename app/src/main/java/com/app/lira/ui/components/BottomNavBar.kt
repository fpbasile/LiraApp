package com.app.lira.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.app.lira.R
import com.app.lira.navigation.Routes
import com.app.lira.ui.theme.*

@Composable
fun LiraBottomNavBar(
    currentRoute: String,
    onNavigate: (String) -> Unit
) {
    Surface(
        color = LiraBackground,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .drawBehind {
                drawLine(
                    color = Color.White.copy(alpha = 0.05f),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx()
                )
            }
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            LiraBottomNavItem(
                iconRes = R.drawable.ic_home,
                isSelected = currentRoute == Routes.ADD_PAYED,
                onClick = { onNavigate(Routes.ADD_PAYED) }
            )

            // The middle icon (Calendar/Chart) - kept slightly larger but scaled down
            LiraBottomNavItem(
                iconRes = R.drawable.ic_chart,
                isSelected = false,
                onClick = { /* Navigate */ }
            )

            LiraBottomNavItem(
                iconRes = R.drawable.ic_calendar,
                isSelected = false,
                onClick = { /* Navigate */ },
                isSpecial = true // We use a flag for that "big" center icon
            )
        }
    }
}

@Composable
private fun LiraBottomNavItem(
    iconRes: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    isSpecial: Boolean = false
) {
    Box(
        modifier = Modifier
            .size(if (isSpecial) 48.dp else 40.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null, // Removes the ripple for a cleaner look
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = if (isSelected) LiraTextPrimary else LiraTextSecondary.copy(alpha = 0.5f),
                modifier = Modifier.size(if (isSpecial) 28.dp else 24.dp) // Clean standard sizes
            )

            // Small indicator dot for the active screen
            if (isSelected) {
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .size(3.dp)
                        .clip(CircleShape)
                        .background(LiraTextPrimary)
                )
            }
        }
    }
}
