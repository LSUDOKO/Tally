package app.tally.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tally.ui.theme.TallyColors

@Composable
fun TopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TallyColors.Text) }
        } else {
            Spacer(Modifier.size(12.dp))
        }
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = TallyColors.Text, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, accent: Color? = null, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .background(TallyColors.Surface, RoundedCornerShape(20.dp))
            .then(if (accent != null) Modifier.border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(20.dp)) else Modifier)
            .padding(18.dp),
    ) { content() }
}

@Composable
fun Label(text: String, color: Color = TallyColors.Muted) = Text(text.uppercase(), fontSize = 11.sp, letterSpacing = 1.2.sp, color = color, fontWeight = FontWeight.Medium)

@Composable
fun Big(text: String, color: Color = TallyColors.Text, size: Int = 34) = Text(text, fontSize = size.sp, fontWeight = FontWeight.Bold, color = color)

@Composable
fun Body(text: String, color: Color = TallyColors.Text) = Text(text, fontSize = 15.sp, color = color, lineHeight = 21.sp)

@Composable
fun Mono(text: String, color: Color = TallyColors.Muted) = Text(text, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = color)

@Composable
fun Chip(text: String, color: Color = TallyColors.Accent) {
    Box(Modifier.background(color.copy(alpha = 0.14f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun Dot(color: Color) = Box(Modifier.size(8.dp).background(color, CircleShape))

@Composable
fun Primary(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, color: Color = TallyColors.Accent) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = TallyColors.Ink),
    ) { Text(text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
}

@Composable
fun Secondary(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp)) {
        Text(text, color = TallyColors.Text, fontSize = 15.sp)
    }
}

@Composable
fun Gap(h: Int = 12) = Spacer(Modifier.height(h.dp))

@Composable
fun Stat(label: String, value: String, color: Color = TallyColors.Text, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Label(label)
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

fun short(a: String?) = a?.let { if (it.length > 10) it.take(4) + "…" + it.takeLast(4) else it } ?: "—"
