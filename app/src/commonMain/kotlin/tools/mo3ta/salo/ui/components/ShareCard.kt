package tools.mo3ta.salo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val SCCream      = Color(0xFFFAF3E0)
private val SCCreamLight = Color(0xFFFFF8E0)
private val SCCreamMid   = Color(0xFFF0E0B0)
private val SCGold       = Color(0xFFC19A46)
private val SCGoldDark   = Color(0xFF8B6914)
private val SCGoldDeep   = Color(0xFF3D2C00)
private val SCGoldFaint  = Color(0x4DC19A46)

@Composable
fun ShareCard(data: ShareCardData) {
    Column(
        modifier = Modifier
            .size(width = 400.dp, height = 620.dp)
            .background(Brush.verticalGradient(listOf(SCCream, SCCreamMid))),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ShareCardHeader()
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 28.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Rosette(); Rosette()
            }
            Spacer(Modifier.height(12.dp))
            RankMedallion(rank = data.rank)
            if (data.roundPlayerCount > 0) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "من أصل ${data.roundPlayerCount} مشارك",
                    style = TextStyle(fontSize = 14.sp, color = SCGoldDark.copy(alpha = 0.6f), textAlign = TextAlign.Center),
                )
            }
            Spacer(Modifier.height(10.dp))
            GoldHRule()
            Spacer(Modifier.height(10.dp))
            Text(
                text = data.displayTag,
                style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = SCGoldDeep, textAlign = TextAlign.Center),
            )
            Spacer(Modifier.height(14.dp))
            ShareScoreRow(userScore = data.userScore, roundTotal = data.roundTotal)
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Rosette(); Rosette()
            }
        }
        ShareCardFooter(dateLabel = data.dateLabel)
    }
}

@Composable
private fun ShareCardHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(SCGold, SCGoldDark, SCGold)))
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text("✦ ✦ ✦", style = TextStyle(fontSize = 12.sp, color = Color(0xB3FFEEB4), letterSpacing = 4.sp))
        Text("صلى الله عليه وسلم", style = TextStyle(fontSize = 14.sp, color = SCCreamLight, letterSpacing = 1.sp))
    }
}

@Composable
private fun ShareCardFooter(dateLabel: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(SCGold, SCGoldDark, SCGold)))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("✦ ✦ ✦", style = TextStyle(fontSize = 9.sp, color = Color(0x66FFEEB4), letterSpacing = 4.sp))
        Text("© SaloAleh · صلِّ عليه", style = TextStyle(fontSize = 10.sp, color = Color(0xB3FFF8E0), letterSpacing = 2.sp))
        if (dateLabel.isNotBlank()) {
            Text(dateLabel, style = TextStyle(fontSize = 9.sp, color = Color(0x80FFF8E0), letterSpacing = 1.sp))
        }
    }
}

@Composable
private fun RankMedallion(rank: Int) {
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(116.dp)
                .drawBehind {
                    val paint = Paint().apply {
                        color = SCGold.copy(alpha = 0.4f)
                        style = PaintingStyle.Stroke
                        strokeWidth = 1.5.dp.toPx()
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                    }
                    drawContext.canvas.drawCircle(
                        center = Offset(size.width / 2, size.height / 2),
                        radius = size.minDimension / 2,
                        paint = paint,
                    )
                },
        )
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(SCCreamLight, SCCreamMid)))
                .border(3.dp, SCGold, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "#$rank",
                    style = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Black, color = SCGoldDark, lineHeight = 36.sp),
                )
                Text("RANK", style = TextStyle(fontSize = 10.sp, color = SCGold, letterSpacing = 1.sp))
            }
        }
    }
}

@Composable
private fun GoldHRule() {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.7f)
            .height(1.dp)
            .background(Brush.horizontalGradient(listOf(Color.Transparent, SCGold, Color.Transparent))),
    )
}

@Composable
private fun ShareScoreRow(userScore: Int, roundTotal: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        ScoreCol(value = userScore.scFormat(), label = "صلاتي هذا الأسبوع", emphasis = true)
        Box(modifier = Modifier.width(1.dp).height(48.dp).background(SCGoldFaint))
        ScoreCol(value = roundTotal.scFormat(), label = "مجموع الجولة", emphasis = false)
    }
}

@Composable
private fun ScoreCol(value: String, label: String, emphasis: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = TextStyle(
                fontSize = 24.sp, fontWeight = FontWeight.Black, lineHeight = 24.sp,
                color = if (emphasis) SCGoldDark else SCGoldDeep,
            ),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            style = TextStyle(fontSize = 10.sp, color = SCGoldDark.copy(alpha = if (emphasis) 1f else 0.6f), letterSpacing = 0.5.sp),
        )
    }
}

@Composable
private fun Rosette() {
    Box(
        modifier = Modifier.size(20.dp).drawBehind {
            val paint = Paint().apply { color = SCGold.copy(alpha = 0.3f) }
            val r = size.minDimension / 2
            val cx = size.width / 2; val cy = size.height / 2
            repeat(8) { i ->
                val angle = i * 45.0 * PI / 180.0
                drawContext.canvas.drawCircle(
                    center = Offset((cx + r * 0.5 * cos(angle)).toFloat(), (cy + r * 0.5 * sin(angle)).toFloat()),
                    radius = r * 0.3f, paint = paint,
                )
            }
        },
    )
}

private fun Int.scFormat(): String = when {
    this >= 1_000_000 -> "${this / 1_000_000}M"
    this >= 1_000     -> "${this / 1_000}.${(this % 1_000) / 100}K"
    else              -> toString()
}
