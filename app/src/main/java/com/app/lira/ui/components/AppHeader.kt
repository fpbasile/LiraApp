package com.app.lira.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.app.lira.R
import com.app.lira.ui.theme.LiraTextPrimary

/**
 * Logo + settings row shared across screens. Scrolls with the page content — not a
 * sticky/pinned header — just a reusable piece of layout.
 */
@Composable
fun AppHeader(onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = R.drawable.lira_logo),
            contentDescription = "Lira Logo",
            modifier = Modifier.height(35.dp)
        )

        Icon(
            painter = painterResource(id = R.drawable.ic_settings),
            contentDescription = "Settings",
            tint = LiraTextPrimary,
            modifier = Modifier
                .size(24.dp)
                .clickable { onSettingsClick() }
        )
    }
}
