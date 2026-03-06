package com.app.lira.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.app.lira.R
import com.app.lira.ui.theme.*

@Composable
fun LiraBottomNavBar() {
    Column(modifier = Modifier.fillMaxWidth().background(LiraBackground)) {
        HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)
        Row(
            modifier = Modifier.fillMaxWidth().height(80.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem(R.drawable.ic_home, "Home", true, Modifier.weight(1f))
            BottomNavItem(R.drawable.ic_chart, "Chart", false, Modifier.weight(1f))
            BottomNavItem(R.drawable.ic_calendar, "Calendar", false, Modifier.weight(1f), size = 50.dp)
        }
    }
}

@Composable
private fun BottomNavItem(iconRes: Int, desc: String, selected: Boolean, modifier: Modifier, size: Dp = 35.dp) {
    Box(modifier = modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = desc,
            tint = if (selected) LiraTextPrimary else LiraTextSecondary,
            modifier = Modifier.size(size).padding(bottom = 10.dp)
        )
    }
}
