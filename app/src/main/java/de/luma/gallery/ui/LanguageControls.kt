package de.luma.gallery.ui

import android.content.res.Resources
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import de.luma.gallery.R
import de.luma.gallery.localization.AppLanguage
import de.luma.gallery.localization.localizedResources
import de.luma.gallery.localization.appLocale
import java.util.Locale

@Composable
internal fun appStrings(): Resources {
    LocalConfiguration.current
    return localizedResources(LocalContext.current)
}

@Composable
fun LanguageControls(language: AppLanguage, onLanguage: (AppLanguage) -> Unit) {
    val strings=appStrings();var open by remember {mutableStateOf(false)};var query by remember {mutableStateOf("")}
    fun label(option:AppLanguage)=option.nativeLabel ?: strings.getString(option.labelRes)
    OutlinedButton(onClick={query="";open=true},modifier=Modifier.fillMaxWidth().semantics {selected=true;contentDescription=strings.getString(R.string.choose_language)}) {
        Text(label(language),Modifier.weight(1f));Text("▾")
    }
    Text(strings.getString(R.string.language_hint),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    if(open) AlertDialog(onDismissRequest={open=false},title={Text(strings.getString(R.string.choose_language))},text={
        Column {
            OutlinedTextField(query,{query=it},singleLine=true,label={Text(strings.getString(R.string.search_language))},modifier=Modifier.fillMaxWidth())
            val choices=AppLanguage.entries.filter {
                query.isBlank() || label(it).contains(query,true) || it.tag.contains(query,true) ||
                    Locale.forLanguageTag(it.tag).getDisplayLanguage(strings.appLocale).contains(query,true) ||
                    Locale.forLanguageTag(it.tag).getDisplayLanguage(Locale.ENGLISH).contains(query,true)
            }
            LazyColumn(Modifier.heightIn(max=320.dp)) {
                items(choices,key={it.name}) {option->
                    ListItem(headlineContent={Text(label(option))},supportingContent={
                        if(option.nativeLabel!=null) Text(Locale.forLanguageTag(option.tag).getDisplayLanguage(strings.appLocale))
                    },leadingContent={RadioButton(language==option,onClick=null)},modifier=Modifier.fillMaxWidth()
                        .semantics {selected=language==option}.clickable {open=false;onLanguage(option)})
                }
            }
        }
    },confirmButton={TextButton(onClick={open=false}) {Text(strings.getString(R.string.close))}})
}
