package app.tally.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.tally.MainActivity
import app.tally.R
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
            val white = ColorProvider(Color.White, Color.White)
            val soft = ColorProvider(Color(0xCCFFFFFF), Color(0xCCFFFFFF))
            Row(
                GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_background)).cornerRadius(24.dp)
                    .padding(horizontal = 14.dp, vertical = 10.dp).clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(ImageProvider(R.drawable.mascot_avatar), contentDescription = null, modifier = GlanceModifier.size(52.dp).cornerRadius(26.dp))
                Column(GlanceModifier.defaultWeight().padding(start = 12.dp)) {
                    Text("TALLY · TODAY", style = TextStyle(color = soft, fontSize = 11.sp, fontWeight = FontWeight.Medium))
                    Text("\$${usdc(spent)}", style = TextStyle(color = white, fontSize = 24.sp, fontWeight = FontWeight.Bold))
                    Text(
                        if (blocked == 1) "1 attack blocked this week" else "$blocked attacks blocked this week",
                        style = TextStyle(color = ColorProvider(Color(0xFFFFB4B6), Color(0xFFFFB4B6)), fontSize = 12.sp),
                    )
                }
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
