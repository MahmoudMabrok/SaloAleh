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
fun DailyLeaderboardPromoDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MohamedLoversPalette.DeepBlue, RoundedCornerShape(20.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = "📊", fontSize = 48.sp)
            Text(
                text = "ميزة جديدة: الترتيب اليومي",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "الآن يمكنك متابعة ترتيبك اليومي بين المصلّين على النبي ﷺ\n\nتابع تقدّمك يومًا بيوم وتنافس مع المحبين!",
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.85f),
                fontSize = 14.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "يمكنك التبديل بين الترتيب الأسبوعي واليومي من الإعدادات",
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.55f),
                fontSize = 12.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MohamedLoversPalette.GoldBase,
                    contentColor = MohamedLoversPalette.DeepBlue,
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "فهمت",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
    }
}
