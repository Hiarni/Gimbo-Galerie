package de.luma.gallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.luma.gallery.R

private val licenseFiles = listOf("LICENSE", "THIRD-PARTY-NOTICES.txt", "LICENSES/Apache-2.0.txt", "LICENSES/MIT.txt")

@Composable
fun OpenSourceLicenses() {
    val strings = appStrings()
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text(strings.getString(R.string.open_source_licenses)) }
    if (open) {
        val texts = remember {
            licenseFiles.mapNotNull { name ->
                runCatching { reflowLicense(context.assets.open("licenses/$name").bufferedReader().use { it.readText() }) }.getOrNull()
            }
        }
        AlertDialog(onDismissRequest = { open = false }, title = { Text(strings.getString(R.string.open_source_licenses)) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(strings.getString(R.string.open_source_licenses_hint), style = MaterialTheme.typography.bodyMedium)
                texts.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }, confirmButton = { TextButton(onClick = { open = false }) { Text(strings.getString(R.string.close)) } })
    }
}

internal fun reflowLicense(text: String): String {
    val marker = Regex("""^(\d+\.|\([a-z0-9]{1,3}\)|[-*=]).*""")
    val out = mutableListOf<Pair<Int, StringBuilder>>()
    var previous: String? = null
    for (raw in text.replace("\r\n", "\n").trimEnd().lines()) {
        val line = raw.trimEnd()
        val indent = line.length - line.trimStart().length
        val content = line.trim()
        val last = out.lastOrNull()
        if (content.isEmpty()) out += -1 to StringBuilder()
        else if (last != null && last.first >= 0 && previous != null && previous.length >= 60 &&
            indent >= last.first && !marker.matches(content)) last.second.append(' ').append(content)
        else out += indent to StringBuilder(content)
        previous = line.ifEmpty { null }
    }
    return out.joinToString("\n") { (indent, line) -> if (indent > 0) "  $line" else line.toString() }
        .replace(Regex("\n{3,}"), "\n\n").trim()
}
