package tools.mo3ta.salo.ui.ghars

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.aref_ruqaa_bold
import tools.mo3ta.salo.generated.resources.aref_ruqaa_regular
import tools.mo3ta.salo.generated.resources.ibm_plex_sans_arabic_light
import tools.mo3ta.salo.generated.resources.ibm_plex_sans_arabic_medium
import tools.mo3ta.salo.generated.resources.ibm_plex_sans_arabic_regular
import tools.mo3ta.salo.generated.resources.ibm_plex_sans_arabic_semibold

/**
 * Palette — dawn over the grove. The foreground is near-silhouette against a lit
 * horizon; the only warm accent is ripe-date amber, spent on milestone fruit and nothing else.
 */
internal object GharsColors {
    val NightIndigo = Color(0xFF14103A) // top of sky — inherits the app's DeepBlue world
    val DawnRose = Color(0xFF8C4A54) // mid sky, the pre-sunrise flush
    val HorizonApricot = Color(0xFFE09A62) // the lit band the palms stand against
    val PalmDeep = Color(0xFF0B2A22) // trunks and fronds in the foreground
    val FrondLit = Color(0xFF3E8F63) // sunlit frond edges only
    val DateAmber = Color(0xFFC4762A) // the one accent — milestone fruit
    val SandPale = Color(0xFFEFE2CB) // the bottom sheet
    val Accent = Color(0xFF1F5C40) // the only deep green in the challenge row

    // Sheet ink + supporting tones (sun-bleached earth, not "cream").
    val SheetInk = Color(0xFF3A2A1C)
    val SheetMuted = Color(0xFF8A7256)
    val SheetStroke = Color(0xFFD8C7A9)
    val SheetTrack = Color(0xFFDCCCAF)
    val Gold = Color(0xFFF5D97A)
    val TextOnSky = Color(0xFFFBF2DE)
}

/**
 * The app's first custom fonts — scoped to the Ghars screen only, deliberately not
 * Cairo/Tajawal. Aref Ruqaa carries the tasbeeh, the title and the tier name; IBM Plex
 * Sans Arabic carries body text and tabular numerals.
 */
@Composable
internal fun arefRuqaaFamily(): FontFamily = FontFamily(
    Font(Res.font.aref_ruqaa_regular, FontWeight.Normal),
    Font(Res.font.aref_ruqaa_bold, FontWeight.Bold),
)

@Composable
internal fun ibmPlexArabicFamily(): FontFamily = FontFamily(
    Font(Res.font.ibm_plex_sans_arabic_light, FontWeight.Light),
    Font(Res.font.ibm_plex_sans_arabic_regular, FontWeight.Normal),
    Font(Res.font.ibm_plex_sans_arabic_medium, FontWeight.Medium),
    Font(Res.font.ibm_plex_sans_arabic_semibold, FontWeight.SemiBold),
)
