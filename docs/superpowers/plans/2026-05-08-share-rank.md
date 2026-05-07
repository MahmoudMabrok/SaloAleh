# Share Rank Feature — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "مشاركة رتبتي" button to `MohamedLoversInfoSheet` that captures a C3 ornamental card as an image and shares it via the native OS share sheet on Android and iOS.

**Architecture:** `ShareCardData` and `ShareCard` live in commonMain. `shareImage(data)` is added to the existing `PlatformActions` expect/actual pattern — Android actual uses `ImageRenderer` + `FileProvider` + `Intent.ACTION_SEND`; iOS actual uses `ImageRenderer` + `UIActivityViewController`. No new ViewModel logic or Firebase calls — all data from existing `MohamedLoversUiState`.

**Tech Stack:** Compose Multiplatform 1.7.3, Kotlin 2.1.20, `androidx.compose.ui.graphics.ImageRenderer` (commonMain), Android `FileProvider` + `androidx.core`, iOS `CGImage` / `UIActivityViewController` via Kotlin/Native interop, existing `AndroidAppContext` singleton.

---

## File Map

| Action | Path |
|---|---|
| Create | `app/src/commonMain/kotlin/tools/mo3ta/salo/ui/components/ShareCardData.kt` |
| Create | `app/src/commonMain/kotlin/tools/mo3ta/salo/ui/components/ShareCard.kt` |
| Modify | `app/src/commonMain/kotlin/tools/mo3ta/salo/ui/PlatformActions.kt` |
| Modify | `app/src/androidMain/kotlin/tools/mo3ta/salo/ui/PlatformActions.android.kt` |
| Create | `app/src/androidMain/res/xml/fileprovider_paths.xml` |
| Modify | `app/src/androidMain/AndroidManifest.xml` |
| Modify | `app/src/iosMain/kotlin/tools/mo3ta/salo/ui/PlatformActions.ios.kt` |
| Modify | `app/src/commonMain/kotlin/tools/mo3ta/salo/ui/components/MohamedLoversInfoSheet.kt` |

---

## Task 1: ShareCardData model

**Files:**
- Create: `app/src/commonMain/kotlin/tools/mo3ta/salo/ui/components/ShareCardData.kt`

- [ ] **Step 1: Create the file**

```kotlin
package tools.mo3ta.salo.ui.components

data class ShareCardData(
    val displayTag: String,
    val rank: Int,
    val userScore: Int,
    val roundTotal: Int,
    val roundPlayerCount: Int,
)
```

- [ ] **Step 2: Compile check**

```bash
./gradlew :app:compileCommonMainKotlinMetadata
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/ui/components/ShareCardData.kt
git commit -m "feat: add ShareCardData model"
```

---

## Task 2: ShareCard composable (C3 ornamental design)

**Files:**
- Create: `app/src/commonMain/kotlin/tools/mo3ta/salo/ui/components/ShareCard.kt`

- [ ] **Step 1: Create the composable**

Fixed size 400×620 dp. Private colors are local to the file — do not use `MohamedLoversPalette` (the card has its own cream/gold palette separate from the dark app theme).

```kotlin
package tools.mo3ta.salo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
            Spacer(Modifier.height(10.dp))
            Text(
                text = "من أصل ${data.roundPlayerCount} مشارك",
                style = TextStyle(fontSize = 14.sp, color = SCGoldDark.copy(alpha = 0.6f), textAlign = TextAlign.Center),
            )
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
        ShareCardFooter()
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
private fun ShareCardFooter() {
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
    }
}

@Composable
private fun RankMedallion(rank: Int) {
    Box(contentAlignment = Alignment.Center) {
        // dashed outer ring
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
        // solid medallion
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(SCCreamLight, SCCreamMid)))
                .border(3.dp, SCGold, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("#$rank", style = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Black, color = SCGoldDark, lineHeight = 36.sp))
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
                val angle = Math.toRadians(i * 45.0)
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
```

- [ ] **Step 2: Compile check**

```bash
./gradlew :app:compileCommonMainKotlinMetadata
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/ui/components/ShareCard.kt
git commit -m "feat: add ShareCard composable (C3 ornamental design)"
```

---

## Task 3: expect declaration

**Files:**
- Modify: `app/src/commonMain/kotlin/tools/mo3ta/salo/ui/PlatformActions.kt`

- [ ] **Step 1: Add import + expect function**

Full file after edit:

```kotlin
package tools.mo3ta.salo.ui

import tools.mo3ta.salo.ui.components.ShareCardData

expect fun showPlatformToast(message: String)
expect fun copyToClipboard(text: String)
expect fun shareText(text: String)
expect fun areNotificationsEnabled(): Boolean
expect fun openNotificationSettings()
expect fun getAppVersion(): String
expect fun shareImage(data: ShareCardData)
```

