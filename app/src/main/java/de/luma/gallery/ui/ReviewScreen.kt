package de.luma.gallery.ui

import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.luma.gallery.R
import de.luma.gallery.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

internal val LocalReviewDraft=staticCompositionLocalOf<de.luma.gallery.data.ReviewDraft?> {null}

@Composable
fun ReviewScreen(ids:List<String>,media:List<GalleryMedia>,settings:AppSettings,onBack:()->Unit,onApply:(ReviewSession)->Unit) {
    val strings=appStrings()
    val haptic=LocalHapticFeedback.current
    val draft=LocalReviewDraft.current
    var decisions by rememberSaveable(ids) {mutableStateOf(draft?.read()?.takeIf {it.ids==ids}?.decisions?.map {it.name}.orEmpty())}
    val session=ReviewSession(ids,decisions.map {ReviewAction.valueOf(it)})
    val item=media.find {it.id==session.currentId}
    val next=media.find {it.id==ids.getOrNull(decisions.size+1)}
    var finish by remember {mutableStateOf(false)}
    var leaving by remember {mutableStateOf(false)}
    var busy by remember {mutableStateOf(false)}
    var feedback by remember {mutableStateOf<ReviewAction?>(null)}
    var feedbackKey by remember {mutableIntStateOf(0)}
    var feedbackVisible by remember {mutableStateOf(false)}
    var undoFeedback by remember {mutableStateOf(false)}
    val animate=settings.reviewPlayful && settings.thumbnailAnimations
    var actionsOpen by remember {mutableStateOf(false)}
    var videoMuted by rememberSaveable(ids) {mutableStateOf(true)}
    fun choose(action:ReviewAction) {
        val current=ReviewSession(ids,decisions.map {ReviewAction.valueOf(it)})
        if(current.finished || current.currentId!=session.currentId || finish || leaving || busy) return
        val updated=current.choose(action)
        draft?.save(updated)
        decisions=updated.decisions.map {it.name}
        feedback=action;feedbackKey++;undoFeedback=false;feedbackVisible=true
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    fun undo() {
        if(busy) return
        val updated=session.undo()
        draft?.save(updated)
        decisions=updated.decisions.map {it.name};feedbackVisible=false;undoFeedback=true;feedbackKey++
    }
    LaunchedEffect(feedbackKey) {
        if(feedbackVisible || undoFeedback) {delay(1200);feedbackVisible=false;undoFeedback=false}
    }
    val leave={if(decisions.isEmpty()) onBack() else leaving=true}
    androidx.activity.compose.BackHandler {leave()}
    if(leaving) CompactDialog(strings.getString(R.string.review_discard_title),{leaving=false},actions={
        TextButton(onClick={leaving=false}) {Text(strings.getString(R.string.cancel))}
        TextButton(onClick=onBack) {Text(strings.getString(R.string.review_discard))}
    }) {Text(strings.getString(R.string.review_discard_hint))}
    if(finish) CompactDialog(strings.getString(R.string.review_summary),{finish=false},actions={
        TextButton(onClick={finish=false}) {Text(strings.getString(R.string.back))}
        Button(onClick={finish=false;onApply(session)}) {Text(strings.getString(R.string.review_apply))}
    }) {
        Text(strings.getString(R.string.review_progress,decisions.size,ids.size),style=MaterialTheme.typography.titleMedium)
        ReviewAction.entries.forEach {action->
            val count=session.selected(action).size
            if(count>0) Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                ReviewActionGlyph(action,Modifier.size(40.dp))
                Text(strings.getString(R.string.review_action_count,strings.getString(action.labelRes),count))
            }
        }
        if(!session.finished) Text(strings.getString(R.string.review_remaining,ids.size-decisions.size))
        Text(strings.getString(R.string.review_confirm_hint),style=MaterialTheme.typography.bodySmall)
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().semantics {paneTitle=strings.getString(R.string.review_title)}) {
        val compact=LocalDensity.current.fontScale>1.25f || maxHeight<600.dp
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
                LabeledIconButton(LumaIcons.ArrowBack,strings.getString(R.string.back),leave)
                Column(Modifier.weight(1f).padding(horizontal=8.dp)) {
                    if(!compact) Text(strings.getString(R.string.review_title),color=MaterialTheme.colorScheme.onSurface,style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Text(strings.getString(R.string.review_progress,decisions.size,ids.size),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Spacer(Modifier.height(5.dp))
                    LinearProgressIndicator(progress={if(ids.isEmpty()) 1f else decisions.size.toFloat()/ids.size},modifier=Modifier.fillMaxWidth().height(2.dp),trackColor=MaterialTheme.colorScheme.surfaceContainerHighest)
                }
                LabeledIconButton(LumaIcons.Undo,strings.getString(R.string.undo),::undo,enabled=decisions.isNotEmpty() && !busy)
                Box {
                    LabeledIconButton(LumaIcons.More,strings.getString(R.string.more_actions),{actionsOpen=true},enabled=!session.finished && !busy)
                    DropdownMenu(actionsOpen,{actionsOpen=false}) {
                        (if(item==null) listOf(ReviewAction.SKIP) else listOf(ReviewAction.REMOVE,ReviewAction.KEEP,ReviewAction.FAVORITE,ReviewAction.ARCHIVE,ReviewAction.SKIP)).forEach {action->
                            DropdownMenuItem(text={Text(strings.getString(action.labelRes),color=if(action==ReviewAction.REMOVE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)},leadingIcon={Icon(action.icon,null)},onClick={actionsOpen=false;choose(action)},enabled=!busy && !session.finished)
                        }
                    }
                }
                LabeledIconButton(LumaIcons.Check,strings.getString(R.string.review_finish),{finish=true},enabled=decisions.isNotEmpty() && !busy)
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp).clipToBounds(),contentAlignment=Alignment.Center) {
                val tight=maxHeight<170.dp
                if(session.finished) Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    Surface(shape=CircleShape,color=MaterialTheme.colorScheme.primaryContainer) {Icon(LumaIcons.Check,null,Modifier.padding(22.dp).size(34.dp),tint=MaterialTheme.colorScheme.primary)}
                    Text(strings.getString(R.string.review_done),style=MaterialTheme.typography.headlineSmall,textAlign=TextAlign.Center)
                    Text(strings.getString(R.string.review_complete_hint),textAlign=TextAlign.Center,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick={finish=true},enabled=decisions.isNotEmpty()) {Text(strings.getString(R.string.review_summary))}
                } else key(session.currentId) {
                    Box(Modifier.widthIn(max=600.dp).fillMaxSize(),contentAlignment=Alignment.Center) {
                        ReviewPhotoDeck(item,next,settings,finish || leaving,busy,{busy=it},{action->busy=false;choose(action)},
                            {feedbackVisible=false;undoFeedback=false},tight,videoMuted,{videoMuted=it})
                    }
                }
                val visible=feedbackVisible && settings.reviewPlayful
                val transition=remember(feedbackKey) {MutableTransitionState(false)}
                transition.targetState=visible
                androidx.compose.animation.AnimatedVisibility(visibleState=transition,modifier=Modifier.align(if(feedbackKey%2==0) AbsoluteAlignment.TopRight else AbsoluteAlignment.TopLeft).padding(12.dp),
                    enter=if(animate) fadeIn(tween(110))+scaleIn(spring(dampingRatio=.65f,stiffness=Spring.StiffnessMedium),initialScale=.78f)+slideInVertically {it/4} else fadeIn(snap()),
                    exit=if(animate) fadeOut(tween(180))+scaleOut(tween(180),targetScale=.94f)+slideOutVertically {-it/5} else fadeOut(snap())) {
                    feedback?.let {action->
                        ReviewPopup(action,strings.getString(action.feedbackRes(feedbackKey)),Modifier.testTag("review_feedback").semantics {liveRegion=LiveRegionMode.Polite},
                            hint=if(action in listOf(ReviewAction.REMOVE,ReviewAction.ARCHIVE,ReviewAction.FAVORITE)) strings.getString(R.string.review_marked) else strings.getString(action.labelRes),
                            compact=tight,left=feedbackKey%2!=0)
                    }
                }
                if(undoFeedback && settings.reviewPlayful) Surface(Modifier.align(Alignment.TopCenter).padding(12.dp).semantics {liveRegion=LiveRegionMode.Polite},shape=ReviewBubbleShape(true),color=MaterialTheme.colorScheme.surfaceContainerHigh,shadowElevation=6.dp) {
                    Row(Modifier.padding(start=16.dp,end=16.dp,top=12.dp,bottom=20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {Icon(LumaIcons.Undo,null,Modifier.size(20.dp));Text(strings.getString(R.string.review_undo_feedback),style=MaterialTheme.typography.labelLarge,maxLines=2)}
                }
            }
            if(!session.finished) ReviewDirectionHints(if(item==null) ReviewAction.SKIP else settings.reviewLeft,if(item==null) ReviewAction.SKIP else settings.reviewRight,settings.reviewGestures)
        }
    }
}

@Composable
private fun ReviewPhotoDeck(item:GalleryMedia?,next:GalleryMedia?,settings:AppSettings,blocked:Boolean,busy:Boolean,onBusy:(Boolean)->Unit,onChoose:(ReviewAction)->Unit,onDragStart:()->Unit,compact:Boolean,videoMuted:Boolean,onVideoMuted:(Boolean)->Unit) {
    val strings=appStrings()
    val haptic=LocalHapticFeedback.current
    val scope=rememberCoroutineScope()
    var drag by remember {mutableFloatStateOf(0f)}
    var dragging by remember {mutableStateOf(false)}
    var exiting by remember {mutableStateOf(false)}
    var flightStarted by remember {mutableStateOf(false)}
    var crossed by remember {mutableStateOf(false)}
    val flyOut=remember {Animatable(0f)}
    val animate=settings.reviewPlayful && settings.thumbnailAnimations
    val resting by animateFloatAsState(drag,if(dragging || !animate) snap() else spring(dampingRatio=.85f,stiffness=Spring.StiffnessMedium),label="reviewReturn")
    val entry=remember {Animatable(if(animate) .04f else 0f)}
    LaunchedEffect(animate) {if(animate) entry.animateTo(0f,tween(180)) else entry.snapTo(0f)}
    val updatedChoose by rememberUpdatedState(onChoose)
    val updatedBusy by rememberUpdatedState(busy || blocked)
    DisposableEffect(Unit) {onDispose {onBusy(false)}}
    LaunchedEffect(blocked) {
        if(blocked && exiting) {flyOut.stop();drag=0f;dragging=false;exiting=false;flightStarted=false;onBusy(false)}
    }
    BoxWithConstraints(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
        val width=with(LocalDensity.current) {maxWidth.toPx().coerceAtLeast(1f)}
        val popupDistance=with(LocalDensity.current) {18.dp.toPx()}
        val threshold=width*.24f
        val position=if(exiting && flightStarted) flyOut.value else if(exiting || dragging) drag else resting
        val progress=(abs(position)/threshold).coerceIn(0f,1f)
        val action=if(item==null) ReviewAction.SKIP else if(position<0) settings.reviewLeft else settings.reviewRight
        val ready=abs(drag)>=threshold
        if(next!=null) Surface(Modifier.fillMaxSize().padding(top=12.dp).graphicsLayer {scaleX=.95f+.03f*progress;scaleY=.95f+.03f*progress}.clearAndSetSemantics {},shape=RoundedCornerShape(32.dp),color=MaterialTheme.colorScheme.surfaceContainerLow) {
            AsyncImage(next.uri,null,Modifier.fillMaxSize(),contentScale=ContentScale.Fit)
        }
        Surface(Modifier.fillMaxSize().testTag("review_card").graphicsLayer {
            translationX=position
            scaleX=1f-entry.value;scaleY=1f-entry.value
            rotationZ=if(animate) (position/width*7f).coerceIn(-9f,9f) else 0f
        }.semantics(mergeDescendants=true) {
            stateDescription=strings.getString(if(settings.reviewGestures) R.string.review_swipe_instruction else R.string.review_menu_hint,
                strings.getString(if(item==null) ReviewAction.SKIP.labelRes else settings.reviewLeft.labelRes),strings.getString(if(item==null) ReviewAction.SKIP.labelRes else settings.reviewRight.labelRes))
            customActions=(if(item==null) listOf(ReviewAction.SKIP) else ReviewAction.entries).map {choice->
                CustomAccessibilityAction(strings.getString(choice.labelRes)) {if(updatedBusy || exiting) false else {exiting=true;updatedChoose(choice);true}}
            }
        }.pointerInput(settings.reviewGestures,settings.reviewLeft,settings.reviewRight,width,item?.id) {
            if(settings.reviewGestures) detectHorizontalDragGestures(onDragStart={
                if(!updatedBusy && !exiting) {dragging=true;crossed=false;onDragStart()}
            },onDragEnd={
                if(!updatedBusy && !exiting && abs(drag)>=threshold) {
                    val choice=if(item==null) ReviewAction.SKIP else if(drag<0) settings.reviewLeft else settings.reviewRight
                    val start=drag;val direction=if(start<0) -1 else 1
                    exiting=true;dragging=false
                    if(animate) {
                        onBusy(true)
                        scope.launch {
                            flyOut.snapTo(start);flightStarted=true
                            flyOut.animateTo(direction*width*1.35f,tween(170,easing=FastOutLinearInEasing))
                            updatedChoose(choice)
                        }
                    } else updatedChoose(choice)
                } else {dragging=false;drag=0f;crossed=false}
            },onDragCancel={if(!exiting) {dragging=false;drag=0f;crossed=false}}) {change,amount->
                change.consume()
                if(!updatedBusy && !exiting) {
                    drag=(drag+amount).coerceIn(-width*1.2f,width*1.2f)
                    val nowReady=abs(drag)>=threshold
                    if(nowReady && !crossed) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    crossed=nowReady
                }
            }
        },shape=RoundedCornerShape(32.dp),color=MaterialTheme.colorScheme.surfaceContainerLow,
            border=BorderStroke(1.dp,if(abs(position)>popupDistance) reviewTint(action).copy(alpha=.2f+.5f*progress) else MaterialTheme.colorScheme.outlineVariant.copy(alpha=.25f))) {
            Box(contentAlignment=Alignment.Center) {
                var previewFailed by remember(item?.uri) {mutableStateOf(false)}
                if(item?.isVideo==true) ReviewVideoPreview(item,settings.autoPlayVideos,!blocked && !busy && !dragging && !exiting,videoMuted,onVideoMuted)
                else if(item!=null) AsyncImage(item.uri,item.name,Modifier.fillMaxSize(),contentScale=ContentScale.Fit,onError={previewFailed=true},onSuccess={previewFailed=false})
                if(item==null || previewFailed && item.isVideo.not()) Column(Modifier.padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Icon(if(item?.isVideo==true) LumaIcons.PlayArrow else LumaIcons.PhotoLibrary,null,Modifier.size(40.dp))
                    Text(strings.getString(if(item==null) R.string.review_source_missing else R.string.preview_unavailable),textAlign=TextAlign.Center)
                }
            }
        }
        if(abs(position)>popupDistance) ReviewPopup(action,strings.getString(if(settings.reviewPlayful) action.stampRes else action.labelRes),
            Modifier.align(if(position<0) AbsoluteAlignment.TopLeft else AbsoluteAlignment.TopRight).padding(12.dp).testTag("review_drag_stamp"),
            hint=if(ready) strings.getString(R.string.review_release_short) else null,progress=progress,compact=compact,playful=settings.reviewPlayful,left=position<0)
    }
}

@Composable
private fun ReviewDirectionHints(left:ReviewAction,right:ReviewAction,gestures:Boolean) {
    val strings=appStrings()
    if(gestures) CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {Row(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=12.dp).testTag("review_directions").clearAndSetSemantics {},horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
        Row(Modifier.weight(1f).testTag("review_direction_left"),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(LumaIcons.ArrowBack,null,Modifier.size(18.dp),tint=reviewInk(left))
            Text(strings.getString(left.labelRes),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        Row(Modifier.weight(1f).testTag("review_direction_right"),horizontalArrangement=Arrangement.End,verticalAlignment=Alignment.CenterVertically) {
            Text(strings.getString(right.labelRes),Modifier.weight(1f),textAlign=TextAlign.End,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)
            Spacer(Modifier.width(6.dp));Icon(LumaIcons.ArrowForward,null,Modifier.size(18.dp),tint=reviewInk(right))
        }
    } } else Text(strings.getString(R.string.review_menu_hint),Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=12.dp),textAlign=TextAlign.Center,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
}

private val ReviewAction.icon:ImageVector get()=when(this) {
    ReviewAction.KEEP->LumaIcons.Check;ReviewAction.REMOVE->LumaIcons.DeleteOutline;ReviewAction.ARCHIVE->LumaIcons.Archive;ReviewAction.FAVORITE->LumaIcons.Favorite;ReviewAction.SKIP->LumaIcons.ArrowForward
}

private val ReviewAction.stampRes:Int get()=when(this) {
    ReviewAction.KEEP->R.string.review_stamp_keep;ReviewAction.REMOVE->R.string.review_stamp_remove;ReviewAction.ARCHIVE->R.string.review_stamp_archive;ReviewAction.FAVORITE->R.string.review_stamp_favorite;ReviewAction.SKIP->R.string.review_stamp_skip
}

@Composable
private fun reviewTint(action:ReviewAction):Color=when(action) {
    ReviewAction.REMOVE->MaterialTheme.colorScheme.error
    ReviewAction.SKIP->MaterialTheme.colorScheme.onSurfaceVariant
    ReviewAction.ARCHIVE->lerp(MaterialTheme.colorScheme.primary,Color(0xFFE2B75C),.55f)
    ReviewAction.FAVORITE->lerp(MaterialTheme.colorScheme.primary,Color(0xFFEB77A8),.55f)
    ReviewAction.KEEP->lerp(MaterialTheme.colorScheme.primary,Color(0xFF68D7A8),.5f)
}

@Composable
private fun reviewBackground(action:ReviewAction)=lerp(MaterialTheme.colorScheme.surfaceContainerHigh,reviewTint(action),.1f)

@Composable
private fun reviewInk(action:ReviewAction)=Color(readableAccent(reviewTint(action).toArgb(),reviewBackground(action).toArgb()))

@Composable
private fun ReviewActionGlyph(action:ReviewAction,modifier:Modifier=Modifier,progress:Float?=null) {
    Box(modifier,contentAlignment=Alignment.Center) {
        Surface(Modifier.fillMaxSize().padding(if(progress!=null) 4.dp else 0.dp),shape=CircleShape,color=reviewBackground(action),contentColor=reviewInk(action)) {
            Box(contentAlignment=Alignment.Center) {Icon(action.icon,null,Modifier.size(24.dp))}
        }
        if(progress!=null) CircularProgressIndicator(progress={progress},modifier=Modifier.fillMaxSize(),color=reviewInk(action),strokeWidth=2.dp,trackColor=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.4f))
    }
}

private fun ReviewAction.feedbackRes(sequence:Int):Int {
    val messages=when(this) {
        ReviewAction.KEEP->listOf(R.string.review_keep_bubble_1,R.string.review_keep_bubble_2,R.string.review_keep_bubble_3)
        ReviewAction.REMOVE->listOf(R.string.review_remove_bubble_1,R.string.review_remove_bubble_2,R.string.review_remove_bubble_3)
        ReviewAction.ARCHIVE->listOf(R.string.review_archive_bubble_1,R.string.review_archive_bubble_2,R.string.review_archive_bubble_3)
        ReviewAction.FAVORITE->listOf(R.string.review_favorite_bubble_1,R.string.review_favorite_bubble_2,R.string.review_favorite_bubble_3)
        ReviewAction.SKIP->listOf(R.string.review_skip_bubble_1,R.string.review_skip_bubble_2,R.string.review_skip_bubble_3)
    }
    return messages[(sequence-1).coerceAtLeast(0)%messages.size]
}

private class ReviewBubbleShape(val left:Boolean):Shape {
    override fun createOutline(size:Size,layoutDirection:LayoutDirection,density:Density):Outline {
        val tail=with(density) {8.dp.toPx()}.coerceAtMost(size.height/4)
        val radius=with(density) {24.dp.toPx()}.coerceAtMost((size.height-tail)/2)
        val x=size.width*(if(left) .24f else .76f)
        val path=Path().apply {
            addRoundRect(RoundRect(Rect(0f,0f,size.width,size.height-tail),CornerRadius(radius,radius)))
            moveTo(x-tail,size.height-tail-1f)
            cubicTo(x-tail*.5f,size.height-tail*.6f,x-tail*.4f,size.height,x,size.height)
            cubicTo(x+tail*.4f,size.height,x+tail*.5f,size.height-tail*.6f,x+tail,size.height-tail-1f)
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
private fun ReviewPopup(action:ReviewAction,title:String,modifier:Modifier=Modifier,hint:String?=null,progress:Float?=null,compact:Boolean=false,playful:Boolean=true,left:Boolean=false) {
    val tint=reviewTint(action)
    val background=reviewBackground(action)
    val ink=Color(readableAccent(tint.toArgb(),lerp(background,tint,.08f).toArgb()))
    val shape=if(playful) ReviewBubbleShape(left) else RoundedCornerShape(22.dp)
    Box(modifier.widthIn(max=280.dp).shadow(if(playful) 8.dp else 2.dp,shape).clip(shape).background(Brush.linearGradient(listOf(lerp(background,tint,.08f),background)))) {
        if(playful) Canvas(Modifier.matchParentSize()) {
            drawCircle(tint.copy(alpha=.12f),size.minDimension*.62f,Offset(if(left) size.width else 0f,0f))
            drawCircle(ink.copy(alpha=.16f),2.dp.toPx(),Offset(size.width-18.dp.toPx(),13.dp.toPx()))
            drawCircle(ink.copy(alpha=.1f),1.5.dp.toPx(),Offset(size.width-28.dp.toPx(),21.dp.toPx()))
        }
        Row(Modifier.padding(start=14.dp,end=18.dp,top=12.dp,bottom=if(playful) 20.dp else 12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            ReviewActionGlyph(action,Modifier.size(if(compact) 36.dp else 44.dp),progress)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Text(title,color=ink,style=if(compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)
                if(hint!=null) Text(hint,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
        }
    }
}
@Composable
fun ReviewGestureSettings(settings:AppSettings,enabled:Boolean,onSettings:(AppSettings)->Unit) {
    val strings=appStrings()
    Text(strings.getString(R.string.review_gestures),style=MaterialTheme.typography.titleSmall)
    Text(strings.getString(R.string.review_settings_hint),style=MaterialTheme.typography.bodySmall)
    Row(verticalAlignment=Alignment.CenterVertically) {
        Text(strings.getString(R.string.review_swipe_enabled),Modifier.weight(1f))
        Switch(settings.reviewGestures,{onSettings(settings.copy(reviewGestures=it))},enabled=enabled)
    }
    Row(verticalAlignment=Alignment.CenterVertically) {
        Text(strings.getString(R.string.review_playful),Modifier.weight(1f))
        Switch(settings.reviewPlayful,{onSettings(settings.copy(reviewPlayful=it))},enabled=enabled)
    }
    Text(strings.getString(R.string.review_playful_hint),style=MaterialTheme.typography.bodySmall)
    listOf(true,false).forEach {left->
        var menu by remember {mutableStateOf(false)}
        val selected=if(left) settings.reviewLeft else settings.reviewRight
        Column {
            Text(strings.getString(if(left) R.string.review_left else R.string.review_right),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Box {
                OutlinedButton(onClick={menu=true},enabled=enabled,modifier=Modifier.fillMaxWidth()) {
                    Icon(selected.icon,null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp))
                    Text(strings.getString(selected.labelRes),Modifier.weight(1f));Icon(LumaIcons.ChevronDown,null)
                }
                DropdownMenu(menu,{menu=false}) {ReviewAction.entries.forEach {action->DropdownMenuItem(text={Text(strings.getString(action.labelRes))},leadingIcon={Icon(action.icon,null)},trailingIcon={if(action==selected) Icon(LumaIcons.Check,null)},onClick={menu=false;onSettings(if(left) settings.copy(reviewLeft=action) else settings.copy(reviewRight=action))})}}
            }
        }
    }
}
