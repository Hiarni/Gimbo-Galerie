package de.luma.gallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import de.luma.gallery.R
import de.luma.gallery.model.*

@Composable
fun MediaSortDialog(settings:AppSettings,onDismiss:()->Unit,onChange:(AppSettings)->Unit) {
    val strings=appStrings()
    AlertDialog(onDismissRequest=onDismiss,title={Text(strings.getString(R.string.sort_media))},text={
        Column {
            MediaSort.entries.forEach {sort->
                TextButton(onClick={onChange(settings.copy(sort=sort,newestFirst=if(sort==settings.sort) settings.newestFirst else sort in listOf(MediaSort.DATE,MediaSort.SIZE,MediaSort.DURATION)));onDismiss()},modifier=Modifier.fillMaxWidth()) {
                    RadioButton(settings.sort==sort,onClick=null)
                    Text(strings.getString(sort.labelRes),Modifier.weight(1f).padding(start=8.dp))
                }
            }
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(strings.getString(R.string.sort_descending),Modifier.weight(1f))
                Switch(settings.newestFirst,{onChange(settings.copy(newestFirst=it))})
            }
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(strings.getString(R.string.group_by_day),Modifier.weight(1f))
                Switch(settings.groupByDay,{onChange(settings.copy(groupByDay=it))},enabled=settings.sort==MediaSort.DATE)
            }
        }
    },confirmButton={TextButton(onClick=onDismiss){Text(strings.getString(R.string.close))}})
}
