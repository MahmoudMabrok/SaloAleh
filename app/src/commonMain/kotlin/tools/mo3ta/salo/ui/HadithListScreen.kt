package tools.mo3ta.salo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tools.mo3ta.salo.data.hadith.DailyHadithStore
import tools.mo3ta.salo.data.hadith.HadithItem

private val Gold = Color(0xFFD4AF37)
private val DeepNavy = Color(0xFF09142B)
private val MidNavy = Color(0xFF0F2040)
private val Cream = Color(0xFFFFF8E7)
private val MutedGold = Color(0xFFA89020)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HadithListScreen(onBack: () -> Unit) {
    Scaffold(
        containerColor = DeepNavy,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "فضل الصلاة على النبي ﷺ",
                        color = Gold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = Gold,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0D1B36)),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            itemsIndexed(DailyHadithStore.HADITHS) { index, item ->
                HadithListItem(item = item, index = index)
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun HadithListItem(item: HadithItem, index: Int) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(MidNavy, DeepNavy)))
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(listOf(Gold.copy(alpha = 0.5f), Gold.copy(alpha = 0.1f))),
                shape = shape,
            )
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(
            text = "${index + 1}. ${item.title}",
            color = Gold,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            style = TextStyle(textDirection = TextDirection.Rtl),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(listOf(Color.Transparent, Gold.copy(alpha = 0.3f), Color.Transparent)),
                ),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = item.text,
            color = Cream,
            fontSize = 13.sp,
            lineHeight = 23.sp,
            textAlign = TextAlign.Center,
            style = TextStyle(textDirection = TextDirection.Rtl),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = item.source,
            color = MutedGold,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            style = TextStyle(textDirection = TextDirection.Rtl),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
