package de.luma.gallery.ui

import de.luma.gallery.R
import de.luma.gallery.localization.appLocale
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.memory.MemoryCache
import androidx.compose.ui.platform.LocalContext
import de.luma.gallery.data.ProjectPreviewFetcher
import de.luma.gallery.model.EditorProject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProjectsScreen(projects: List<EditorProject>, loading: Boolean, error: String?, onBack: () -> Unit,
    onOpen: (String) -> Unit, onRename: (String, String) -> Unit, onDelete: (String) -> Unit, onReplace: (EditorProject) -> Unit = {}, revision: Long = 0) {
    val strings = appStrings()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val imageLoader = remember(context.applicationContext) { ImageLoader.Builder(context.applicationContext)
        .components { add(ProjectPreviewFetcher.Factory(context.applicationContext)) }
        .memoryCache { MemoryCache.Builder(context.applicationContext).maxSizeBytes(8 * 1024 * 1024).build() }
        .diskCache(null).build() }
    DisposableEffect(imageLoader) { onDispose { imageLoader.shutdown() } }
    var renaming by remember { mutableStateOf<EditorProject?>(null) }
    var deleting by remember { mutableStateOf<EditorProject?>(null) }
    val locale = strings.appLocale
    val date = remember(locale) { java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT, locale) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(LumaIcons.ArrowBack, strings.getString(R.string.back)) }
            Text(strings.getString(R.string.your_projects_title), style = MaterialTheme.typography.titleLarge)
        }
        Text(strings.getString(R.string.projects_hint), Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            error != null -> Text(error, Modifier.padding(24.dp), color = MaterialTheme.colorScheme.error)
            projects.isEmpty() -> Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(LumaIcons.Projects, null, tint = MaterialTheme.colorScheme.primary)
                Text(strings.getString(R.string.projects_empty_title), style = MaterialTheme.typography.titleMedium)
                Text(strings.getString(R.string.projects_empty_hint))
            }
            else -> LazyColumn(state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(projects, key = { it.id }) { project ->
                    var menu by remember { mutableStateOf(false) }
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Row(Modifier.fillMaxWidth().clickable { onOpen(project.id) }.padding(start = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                                ProjectThumbnail(project, imageLoader, revision)
                            }
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(project.name, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(strings.getString(R.string.project_source, strings.getString(if (project.source.isVideo) R.string.video else R.string.photo), project.source.name), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(date.format(Date(project.updatedAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(LumaIcons.More, strings.getString(R.string.project_actions, project.name)) }
                                DropdownMenu(menu, { menu = false }) {
                                    DropdownMenuItem(text = { Text(strings.getString(R.string.replace_source)) }, onClick = { menu = false; onReplace(project) })
                                    DropdownMenuItem(text = { Text(strings.getString(R.string.rename)) }, onClick = { menu = false; renaming = project })
                                    DropdownMenuItem(text = { Text(strings.getString(R.string.remove_project)) }, onClick = { menu = false; deleting = project })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    renaming?.let { project -> RenameProjectDialog(project.name, { renaming = null }, { name -> onRename(project.id, name); renaming = null }) }
    deleting?.let { project -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text(strings.getString(R.string.remove_project_title)) },
        text = { Text(strings.getString(R.string.remove_project_hint, project.name)) },
        confirmButton = { TextButton(onClick = { onDelete(project.id); deleting = null }) { Text(strings.getString(R.string.remove_project)) } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text(strings.getString(R.string.cancel)) } }) }
}
