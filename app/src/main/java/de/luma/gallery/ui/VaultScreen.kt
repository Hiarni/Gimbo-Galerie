package de.luma.gallery.ui

import android.app.KeyguardManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import de.luma.gallery.R
import de.luma.gallery.data.*
import de.luma.gallery.localization.userMessage
import de.luma.gallery.model.*
import kotlinx.coroutines.*

@Composable
fun VaultScreen(library:List<GalleryMedia>,initialSources:List<GalleryMedia>,onBack:()->Unit,autoPlay:Boolean=true,onRemoveOriginals:(List<VaultItem>)->Unit) {
    val context=LocalContext.current
    val activity=androidx.activity.compose.LocalActivity.current
    val strings=appStrings()
    val state by VaultController.state.collectAsState()
    val scope=rememberCoroutineScope()
    var error by remember {mutableStateOf<String?>(null)}
    var opening by remember {mutableStateOf(false)}
    var imports by remember(initialSources) {mutableStateOf(initialSources)}
    var picking by remember {mutableStateOf(false)}
    var pickLibrary by remember {mutableStateOf<List<GalleryMedia>?>(null)}
    var albumMenu by remember {mutableStateOf(false)}
    var selected by remember {mutableStateOf(emptySet<String>())}
    var removing by remember {mutableStateOf(false)}
    var viewed by remember {mutableStateOf<VaultItem?>(null)}
    var authenticating by remember {mutableStateOf<CancellationSignal?>(null)}
    fun operation(block:suspend ()->Unit) {scope.launch {try {block()} catch(e:Exception) {if(e is CancellationException) throw e;error=e.userMessage(strings,R.string.vault_failed)} catch(e:OutOfMemoryError) {error=strings.getString(R.string.image_memory_limit)}}}
    fun unlock() {
        if((activity as? androidx.lifecycle.LifecycleOwner)?.lifecycle?.currentState?.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)!=true) return
        opening=true;operation {try {VaultController.unlock(context)} finally {opening=false}}}
    val credential=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {result->if(result.resultCode==android.app.Activity.RESULT_OK) unlock()}
    val keyguard=context.getSystemService(KeyguardManager::class.java)
    DisposableEffect(Unit) {onDispose {authenticating?.cancel();VaultController.lock(context)}}
    LaunchedEffect(state.unlocked) {if(!state.unlocked) {selected=emptySet();viewed=null;removing=false}}
    LaunchedEffect(state.running) {if(!state.running && state.unlocked) VaultController.refresh(context)}
    ImmersiveMedia(viewed!=null && state.unlocked)
    if(viewed!=null && state.unlocked) {
        VaultViewer(requireNotNull(viewed),autoPlay,onBack={viewed=null})
        return
    }
    if(picking && state.unlocked) MediaChoiceDialog((pickLibrary?:library).filter {!it.isDemo},1,5000,strings.getString(R.string.vault_add),{picking=false}) {items->picking=false;imports=items}
    LaunchedEffect(imports,state.unlocked) {if(state.unlocked && imports.size>5000) {pickLibrary=imports;imports=emptyList();picking=true}}
    if(imports.isNotEmpty() && state.unlocked) CompactDialog(strings.getString(R.string.vault_add),{imports=emptyList()},actions={
        TextButton(onClick={imports=emptyList()}) {Text(strings.getString(R.string.cancel))}
        Button(onClick={val sources=imports;imports=emptyList();operation {VaultController.start(context,sources)}},enabled=!state.running && !state.pending) {Text(strings.getString(R.string.vault_secure))}
    }) {
        Text(strings.getQuantityString(R.plurals.media_count,imports.size,imports.size))
        Text(strings.getString(R.string.vault_originals_hint))
    }
    val chosen=state.items.filter {it.id in selected}
    if(removing && chosen.isNotEmpty() && state.unlocked) CompactDialog(strings.getString(R.string.delete_action_title),{removing=false},actions={
        TextButton(onClick={removing=false}) {Text(strings.getString(R.string.cancel))}
        Button(onClick={removing=false;selected=emptySet();operation {VaultController.remove(context,chosen)}},colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error,contentColor=MaterialTheme.colorScheme.onError)) {Text(strings.getString(R.string.permanently_delete))}
    }) {Text(strings.getQuantityString(R.plurals.media_count,chosen.size,chosen.size));Text(strings.getString(R.string.vault_delete_hint))}
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onBack) {Icon(LumaIcons.ArrowBack,strings.getString(R.string.back))}
            Text(strings.getString(R.string.vault_title),Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
            if(state.unlocked) IconButton(onClick={VaultController.lock(context)}) {Icon(LumaIcons.Lock,strings.getString(R.string.vault_lock))}
        }
        if(!state.unlocked) Column(Modifier.weight(1f).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f));Icon(LumaIcons.Lock,null,Modifier.size(64.dp),tint=MaterialTheme.colorScheme.primary)
            Text(strings.getString(R.string.vault_locked),style=MaterialTheme.typography.headlineSmall)
            Text(strings.getString(R.string.vault_auth_hint))
            Button(onClick={
                if(!keyguard.isDeviceSecure) context.startActivity(android.content.Intent(Settings.ACTION_SECURITY_SETTINGS))
                else if(Build.VERSION.SDK_INT<30 && context.getSystemService(android.hardware.biometrics.BiometricManager::class.java).canAuthenticate()!=android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS) {
                    credential.launch(keyguard.createConfirmDeviceCredentialIntent(strings.getString(R.string.vault_title),strings.getString(R.string.vault_auth_hint)))
                } else if(activity!=null) {
                    val signal=CancellationSignal();authenticating=signal
                    val builder=BiometricPrompt.Builder(context).setTitle(strings.getString(R.string.vault_unlock)).setSubtitle(strings.getString(R.string.vault_auth_hint))
                    if(Build.VERSION.SDK_INT>=30) builder.setAllowedAuthenticators(android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG or android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                    else builder.setDeviceCredentialAllowed(true)
                    builder.build().authenticate(signal,context.mainExecutor,object:BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(result:BiometricPrompt.AuthenticationResult) {authenticating=null;unlock()}
                            override fun onAuthenticationError(code:Int,message:CharSequence) {authenticating=null;if(code !in listOf(BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED,BiometricPrompt.BIOMETRIC_ERROR_CANCELED)) error=strings.getString(R.string.vault_auth_failed)}
                        })
                }
            },enabled=!opening && authenticating==null) {Text(strings.getString(if(keyguard.isDeviceSecure) R.string.vault_unlock else R.string.vault_setup_lock))}
            if(keyguard.isDeviceSecure) TextButton(onClick={credential.launch(keyguard.createConfirmDeviceCredentialIntent(strings.getString(R.string.vault_title),strings.getString(R.string.vault_auth_hint)))},enabled=!opening && authenticating==null) {Text(strings.getString(R.string.vault_device_lock))}
            if(opening) CircularProgressIndicator()
            error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            Text(strings.getString(R.string.vault_local_hint),style=MaterialTheme.typography.bodySmall)
            Spacer(Modifier.weight(1f))
        } else {
            Row(Modifier.padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick={pickLibrary=null;picking=true},enabled=!state.running && !state.pending) {Icon(LumaIcons.Add,null);Text(strings.getString(R.string.vault_add))}
                Box {
                    OutlinedButton(onClick={albumMenu=true},enabled=!state.running && !state.pending) {Icon(LumaIcons.FolderOpen,null);Text(strings.getString(R.string.album))}
                    DropdownMenu(albumMenu,{albumMenu=false}) {
                        library.filter {!it.isImported && !it.isDemo}.groupBy {it.album}.toSortedMap().forEach {(album,media)->
                            DropdownMenuItem(text={Text(album)},onClick={albumMenu=false;imports=media})
                        }
                    }
                }
            }
            if(state.running) Column(Modifier.padding(16.dp)) {
                LinearProgressIndicator(progress={if(state.total==0) 0f else state.done.toFloat()/state.total},modifier=Modifier.fillMaxWidth())
                Text(strings.getString(R.string.review_progress,state.done,state.total))
                TextButton(onClick={VaultController.pause(context)}) {Text(strings.getString(R.string.background_pause))}
            } else if(state.pending) TextButton(onClick={operation {VaultController.resume(context)}}) {Text(strings.getString(R.string.background_resume))}
            (error?:state.error)?.let {Text(it,Modifier.padding(16.dp),color=MaterialTheme.colorScheme.error)}
            if(state.items.isEmpty()) Text(strings.getString(R.string.vault_empty),Modifier.padding(24.dp))
            LazyVerticalGrid(GridCells.Adaptive(108.dp),Modifier.weight(1f),contentPadding=PaddingValues(12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                items(state.items,key={it.id}) {item->
                    Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(18.dp)).clickable {
                        if(selected.isEmpty()) viewed=item else selected=if(item.id in selected) selected-item.id else selected+item.id
                    }) {
                        VaultThumbnail(item,Modifier.fillMaxSize())
                        RoundCheckbox(item.id in selected,{selected=if(it) selected+item.id else selected-item.id},Modifier.align(Alignment.TopEnd),enabled=!state.running)
                        if(item.media.isVideo) Icon(LumaIcons.PlayArrow,strings.getString(R.string.videos),Modifier.align(Alignment.BottomStart).padding(8.dp),tint=androidx.compose.ui.graphics.Color.White)
                    }
                }
            }
            if(chosen.isNotEmpty()) Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(strings.getQuantityString(R.plurals.media_count,chosen.size,chosen.size))
                if(chosen.count {canManageMedia(it.media)}>MEDIA_ACTION_LIMIT) Text(strings.getString(R.string.media_action_limit,MEDIA_ACTION_LIMIT),style=MaterialTheme.typography.bodySmall)
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    item {OutlinedButton(onClick={operation {VaultController.export(context,chosen);selected=emptySet()}},enabled=!state.running && !state.pending) {Text(strings.getString(R.string.vault_restore_gallery))}}
                    item {TextButton(onClick={onRemoveOriginals(chosen.filter {canManageMedia(it.media)}.take(MEDIA_ACTION_LIMIT))},enabled=!state.running && chosen.count {canManageMedia(it.media)} in 1..MEDIA_ACTION_LIMIT) {Text(strings.getString(R.string.vault_remove_originals))}}
                    item {TextButton(onClick={removing=true},enabled=!state.running) {Text(strings.getString(R.string.delete))}}
                }
            }
            Text(strings.getString(R.string.vault_originals_hint),Modifier.padding(horizontal=16.dp,vertical=8.dp),style=MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun VaultThumbnail(item:VaultItem,modifier:Modifier=Modifier,maxSide:Int=384) {
    val context=LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null,item.id,maxSide) {
        try {value=VaultController.bitmap(context,item,maxSide)} catch(e:CancellationException) {throw e} catch(_:Exception) {} catch(_:OutOfMemoryError) {}
    }
    Surface(modifier,color=MaterialTheme.colorScheme.surfaceContainerHigh) {
        if(bitmap!=null) Image(requireNotNull(bitmap).asImageBitmap(),item.media.name,Modifier.fillMaxSize(),contentScale=if(maxSide>384) ContentScale.Fit else ContentScale.Crop)
        else Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {Icon(if(item.media.isVideo) LumaIcons.PlayArrow else LumaIcons.PhotoLibrary,item.media.name)}
    }
}

