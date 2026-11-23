package de.luma.gallery.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp


object LumaIcons {
    val Lock by lazy {icon("Tresor","M5,10H19V22H5ZM8,10V6A4,4 0,0 1,16,6V10M12,15V18")}
    val Stop by lazy {icon("Pause","M8,5V19M16,5V19")}
    private fun icon(name: String, paths: String): ImageVector = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(PathParser().parsePathString(paths).toNodes(), fill = null, stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round).build()

    val Sun by lazy { icon("Licht", "M17,12A5,5 0,1 1,7,12A5,5 0,1 1,17,12M12,1V3M12,21V23M1,12H3M21,12H23M4,4L6,6M18,18L20,20M4,20L6,18M18,6L20,4") }
    val Palette by lazy { icon("Farbe", "M12,3C0,3 0,21 12,21C18,21 12,15 18,15C24,15 24,3 12,3ZM6,10H7M9,6H10M15,6H16M19,10H20") }
    val Curve by lazy { icon("Kurven", "M3,3V21H21M4,19C10,19 8,5 20,4") }
    val Crop by lazy { icon("Zuschnitt", "M7,2V17H22M2,7H17V22M7,7H17V17") }
    val Text by lazy { icon("Text", "M4,4H20M12,4V21M8,21H16") }
    val Brush by lazy { icon("Zeichnen", "M9,15L19,3L22,6L12,17M9,15C3,12 8,22 2,21C10,26 16,21 12,17") }
    val Layers by lazy { icon("Ebenen", "M2,8L12,2L22,8L12,14ZM2,13L12,19L22,13M2,18L12,24L22,18") }
    val Mask by lazy { icon("Auswahl", "M3,8V3H8M16,3H21V8M21,16V21H16M8,21H3V16M17,12A5,5 0,1 1,7,12A5,5 0,1 1,17,12") }
    val ArrowBack by lazy { icon("Zurück", "M19,12H5M12,5L5,12L12,19") }
    val ArrowForward by lazy { icon("Weiter", "M5,12H19M12,5L19,12L12,19") }
    val PhotoLibrary by lazy { icon("Medien", "M6,4H20V18H6ZM3,7V21H17M7,15L11,10L15,15L17,12L20,16M14,7H15") }
    val FolderOpen by lazy { icon("Album", "M3,7V5H9L11,7H21V10M3,7V20H19L22,10H7L3,20") }
    val Search by lazy { icon("Suche", "M17,10A7,7 0,1 1,3,10A7,7 0,1 1,17,10M15,15L21,21") }
    val Close by lazy { icon("Schließen", "M6,6L18,18M18,6L6,18") }
    val Share by lazy { icon("Teilen", "M8,11L16,6M8,13L16,18M8,12A3,3 0,1 1,2,12A3,3 0,1 1,8,12M22,5A3,3 0,1 1,16,5A3,3 0,1 1,22,5M22,19A3,3 0,1 1,16,19A3,3 0,1 1,22,19") }
    val FavoriteBorder by lazy { icon("Favorit", "M12,21L3,12C-2,5 6,-1 12,6C18,-1 26,5 21,12Z") }
    val Favorite by lazy { ImageVector.Builder("Favorit", 24.dp, 24.dp, 24f, 24f)
        .addPath(PathParser().parsePathString("M12,21L3,12C-2,5 6,-1 12,6C18,-1 26,5 21,12Z").toNodes(), fill = SolidColor(Color.Black)).build() }
    val Archive by lazy { icon("Archiv", "M3,3H21V8H3ZM5,8V21H19V8M9,12H15") }
    val Cloud by lazy { icon("Cloud", "M6,18C0,18 0,10 6,10C6,2 18,2 18,10C25,10 25,18 18,18ZM12,10V21M8,17L12,21L16,17") }
    val Copy by lazy { icon("Kopieren", "M8,8H21V21H8ZM16,4H4V16") }
    val Move by lazy { icon("Verschieben", "M3,5H10V19H3M9,12H22M17,7L22,12L17,17") }
    val AutoAwesome by lazy { icon("Filter", "M12,3L14,9L20,12L14,15L12,21L10,15L4,12L10,9ZM21,2V6M19,4H23") }
    val Info by lazy { icon("Info", "M22,12A10,10 0,1 1,2,12A10,10 0,1 1,22,12M12,11V17M12,7V7.2") }
    val Tune by lazy { icon("Anpassen", "M3,6H7M11,6H21M3,12H15M19,12H21M3,18H5M9,18H21M7,3V9H11V3ZM15,9V15H19V9ZM5,15V21H9V15Z") }
    val Add by lazy { icon("Hinzufügen", "M12,5V19M5,12H19") }
    val FilterHdr by lazy { icon("HDR", "M2,19L10,5L18,19ZM14,13L18,7L23,19H18M7,10L10,12L13,10") }
    val CheckCircle by lazy { icon("Ausgewählt", "M22,12A10,10 0,1 1,2,12A10,10 0,1 1,22,12M7,12L10,15L17,8") }
    val Check by lazy { icon("Bestätigen", "M4,12L9,17L20,6") }
    val DeleteOutline by lazy { icon("Papierkorb", "M3,6H21M9,6V3H15V6M5,6L6,21H18L19,6M10,10V17M14,10V17") }
    val PrivacyTip by lazy { icon("Privat", "M12,2L21,6V12C21,17 16,21 12,23C8,21 3,17 3,12V6ZM8,12L11,15L16,9") }
    val Undo by lazy { icon("Rückgängig", "M9,5L3,11L9,17M3,11H15C21,11 23,18 19,21") }
    val Refresh by lazy {icon("Aktualisieren", "M20,7V3M20,7H16M20,7A9,9 0,1 0,21,15")}
    val Redo by lazy { icon("Wiederholen", "M15,5L21,11L15,17M21,11H9C3,11 1,18 5,21") }
    val Compare by lazy { icon("Vergleich", "M12,2V22M9,5H3V19H9M15,5H21V19H15") }
    val Rotate90DegreesCw by lazy { icon("Drehen", "M3,11A9,9 0,1 1,12,21M3,11V4M3,11H10M10,10H17V17H10Z") }
    val Flip by lazy { icon("Spiegeln", "M12,2V22M3,18L9,5V18ZM21,18L15,5V18Z") }
    val PlayArrow by lazy { icon("Abspielen", "M7,3L21,12L7,21Z") }
    val Projects by lazy { icon("Projekte", "M4,5H14V9M4,5V20H19V16M10,16L11,12L19,4L22,7L14,15ZM17,6L20,9") }
    val Settings by lazy { icon("Einstellungen", "M9,3H15L16,6L19,7L22,11V13L19,17L16,18L15,21H9L8,18L5,17L2,13V11L5,7L8,6ZM16,12A4,4 0,1 1,8,12A4,4 0,1 1,16,12") }
    val Sort by lazy { icon("Sortierung", "M3,5H15M3,11H11M3,17H7M18,5V21M14,17L18,21L22,17") }
    val ChevronDown by lazy { icon("Öffnen", "M6,9L12,15L18,9") }
    val Audio by lazy { icon("Audio", "M3,10H7L12,5V19L7,14H3ZM16,8C20,10 20,14 16,16M19,4C26,9 26,15 19,20") }
    val VolumeOff by lazy { icon("Ton aus", "M3,10H7L12,5V19L7,14H3ZM16,9L22,15M22,9L16,15") }
    val Timeline by lazy { icon("Zeitleiste", "M2,5H22V19H2ZM8,5V19M16,5V19M5,2V5M12,2V5M19,2V5") }
    val Duplicates by lazy {icon("Duplikate", "M8,8H21V21H8ZM16,3H3V16M9,18L12,14L15,17L18,13L21,18")}
    val Collage by lazy { icon("Collage", "M3,3H21V21H3ZM13,3V21M3,13H13M13,10H21") }
    val Screenshot by lazy { icon("Screenshot", "M7,2H17V22H7ZM7,6H17M7,18H17M10,20H14") }
    val Review by lazy { icon("Aussortieren", "M6,3H18V21H6ZM1,12H5M3,10L1,12L3,14M19,12H23M21,10L23,12L21,14M9,12L11,14L15,9") }
    val More by lazy { icon("Aktionen", "M6,12A1,1 0,1 1,4,12A1,1 0,1 1,6,12M13,12A1,1 0,1 1,11,12A1,1 0,1 1,13,12M20,12A1,1 0,1 1,18,12A1,1 0,1 1,20,12") }
}
