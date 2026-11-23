package de.luma.gallery.ui

import de.luma.gallery.R
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.luma.gallery.model.projectName

@Composable
fun SaveProjectDialog(initialName: String, existing: Boolean, onDismiss: () -> Unit, onSave: (String, Boolean) -> Unit) {
    val strings = appStrings()
    var name by remember { mutableStateOf(initialName.take(80)) }
    var asCopy by remember { mutableStateOf(false) }
    val valid = runCatching { projectName(name) }.isSuccess
    CompactDialog(if(existing) strings.getString(R.string.save_project) else strings.getString(R.string.new_project),onDismiss,actions={
        TextButton(onClick=onDismiss) {Text(strings.getString(R.string.cancel))}
        TextButton(onClick={onSave(projectName(name),asCopy)},enabled=valid) {Text(strings.getString(R.string.save_project))}
    }) {
            OutlinedTextField(name, { name = it.take(80) }, label = { Text(strings.getString(R.string.project_name)) }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), supportingText = { Text(strings.getString(R.string.name_character_limit, name.length)) })
            if (existing) Row(verticalAlignment = Alignment.CenterVertically) {
                RoundCheckbox(asCopy, { asCopy = it }, modifier = Modifier.semantics { contentDescription = strings.getString(R.string.save_new_project) }); Text(strings.getString(R.string.save_new_project))
            }
            Text(strings.getString(R.string.project_save_hint), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun RenameProjectDialog(initialName: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    val strings = appStrings()
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(strings.getString(R.string.rename_project)) }, text = {
        OutlinedTextField(name, { name = it.take(80) }, label = { Text(strings.getString(R.string.project_name)) }, singleLine = true)
    }, confirmButton = { TextButton(onClick = { onRename(projectName(name)) }, enabled = runCatching { projectName(name) }.isSuccess) { Text(strings.getString(R.string.rename)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.getString(R.string.cancel)) } })
}

@Composable
fun SourceReplacementDialog(review: de.luma.gallery.model.SourceReplacement, saving: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val strings = appStrings()
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text(strings.getString(R.string.replace_source)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(strings.getString(R.string.source_replace_hint))
            val old = review.previousInfo
            Text(if (old == null) strings.getString(R.string.source_old_unavailable, review.project.source.name)
                else strings.getString(R.string.source_dimensions, review.project.source.name, old.width, old.height))
            Text(strings.getString(R.string.source_dimensions, review.source.name, review.replacementInfo.width, review.replacementInfo.height))
        }
    }, confirmButton = { TextButton(onClick = onConfirm, enabled = !saving) { Text(strings.getString(R.string.replace_source)) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text(strings.getString(R.string.cancel)) } })
}
