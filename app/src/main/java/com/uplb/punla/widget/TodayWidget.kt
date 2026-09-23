package com.uplb.punla.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.layout.*
import androidx.glance.text.*
import com.uplb.punla.MainActivity
import com.uplb.punla.planning.StudentOsRepository
import com.uplb.punla.ui.screens.deriveTodayNowPresentation
import com.uplb.punla.ui.screens.deriveTodayRecommendationPresentation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = withTimeoutOrNull(10000) { StudentOsRepository.get(context).state.first { it.ready } }
        val open = Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_START_ROUTE, "student-os")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val title = snapshot?.context?.let { deriveTodayNowPresentation(it).title } ?: "Your Punla day"
        val recommendation = snapshot?.ranked?.firstOrNull()
        val detail = snapshot?.context?.let { deriveTodayRecommendationPresentation(it,
            recommendation?.task?.title, recommendation?.reasons?.firstOrNull()).title } ?: "Open Plan & Inbox"
        val updated = LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a"))
        provideContent {
            Column(GlanceModifier.fillMaxSize().background(Color(0xFF143E30)).padding(16.dp)
                .clickable(actionStartActivity(open))) {
                Text("PUNLA TODAY", style = TextStyle(color = androidx.glance.unit.ColorProvider(Color(0xFFB7E6BF)), fontSize = 12.sp))
                Spacer(GlanceModifier.height(6.dp))
                Text(title, style = TextStyle(color = androidx.glance.unit.ColorProvider(Color.White), fontSize = 17.sp, fontWeight = FontWeight.Bold))
                Text(detail, style = TextStyle(color = androidx.glance.unit.ColorProvider(Color.White), fontSize = 14.sp))
                Text("Updated $updated · Tap to plan", style = TextStyle(color = androidx.glance.unit.ColorProvider(Color(0xFFB7E6BF)), fontSize = 11.sp))
            }
        }
    }
}
class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
