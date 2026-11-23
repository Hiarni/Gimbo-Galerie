package de.luma.gallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.luma.gallery.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GalleryNavigation(page:Int,reviewEnabled:Boolean=true,reviewResume:Boolean=false,onNavigate:(Int)->Unit) {
    val strings=appStrings()
    val enlarged=LocalDensity.current.fontScale>1.35f
    Surface(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=12.dp,vertical=8.dp),shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().selectableGroup(),verticalAlignment=Alignment.CenterVertically) {
            listOf(R.string.photos to LumaIcons.PhotoLibrary,R.string.videos to LumaIcons.PlayArrow,R.string.review_title to LumaIcons.Review,R.string.albums to LumaIcons.FolderOpen,R.string.search to LumaIcons.Search).forEachIndexed {index,(resource,icon)->
                val label=strings.getString(resource)
                val review=index==2
                val selected=!review && page==index
                val enabled=!review || reviewEnabled
                val description=if(review && reviewResume) strings.getString(R.string.review_resume) else label
                Box(Modifier.weight(if(enlarged && selected) 2f else if(!enlarged && review) 1.5f else 1f)) {
                    TooltipBox(positionProvider=TooltipDefaults.rememberPlainTooltipPositionProvider(),tooltip={PlainTooltip {Text(description)}},state=rememberTooltipState(),modifier=Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().heightIn(min=72.dp).clip(RoundedCornerShape(24.dp)).then(if(review) Modifier.clickable(enabled=enabled,role=Role.Button,onClick={onNavigate(index)}) else Modifier.selectable(selected=selected,role=Role.Tab,onClick={onNavigate(index)})).semantics {contentDescription=description}.padding(horizontal=2.dp,vertical=10.dp),
                        horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Surface(shape=RoundedCornerShape(50),color=if(review && enabled) MaterialTheme.colorScheme.primary else if(selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent) {
                            Box(Modifier.size(width=44.dp,height=30.dp),contentAlignment=Alignment.Center) {Icon(icon,null,Modifier.size(23.dp),tint=if(review && enabled) MaterialTheme.colorScheme.onPrimary else if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=if(enabled) 1f else .38f))}
                        }
                        Box(Modifier.fillMaxWidth().heightIn(min=with(LocalDensity.current) {MaterialTheme.typography.labelSmall.lineHeight.toDp()}+4.dp),contentAlignment=Alignment.Center) {
                            if(!enlarged || selected) Text(label,Modifier.fillMaxWidth(),style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis,textAlign=TextAlign.Center,color=if(selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                }
            }
        }
    }
}
