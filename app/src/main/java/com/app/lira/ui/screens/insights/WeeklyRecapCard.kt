package com.app.lira.ui.screens.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.lira.data.CategoryLook
import com.app.lira.ui.components.lira.CategoryIconBadge
import com.app.lira.ui.theme.LiraColors
import com.app.lira.ui.theme.LiraType

/**
 * "Your week in Lira" — the Monday recap (Recap-Home board). On Home it shows once on Monday;
 * here in Insights it stays available for the last completed week.
 */
@Composable
fun WeeklyRecapCard(
    recap: WeeklyRecap,
    code: String,
    lookOf: (categoryName: String?) -> CategoryLook,
    modifier: Modifier = Modifier
) {
    Panel(modifier, gap = 18.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text("YOUR WEEK IN LIRA", style = LiraType.Caption, modifier = Modifier.weight(1f))
            Text(weekRangeLabel(recap.start, recap.end), style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextTertiary))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    money(recap.spent, code),
                    style = LiraType.AmountXl.copy(fontSize = 44.sp, lineHeight = 44.sp),
                    maxLines = 1, softWrap = false
                )
                val line = buildAnnotatedString {
                    append("spent")
                    val limit = recap.limit
                    if (limit != null) {
                        append(" · ")
                        val left = limit - recap.spent
                        if (left >= 0) {
                            withStyle(SpanStyle(color = LiraColors.Lichen, fontWeight = FontWeight.SemiBold)) { append("${money(left, code)} under") }
                        } else {
                            withStyle(SpanStyle(color = LiraColors.OverLimit, fontWeight = FontWeight.SemiBold)) { append("${money(-left, code)} over") }
                        }
                        append(" your limit")
                    }
                }
                Text(line, style = LiraType.BodyS)
            }
            MiniWeekBars(recap.perDay, Modifier.semantics { contentDescription = "Spending per day this week" })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val top = recap.topCategory
            if (top != null) {
                RecapTile("Top category", Modifier.weight(1f)) {
                    val look = lookOf(top.first.takeIf { it != UNCATEGORISED })
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CategoryIconBadge(look.icon, look.color, size = 28.dp)
                        Column {
                            Text(top.first, style = LiraType.Label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(money(top.second, code), style = LiraType.AmountXs.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal, color = LiraColors.TextSecondary))
                        }
                    }
                }
            }
            RecapTile("Logging", Modifier.weight(1f)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${recap.logged.count { it }} of 7 days", style = LiraType.Label)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        recap.logged.forEach { on ->
                            if (on) Box(Modifier.size(6.dp).clip(CircleShape).background(LiraColors.Lichen))
                            else Box(Modifier.size(6.dp).border(1.dp, LiraColors.TextDisabled, CircleShape))
                        }
                    }
                }
            }
        }
        recap.weekendShare?.let { share ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.CalendarMonth, null, tint = LiraColors.Lichen, modifier = Modifier.size(18.dp))
                Text("The weekend was $share% of your week.", style = LiraType.BodyM.copy(fontSize = 14.sp, lineHeight = 20.sp))
            }
        }
    }
}

@Composable
private fun RecapTile(label: String, modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(LiraColors.Surface2).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label.uppercase(), style = LiraType.Caption)
        content()
    }
}
