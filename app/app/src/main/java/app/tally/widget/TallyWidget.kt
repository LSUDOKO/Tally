package app.tally.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.tally.core.usdc
import app.tally.data.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Home-screen widget: today's spend and attacks blocked this week. */
class TallyWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = Store(context)
        val spent = store.spentToday()
        val blocked = store.attacksBlockedThisWeek()
        provideContent {
            val text = ColorProvider(Color(0xFFE9F1EC), Color(0xFFE9F1EC))
            Column(GlanceModifier.fillMaxSize().background(Color(0xFF0B0F0E)).padding(12.dp)) {
                Text("Tally", style = TextStyle(color = ColorProvider(Color(0xFF7CF0C0), Color(0xFF7CF0C0)), fontWeight = FontWeight.Bold, fontSize = 14.sp))
                Text("\$${usdc(spent)} today", style = TextStyle(color = text, fontSize = 20.sp, fontWeight = FontWeight.Bold))
                Text("$blocked attacks blocked this week", style = TextStyle(color = ColorProvider(Color(0xFFFF5D5D), Color(0xFFFF5D5D)), fontSize = 12.sp))
            }
        }
    }

    companion object {
        fun refresh(context: Context) {
            CoroutineScope(Dispatchers.Default).launch {
                runCatching { if (GlanceAppWidgetManager(context).getGlanceIds(TallyWidget::class.java).isNotEmpty()) TallyWidget().updateAll(context) }
            }
        }
    }
}

class TallyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TallyWidget()
}
