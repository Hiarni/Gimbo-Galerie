package de.luma.gallery.ui

import de.luma.gallery.R
import de.luma.gallery.BuildConfig
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.luma.gallery.GalleryState
import de.luma.gallery.model.AppSettings
import de.luma.gallery.model.ThemeMode
import de.luma.gallery.localization.AppLanguage

@Composable
fun SettingsScreen(state: GalleryState, onBack: () -> Unit, onSettings: (AppSettings) -> Unit,
    onImport: () -> Unit, onTrash: () -> Unit,
    language: AppLanguage = AppLanguage.current(), onLanguage: (AppLanguage) -> Unit = { it.select() }, onRetrySettings: () -> Unit = {}, onFiles: () -> Unit = {}, onSaveBackup: () -> Unit = {}, onReadBackup: () -> Unit = {},
    onSaveArchive: () -> Unit = {}, onReadArchive: () -> Unit = {}, onCancelBackup: () -> Unit = {}) {
    val strings = appStrings()
    var sorting by remember {mutableStateOf(false)}
    if(sorting) MediaSortDialog(state.settings,{sorting=false},onSettings)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(LumaIcons.ArrowBack, strings.getString(R.string.back)) }
            Text(strings.getString(R.string.style_title), fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        }
        LazyColumn(modifier=Modifier.semantics {contentDescription=strings.getString(R.string.settings)},contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            item {
                Text(strings.getString(R.string.language_heading), fontSize = 11.sp, letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                LanguageControls(language, onLanguage)
            }
            item {
                if (state.settingsLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.settingsError?.let { error ->
                    Text(error, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetrySettings) { Text(strings.getString(R.string.retry_settings)) }
                }
                Text(strings.getString(R.string.appearance_heading), fontSize = 11.sp, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ThemeMode.entries.forEach { mode -> FilterChip(selected = state.settings.theme == mode,
                        onClick = { onSettings(state.settings.copy(theme = mode)) }, label = { Text(strings.getString(mode.labelRes)) }, enabled = state.settingsReady) }
                }
                Spacer(Modifier.height(16.dp))
                Text(strings.getString(R.string.accent_color), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(12.dp))
                AccentPicker(state.settings, state.settingsReady, onSettings)
            }
            item {
                SettingsCategory(strings.getString(R.string.category_editor)) {AppearanceControls(state.settings,state.settingsReady,onSettings)}
            }
            item { SettingsCategory(strings.getString(R.string.category_gallery)) {
                Text(strings.getString(R.string.gallery_grid), style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (2..8).forEach { count -> item { FilterChip(state.settings.columns == count,
                        { onSettings(state.settings.copy(columns = count)) }, { Text("$count") }, enabled = state.settingsReady) } }
                }
                Text(strings.getString(R.string.pinch_grid_hint),style=MaterialTheme.typography.bodySmall)
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(strings.getString(R.string.remember_grid),Modifier.weight(1f))
                    Switch(state.settings.rememberGrid,{onSettings(state.settings.copy(rememberGrid=it))},enabled=state.settingsReady)
                }
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(strings.getString(R.string.auto_play_videos),Modifier.weight(1f))
                    Switch(state.settings.autoPlayVideos,{onSettings(state.settings.copy(autoPlayVideos=it))},enabled=state.settingsReady,modifier=Modifier.semantics {contentDescription=strings.getString(R.string.auto_play_videos)})
                }
                Text(strings.getString(R.string.auto_play_videos_hint),style=MaterialTheme.typography.bodySmall)
                ReviewGestureSettings(state.settings,state.settingsReady,onSettings)
                OutlinedButton(onClick={sorting=true},enabled=state.settingsReady,modifier=Modifier.fillMaxWidth()) {
                    Text(strings.getString(R.string.sort_media)+": "+strings.getString(state.settings.sort.labelRes))
                }
                }
            }
            item {SettingsCategory(strings.getString(R.string.category_export)) {ExportNotificationControls()}}
            item {SettingsCategory(strings.getString(R.string.cloud_title)) {CloudBackupControls()}}
            item { SettingsCategory(strings.getString(R.string.trash_retention)) {
                Text(strings.getString(R.string.trash_retention),style=MaterialTheme.typography.titleMedium)
                EditorSlider(strings.getString(R.string.retention_days),state.settings.trashDays.toFloat(),0f..30f,
                    {onSettings(state.settings.copy(trashDays=it.toInt()))},{})
                Text(strings.getString(if(state.settings.trashDays==0) R.string.delete_immediately_hint else R.string.retention_hint),style=MaterialTheme.typography.bodySmall)
                Text(strings.getString(R.string.retention_system_limit),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { SettingsCategory(strings.getString(R.string.category_storage)) {
                Text(strings.getString(R.string.media_management_heading), fontSize = 11.sp, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onTrash, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Icon(LumaIcons.DeleteOutline, null); Spacer(Modifier.width(8.dp)); Text(strings.getString(R.string.open_trash))
                }
                OutlinedButton(onClick = onFiles, modifier = Modifier.fillMaxWidth()) { Text(strings.getString(R.string.file_tools)) }
                Text(strings.getString(R.string.backup_hint), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = onSaveBackup, enabled = !state.backupBusy, modifier = Modifier.fillMaxWidth()) { Text(strings.getString(R.string.backup_save)) }
                OutlinedButton(onClick = onReadBackup, enabled = !state.backupBusy, modifier = Modifier.fillMaxWidth()) { Text(strings.getString(R.string.backup_open)) }
                Text(strings.getString(R.string.archive_hint), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = onSaveArchive, enabled = !state.backupBusy, modifier = Modifier.fillMaxWidth()) { Text(strings.getString(R.string.archive_save)) }
                OutlinedButton(onClick = onReadArchive, enabled = !state.backupBusy, modifier = Modifier.fillMaxWidth()) { Text(strings.getString(R.string.archive_open)) }
                if (state.backupBusy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    TextButton(onClick = onCancelBackup) { Text(strings.getString(R.string.cancel)) }
                }
                Text(strings.getString(R.string.trash_settings_hint),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(LumaIcons.PrivacyTip, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(strings.getString(R.string.privacy_title), fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(10.dp))
                PrivacyControls()
                OpenSourceLicenses()
                Text(strings.getString(R.string.privacy_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = onImport, modifier = Modifier.padding(top = 12.dp)) { Text(strings.getString(R.string.open_storage)) }
            }
            item { Text("${strings.getString(R.string.app_name)} · ${BuildConfig.VERSION_NAME}", style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }

        }
    }
}
