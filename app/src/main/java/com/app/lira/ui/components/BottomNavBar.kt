package com.app.lira.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            .drawBehind {
                drawLine(
                    color = Color.White.copy(alpha = 0.05f),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx()
                )
            }
            // MainActivity uses enableEdgeToEdge(), so the app draws behind the system nav
            // bar/gesture area. Material3's real NavigationBar pads for this internally; our
            // custom Surface needs to do it explicitly or the system bar overlaps this content.
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LiraBottomNavItem(
                iconRes = R.drawable.ic_home,
                label = "Home",
                isSelected = currentRoute == Routes.ADD_PAYED,
                onClick = { onNavigate(Routes.ADD_PAYED) }
            )

            LiraBottomNavItem(
                iconRes = R.drawable.ic_calendar,
                label = "Calendar",
                isSelected = currentRoute == Routes.CALENDAR,
                onClick = { onNavigate(Routes.CALENDAR) }
            )
        }
    }
}

@Composable
private fun LiraBottomNavItem(
    iconRes: Int,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) LiraTextPrimary.copy(alpha = 0.12f) else Color.Transparent,
        label = "nav_item_bg"
    )
    val contentColor = if (isSelected) LiraTextPrimary else LiraTextSecondary.copy(alpha = 0.6f)

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null, // Removes the ripple for a cleaner look
                onClick = onClick
            )
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            color = contentColor,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}