- [ ] **Step 2: Compile check**

```bash
./gradlew :app:compileCommonMainKotlinMetadata
```
Expected: `BUILD SUCCESSFUL` (commonMain only; actuals not required here)

- [ ] **Step 3: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/ui/PlatformActions.kt
git commit -m "feat: add shareImage expect declaration"
```

---

## Task 4: Android actual + FileProvider

**Files:**
- Create: `app/src/androidMain/res/xml/fileprovider_paths.xml`
- Modify: `app/src/androidMain/AndroidManifest.xml`
- Modify: `app/src/androidMain/kotlin/tools/mo3ta/salo/ui/PlatformActions.android.kt`

- [ ] **Step 1: Create FileProvider paths config**

Create `app/src/androidMain/res/xml/fileprovider_paths.xml` (create `res/xml/` directory first if absent):

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="shared_images" path="share/" />
</paths>
```

- [ ] **Step 2: Register FileProvider in AndroidManifest.xml**

Inside the `<application>` block, add before the closing `</application>` tag:

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.provider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/fileprovider_paths" />
</provider>
```

- [ ] **Step 3: Add actual to PlatformActions.android.kt**

Add these imports at the top of the file:

```kotlin
import androidx.compose.ui.graphics.ImageRenderer
import androidx.compose.ui.graphics.asAndroidBitmap
import tools.mo3ta.salo.ui.components.ShareCard
import tools.mo3ta.salo.ui.components.ShareCardData
```

Add the actual function:

```kotlin
actual fun shareImage(data: ShareCardData) {
    val context = AndroidAppContext.get()
    val renderer = ImageRenderer { ShareCard(data = data) }
    val androidBitmap = renderer.imageBitmap?.asAndroidBitmap() ?: return

    val shareDir = java.io.File(context.cacheDir, "share").also { it.mkdirs() }
    val file = java.io.File(shareDir, "share_card.png")
    file.outputStream().use { androidBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }

    val uri = androidx.core.content.FileProvider.getUriForFile(
        context, "${context.packageName}.provider", file
    )
    val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        android.content.Intent.createChooser(sendIntent, null).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    )
}
```

- [ ] **Step 4: Compile check**

```bash
./gradlew :app:compileDebugKotlin
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/androidMain/res/xml/fileprovider_paths.xml \
        app/src/androidMain/AndroidManifest.xml \
        app/src/androidMain/kotlin/tools/mo3ta/salo/ui/PlatformActions.android.kt
