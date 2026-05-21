package tools.mo3ta.salo.ui.tendays

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TenDaysEntryIcon(
    modifier: Modifier = Modifier.size(28.dp),
    numberFontSize: TextUnit = 11.sp,
    contentDescription: String? = null,
) {
    val semanticModifier = if (contentDescription == null) {
        modifier
    } else {
        modifier.semantics { this.contentDescription = contentDescription }
    }

    Box(
        modifier = semanticModifier,
        contentAlignment = Alignment.Center,
    ) {
        CompositionNeutralArabicNumber {
            Text(
                text = "10",
                color = TenDaysPalette.Gold,
                fontSize = numberFontSize,
                fontWeight = FontWeight.Black,
                lineHeight = numberFontSize,
            )
        }
    }
}

@Composable
private fun CompositionNeutralArabicNumber(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Ltr,
        content = content,
    )
}