@Composable
private fun VaultViewer(item:VaultItem,autoPlay:Boolean,onBack:()->Unit) {
    val strings=appStrings();val context=LocalContext.current
    var scale by remember {mutableFloatStateOf(1f)}
    var pan by remember {mutableStateOf(androidx.compose.ui.geometry.Offset.Zero)}
    var video by remember {mutableStateOf<android.media.MediaPlayer?>(null)}
    var playing by remember {mutableStateOf(false)}
    var position by remember {mutableFloatStateOf(0f)}
    var duration by remember {mutableFloatStateOf(0f)}
    var error by remember {mutableStateOf(false)}
    var controls by remember {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    var mediaSource by remember {mutableStateOf<VaultDataSource?>(null)}
    val player=remember(item.id) {if(item.media.isVideo) android.media.MediaPlayer() else null}
    DisposableEffect(player) {onDispose {runCatching {player?.release()};runCatching {mediaSource?.close()}}}
    LaunchedEffect(player) {while(isActive && player!=null) {delay(300);runCatching {position=player.currentPosition.toFloat();playing=player.isPlaying}}}
    androidx.activity.compose.BackHandler(onBack=onBack)
    Column(Modifier.fillMaxSize().safeDrawingPadding().pointerInput(Unit) {detectTapGestures(onTap={controls=!controls})}) {
        if(controls) Row(verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onBack) {Icon(LumaIcons.ArrowBack,strings.getString(R.string.back))}
            Text(item.media.name,Modifier.weight(1f),maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        if(player!=null) AndroidView(factory={android.view.SurfaceView(it).apply {setSecure(true);holder.addCallback(object:android.view.SurfaceHolder.Callback {
            override fun surfaceCreated(holder:android.view.SurfaceHolder) {
                player.setDisplay(holder)
                scope.launch {
                    try {
                        val master=VaultController.keyCopy()
                        val source=try {withContext(Dispatchers.IO) {VaultRepository(context).dataSource(master,item,VaultController.sessionCheck())}} finally {master.fill(0)}
                        mediaSource=source
                        player.setDataSource(source)
                        player.setOnPreparedListener {duration=it.duration.toFloat();video=it;if(autoPlay) {it.start();playing=true}}
                        player.setOnErrorListener {_,_,_->error=true;true};player.prepareAsync()
                    } catch(e:CancellationException) {throw e} catch(_:Exception) {error=true}
                }
            }
            override fun surfaceChanged(holder:android.view.SurfaceHolder,format:Int,width:Int,height:Int) {}
            override fun surfaceDestroyed(holder:android.view.SurfaceHolder) {runCatching {player.setDisplay(null)}}
        })}},modifier=Modifier.fillMaxWidth().weight(1f))
        else Box(Modifier.fillMaxWidth().weight(1f).clip(androidx.compose.ui.graphics.RectangleShape).pointerInput(Unit) {detectTransformGestures {_,delta,zoom,_->scale=(scale*zoom).coerceIn(1f,5f);pan=if(scale==1f) androidx.compose.ui.geometry.Offset.Zero else pan+delta}}) {
            VaultThumbnail(item,Modifier.fillMaxSize().graphicsLayer {scaleX=scale;scaleY=scale;translationX=pan.x;translationY=pan.y},2048)
        }
        if(video!=null && controls) Row(Modifier.padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick={if(playing) player?.pause() else player?.start();playing=!playing}) {Icon(if(playing) LumaIcons.Stop else LumaIcons.PlayArrow,strings.getString(if(playing) R.string.vault_pause_video else R.string.play_clip))}
            Slider(position,{position=it;player?.seekTo(it.toInt())},Modifier.weight(1f),valueRange=0f..maxOf(duration,1f))
        }
        if(error) Text(strings.getString(R.string.video_play_failed),Modifier.padding(16.dp),color=MaterialTheme.colorScheme.error)
    }
}