git commit -m "feat: add Android actual for shareImage with FileProvider"
```

---

## Task 5: iOS actual

**Files:**
- Modify: `app/src/iosMain/kotlin/tools/mo3ta/salo/ui/PlatformActions.ios.kt`

The iOS actual converts `ImageBitmap` → raw RGBA bytes → `CGImage` → `UIImage` → `UIActivityViewController`.

- [ ] **Step 1: Add imports at the top of PlatformActions.ios.kt**

```kotlin
import androidx.compose.ui.graphics.ImageRenderer
import androidx.compose.ui.graphics.toPixelMap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreFoundation.CFDataRef
import platform.CoreGraphics.*
import platform.Foundation.NSData
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import tools.mo3ta.salo.ui.components.ShareCard
import tools.mo3ta.salo.ui.components.ShareCardData
```

- [ ] **Step 2: Add iOS actual**

```kotlin
@OptIn(ExperimentalForeignApi::class)
actual fun shareImage(data: ShareCardData) {
    val renderer = ImageRenderer { ShareCard(data = data) }
    val imageBitmap = renderer.imageBitmap ?: return
    val pixelMap = imageBitmap.toPixelMap()
    val width = pixelMap.width
    val height = pixelMap.height
    val bytesPerRow = width * 4

    val rawBytes = ByteArray(height * bytesPerRow)
    for (y in 0 until height) {
        for (x in 0 until width) {
            val color = pixelMap[x, y]
            val i = (y * width + x) * 4
            rawBytes[i]     = (color.red   * 255).toInt().and(0xFF).toByte()
            rawBytes[i + 1] = (color.green * 255).toInt().and(0xFF).toByte()
            rawBytes[i + 2] = (color.blue  * 255).toInt().and(0xFF).toByte()
            rawBytes[i + 3] = (color.alpha * 255).toInt().and(0xFF).toByte()
        }
    }

    val colorSpace = CGColorSpaceCreateDeviceRGB() ?: return
    val uiImage: UIImage = rawBytes.usePinned { pinned ->
        val nsData = NSData.create(bytes = pinned.addressOf(0), length = rawBytes.size.toULong())
        val provider = CGDataProviderCreateWithCFData(nsData as CFDataRef) ?: return@usePinned null
        val cgImage = CGImageCreate(
            width          = width.toULong(),
            height         = height.toULong(),
            bitsPerComponent = 8u,
            bitsPerPixel   = 32u,
            bytesPerRow    = bytesPerRow.toULong(),
            space          = colorSpace,
            bitmapInfo     = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value,
            provider       = provider,
            decode         = null,
            shouldInterpolate = false,
            intent         = kCGRenderingIntentDefault,
        ) ?: return@usePinned null
        UIImage.imageWithCGImage(cgImage)
    } ?: return

    val activityVC = UIActivityViewController(
        activityItems = listOf(uiImage),
        applicationActivities = null,
    )
    UIApplication.sharedApplication.keyWindow
        ?.rootViewController
        ?.presentViewController(activityVC, animated = true, completion = null)
}
```

- [ ] **Step 3: Compile check**

```bash
./gradlew :app:compileKotlinIosSimulatorArm64
```
Expected: `BUILD SUCCESSFUL`

If `CGImageCreate` or `CGDataProviderCreateWithCFData` have compiler errors about parameter types, check the exact Kotlin/Native signatures via Xcode's symbol browser or autocomplete and adjust the casts accordingly — the logic is correct, only the interop types may need minor adjustments.

- [ ] **Step 4: Commit**

```bash
git add app/src/iosMain/kotlin/tools/mo3ta/salo/ui/PlatformActions.ios.kt
git commit -m "feat: add iOS actual for shareImage via UIActivityViewController"
```

---

## Task 6: Wire share button into MohamedLoversInfoSheet

**Files:**
- Modify: `app/src/commonMain/kotlin/tools/mo3ta/salo/ui/components/MohamedLoversInfoSheet.kt`

- [ ] **Step 1: Add ShareButton composable**

Add this private composable anywhere before the closing of the file (e.g., before `formatCount`):

```kotlin
@Composable
private fun ShareButton(data: ShareCardData) {
    val enabled = data.rank > 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(
                    if (enabled)
                        Brush.horizontalGradient(listOf(MohamedLoversPalette.GoldBase, MohamedLoversPalette.MoonShadow))
                    else
                        Brush.horizontalGradient(
                            listOf(
                                MohamedLoversPalette.GoldBase.copy(alpha = 0.3f),
                                MohamedLoversPalette.MoonShadow.copy(alpha = 0.3f),
                            )
                        )
                )
                .clickable(enabled = enabled) { shareImage(data) }
                .padding(horizontal = 24.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = if (enabled) 1f else 0.4f),
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "مشاركة رتبتي",
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = if (enabled) 1f else 0.4f),
                    ),
                )
            }
        }
    }
}
```

- [ ] **Step 2: Call ShareButton below TotalsCard in MohamedLoversInfoSheet**

In the `MohamedLoversInfoSheet` composable body (L71–130), find the `TotalsCard(...)` call and add `ShareButton` immediately after it:

```kotlin
TotalsCard(
    roundTotal = state.roundTotal,
    allTimeTotal = state.allTimeTotal,
    roundPlayerCount = state.roundPlayerCount,
)
ShareButton(
    data = ShareCardData(
        displayTag       = state.selfEntry?.displayTag ?: "",
        rank             = state.selfEntry?.rank ?: 0,
        userScore        = state.selfEntry?.totalCount ?: 0,
        roundTotal       = state.roundTotal,
        roundPlayerCount = state.roundPlayerCount,
    )
)
```

- [ ] **Step 3: Add missing imports**

Add to the import block:

```kotlin
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.default.Share
import tools.mo3ta.salo.ui.shareImage
import tools.mo3ta.salo.ui.components.ShareCardData
```

- [ ] **Step 4: Compile check**

```bash
./gradlew :app:compileCommonMainKotlinMetadata
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Manual test on Android**

1. Build and run on device or emulator: `./gradlew :app:installDebug`
2. Open leaderboard sheet
3. Verify "مشاركة رتبتي" button appears below totals card
4. Tap button — OS share sheet opens with card image attached
5. Verify card shows correct rank, user score, community total, player count, display name
6. Tap button when rank is 0 (before data loads) — button should be visually dimmed and not trigger share

- [ ] **Step 6: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/ui/components/MohamedLoversInfoSheet.kt
git commit -m "feat: add share button to leaderboard info sheet"
```
