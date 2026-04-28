package com.elsharif.dailyseventy.presentation.mohamedlovers.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elsharif.dailyseventy.R

@Composable
internal fun MohamedLoversHadithBanner(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.verticalGradient(
                        0f to Color(0xB31E1608),
                        1f to Color(0x800F0C04),
                    ),
                )
                .border(
                    width = 1.dp,
                    color = MohamedLoversPalette.GoldBase.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(10.dp),
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.mohamed_lovers_hadith_isnad),
                    style = TextStyle(
                        fontFamily = MohamedLoversFonts.arabic,
                        fontWeight = FontWeight.W400,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center,
                    ),
                    color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.6f),
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(1.dp)
                        .background(MohamedLoversPalette.GoldBase.copy(alpha = 0.5f)),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.mohamed_lovers_hadith_quote),
                    style = TextStyle(
                        fontFamily = MohamedLoversFonts.arabic,
                        fontWeight = FontWeight.W700,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        textAlign = TextAlign.Center,
                    ),
                    color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.95f),
                )
            }
        }
        CornerOrnaments(modifier = Modifier.matchParentSize())
    }
}

@Composable
private fun CornerOrnaments(modifier: Modifier) {
    Canvas(modifier = modifier) {
        val corner = 22.dp.toPx()
        val inset = 2.dp.toPx()
        val strokeWidth = 1.dp.toPx()
        val color = MohamedLoversPalette.GoldBase.copy(alpha = 0.7f)

        drawLine(color, Offset(size.width - inset - corner, inset), Offset(size.width - inset, inset), strokeWidth)
        drawLine(color, Offset(size.width - inset, inset), Offset(size.width - inset, inset + corner), strokeWidth)
        drawLine(color, Offset(inset, size.height - inset), Offset(inset + corner, size.height - inset), strokeWidth)
        drawLine(color, Offset(inset, size.height - inset), Offset(inset, size.height - inset - corner), strokeWidth)
    }
}
