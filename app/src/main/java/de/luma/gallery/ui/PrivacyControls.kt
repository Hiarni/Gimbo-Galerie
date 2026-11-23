package de.luma.gallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.luma.gallery.R

@Composable
fun PrivacyControls() {
    val strings=appStrings()
    var open by remember {mutableStateOf(false)}
    TextButton(onClick={open=true}) {Text(strings.getString(R.string.privacy_details))}
    if(open) AlertDialog(onDismissRequest={open=false},title={Text(strings.getString(R.string.privacy_title))},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            listOf(R.string.privacy_local,R.string.privacy_cloud,R.string.privacy_rights).forEach {Text(strings.getString(it))}
        }
    },confirmButton={TextButton(onClick={open=false}) {Text(strings.getString(R.string.close))}})
}
