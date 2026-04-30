package tools.mo3ta.salo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import tools.mo3ta.salo.ui.components.MohamedLoversPalette

@Composable
fun NotificationRationaleDialog(
    onAllow: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MohamedLoversPalette.DeepBlue, RoundedCornerShape(20.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "🔔",
                fontSize = 48.sp,
            )
            Text(
                text = "ابق على تواصل مع النبي ﷺ",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "نرسل لك تذكيراً يومياً بالصلاة على النبي محمد ﷺ\nوتنبيهاً عندما تفوتنا — لأن كل يوم يُحتسب.",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onAllow,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MohamedLoversPalette.Gold),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("السماح بالإشعارات", color = Color.Black, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onDismiss) {
                Text("ليس الآن", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
            }
        }
    }
}
