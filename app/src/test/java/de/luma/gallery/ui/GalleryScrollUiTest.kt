package de.luma.gallery.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import de.luma.gallery.GalleryViewModel
import de.luma.gallery.data.*
import de.luma.gallery.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],qualifiers="de-w360dp-h740dp")
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalCoroutinesApi::class)
class GalleryScrollUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Before fun setMain() {Dispatchers.setMain(android.os.Handler(android.os.Looper.getMainLooper()).asCoroutineDispatcher())}
    @After fun resetMain() {Dispatchers.resetMain()}

    @Test fun photoPositionSurvivesViewerSettingsReviewAndRestorationWhileViewerIsOpen() = withGallery(review=true) {vm,_,restore->
        val target=selectMedia(vm.state.value.media,MediaFilter.PHOTOS,emptySet())[30]
        compose.onNodeWithContentDescription("Medienraster").performScrollToNode(hasContentDescription(target.name))
        compose.onNodeWithContentDescription("Medienraster").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) {it(0f,37f)}
        compose.waitForIdle()
        val before=position("Medienraster")
        assertTrue(before>0f)
        compose.onNodeWithContentDescription(target.name).performClick()
        back()
        assertEquals(before,position("Medienraster"),.000001f)
        compose.onNodeWithContentDescription(target.name).performClick()
        restore.emulateSavedInstanceStateRestore()
        back()
        assertEquals(before,position("Medienraster"),.000001f)
        compose.onNodeWithContentDescription(target.name).performClick()
        back()
        compose.onNodeWithContentDescription("Aussortieren fortsetzen").performClick()
        back()
        assertEquals(before,position("Medienraster"),.000001f)
        compose.onNodeWithContentDescription("Medienraster").performScrollToIndex(0)
        compose.onNodeWithContentDescription("Einstellungen").performClick()
        back()
        assertEquals(0f,position("Medienraster"),.000001f)
    }

    @Test fun albumOverviewAndItsPhotoGridKeepIndependentPositionsIncludingRestoration() = withGallery(albums=true,restoreWithNewViewModel=true) {vm,_,restore->
        compose.onNodeWithContentDescription("Alben").performClick()
        compose.onNodeWithContentDescription("Albumraster").performScrollToNode(hasText("Sammlung 16"))
        val albumsBefore=position("Albumraster")
        assertTrue(albumsBefore>0f)
        compose.onNodeWithText("Sammlung 16").performClick()
        val target=selectMedia(vm.state.value.media,MediaFilter.ALL,emptySet())[30]
        compose.onNodeWithContentDescription("Medienraster").performScrollToNode(hasContentDescription(target.name))
        val mediaBefore=position("Medienraster")
        assertTrue(mediaBefore>0f)
        compose.onNodeWithContentDescription(target.name).performClick()
        back()
        assertEquals(mediaBefore,position("Medienraster"),.000001f)
        restore.emulateSavedInstanceStateRestore()
        compose.waitUntil(10_000) {shadowOf(android.os.Looper.getMainLooper()).idle();compose.onAllNodesWithContentDescription("Medienraster").fetchSemanticsNodes().isNotEmpty()}
        assertEquals(mediaBefore,position("Medienraster"),.000001f)
        compose.onNodeWithText("Alle Alben").performClick()
        assertEquals(albumsBefore,position("Albumraster"),.000001f)
        compose.onNodeWithText("Sammlung 16").assertIsDisplayed().performClick()
        assertEquals(mediaBefore,position("Medienraster"),.000001f)
        back()
        assertEquals(albumsBefore,position("Albumraster"),.000001f)
        restore.emulateSavedInstanceStateRestore()
        compose.waitUntil(10_000) {shadowOf(android.os.Looper.getMainLooper()).idle();compose.onAllNodesWithContentDescription("Albumraster").fetchSemanticsNodes().isNotEmpty()}
        assertEquals(albumsBefore,position("Albumraster"),.000001f)
    }

    @Test fun settingsReturnFromTrashRetainsScrollAndExpandedCategoryAfterRestoration() = withGallery {_,_,restore->
        compose.onNodeWithContentDescription("Einstellungen").performClick()
        val settings=compose.onNode(hasContentDescription("Einstellungen") and hasScrollToIndexAction())
        settings.performScrollToNode(hasText("Speicher, Ordner & Sicherungen"))
        compose.onNodeWithText("Speicher, Ordner & Sicherungen").performClick()
        settings.performScrollToNode(hasText("Papierkorb öffnen"))
        val before=settings.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue(before>0f)
        compose.onNodeWithText("Papierkorb öffnen").performClick()
        restore.emulateSavedInstanceStateRestore()
        back()
        assertEquals(before,settings.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value(),.000001f)
        compose.onNodeWithText("Papierkorb öffnen").assertIsDisplayed()
        compose.onNodeWithText("Dateien und Ordner").performScrollTo()
        val filesBefore=settings.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        compose.onNodeWithText("Dateien und Ordner").performClick()
        back()
        assertEquals(filesBefore,settings.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value(),.000001f)
    }

    @Test fun returningFromProjectEditorKeepsTheProjectListPosition() = withGallery(projects=true) {vm,_,_->
        compose.onNodeWithContentDescription("Mehr").performClick()
        compose.onNodeWithText("Projekte").performClick()
        val list=compose.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasText("Entwurf 16"))
        val before=list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue(before>0f)
        compose.onNodeWithText("Entwurf 16").performClick()
        compose.waitUntil(10_000) {shadowOf(android.os.Looper.getMainLooper()).idle();vm.state.value.editor?.projectId!=null}
        back()
        assertEquals(before,list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value(),.000001f)
        compose.onNodeWithText("Entwurf 16").assertIsDisplayed()
    }

    @Test fun aNewSortAlwaysStartsAtTheTopEvenWhenReturningToAnEarlierSort() = withGallery {vm,_,_->
        listOf(MediaSort.NAME,MediaSort.DATE).forEach {sort->
            val target=selectMedia(vm.state.value.media,MediaFilter.PHOTOS,emptySet(),sort=vm.state.value.settings.sort)[30]
            compose.onNodeWithContentDescription("Medienraster").performScrollToNode(hasContentDescription(target.name))
            assertTrue(position("Medienraster")>0f)
            compose.runOnIdle {vm.settings(vm.state.value.settings.copy(sort=sort))}
            compose.waitUntil(10_000) {shadowOf(android.os.Looper.getMainLooper()).idle();vm.state.value.settings.sort==sort}
            assertEquals(0f,position("Medienraster"),.000001f)
        }
    }

    @Test fun deviceFolderBackNavigationAndRestorationKeepEachFoldersOwnPosition() {
        val app=RuntimeEnvironment.getApplication()
        val provider=DocumentToolsTest.TestProvider()
        provider.attachInfo(app,android.content.pm.ProviderInfo().apply {authority="luma.test.docs";exported=true;grantUriPermissions=true;readPermission=android.Manifest.permission.MANAGE_DOCUMENTS;writePermission=android.Manifest.permission.MANAGE_DOCUMENTS})
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("luma.test.docs",provider)
        repeat(30) {provider.addFolder("folder-$it","root","Ordner %02d".format(it));provider.add("child-$it","folder-16","Datei %02d.jpg".format(it),byteArrayOf(1))}
        val tree=android.provider.DocumentsContract.buildTreeDocumentUri("luma.test.docs","root")
        val registry=object:androidx.activity.result.ActivityResultRegistry() {
            override fun <I,O> onLaunch(requestCode:Int,contract:androidx.activity.result.contract.ActivityResultContract<I,O>,input:I,options:androidx.core.app.ActivityOptionsCompat?) {
                dispatchResult(requestCode,android.app.Activity.RESULT_OK,android.content.Intent().setData(tree).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
            }
        }
        val owner=object:androidx.activity.result.ActivityResultRegistryOwner {override val activityResultRegistry=registry}
        var show by mutableStateOf(true)
        val restore=StateRestorationTester(compose)
        try {
            restore.setContent {CompositionLocalProvider(androidx.activity.compose.LocalActivityResultRegistryOwner provides owner) {androidx.compose.material3.MaterialTheme {if(show) FileToolsScreen({show=false},{})}}}
            compose.onNodeWithText("Ordner wählen").performClick()
            compose.waitUntil(10_000) {shadowOf(android.os.Looper.getMainLooper()).idle();compose.onAllNodesWithText("Ordner 00").fetchSemanticsNodes().isNotEmpty()}
            val list=compose.onNode(hasScrollToIndexAction())
            list.performScrollToNode(hasText("Ordner 16"))
            val parentBefore=list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
            assertTrue(parentBefore>0f)
            compose.onNodeWithText("Ordner 16",useUnmergedTree=true).performClick()
            compose.waitUntil(10_000) {shadowOf(android.os.Looper.getMainLooper()).idle();compose.onAllNodesWithText("Datei 00.jpg").fetchSemanticsNodes().isNotEmpty()}
            assertEquals(0f,list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value(),.000001f)
            list.performScrollToNode(hasText("Datei 20.jpg"))
            val childBefore=list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
            assertTrue(childBefore>0f)
            restore.emulateSavedInstanceStateRestore()
            compose.waitUntil(10_000) {shadowOf(android.os.Looper.getMainLooper()).idle();compose.onAllNodesWithText("Datei 20.jpg").fetchSemanticsNodes().isNotEmpty()}
            assertEquals(childBefore,list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value(),.000001f)
            back()
            compose.waitUntil(10_000) {shadowOf(android.os.Looper.getMainLooper()).idle();compose.onAllNodesWithText("Ordner 16").fetchSemanticsNodes().isNotEmpty()}
            assertEquals(parentBefore,list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value(),.000001f)
            compose.onNodeWithText("Ordner 16",useUnmergedTree=true).performClick()
            compose.waitUntil(10_000) {shadowOf(android.os.Looper.getMainLooper()).idle();compose.onAllNodesWithText("Datei 20.jpg").fetchSemanticsNodes().isNotEmpty()}
            assertEquals(childBefore,list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value(),.000001f)
        } catch(failure:Throwable) {println(compose.onRoot().printToString());throw failure}
        finally {compose.runOnIdle {show=false};compose.waitForIdle();provider.folder.deleteRecursively()}
    }

    private fun position(label:String):Float = compose.onNodeWithContentDescription(label).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
    private fun back() {compose.runOnIdle {compose.activity.onBackPressedDispatcher.onBackPressed()};compose.waitForIdle()}

    private fun withGallery(albums:Boolean=false,projects:Boolean=false,review:Boolean=false,restoreWithNewViewModel:Boolean=false,check:(GalleryViewModel,ProjectRepository,StateRestorationTester)->Unit) {
        val app=RuntimeEnvironment.getApplication()
        app.getSharedPreferences("luma",0).edit().clear().commit()
        ReviewDraft(app).clear()
        val provider=DocumentToolsTest.TestProvider()
        provider.attachInfo(app,android.content.pm.ProviderInfo().apply {authority="luma.test.docs";exported=true;grantUriPermissions=true;readPermission=android.Manifest.permission.MANAGE_DOCUMENTS;writePermission=android.Manifest.permission.MANAGE_DOCUMENTS})
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("luma.test.docs",provider)
        val bitmap=android.graphics.Bitmap.createBitmap(8,8,android.graphics.Bitmap.Config.ARGB_8888)
        val bytes=java.io.ByteArrayOutputStream().apply {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,this)}.toByteArray()
        bitmap.recycle()
        val tree=android.provider.DocumentsContract.buildTreeDocumentUri("luma.test.docs","root")
        val imported=(0..71).map {i->provider.add("scroll-$i","root","Scroll-$i.jpg",bytes);android.provider.DocumentsContract.buildDocumentUriUsingTree(tree,"scroll-$i").toString()}.toSet()
        app.getSharedPreferences("luma",0).edit().putStringSet("imports",imported).commit()
        val database=Room.inMemoryDatabaseBuilder(app,ProjectDatabase::class.java).build()
        val repository=ProjectRepository(database)
        val store=ViewModelStore()
        val vm=GalleryViewModel(app,repository)
        ViewModelProvider(store,viewModelFactory {initializer {vm}})[GalleryViewModel::class.java]
        val stores=mutableListOf(store)
        var contentGeneration=0
        var show by mutableStateOf(true)
        try {
            compose.waitUntil(10_000) {shadowOf(android.os.Looper.getMainLooper()).idle();!vm.state.value.loading && !vm.state.value.albumsLoading && vm.state.value.settingsReady && vm.state.value.media.size==72}
            runBlocking(Dispatchers.IO) {
                if(albums) repeat(24) {AlbumRepository(database).create("Sammlung %02d".format(it),vm.state.value.media.map {media->media.id}.toSet())}
                if(projects) repeat(24) {repository.save(vm.state.value.media.first(),"Entwurf %02d".format(it),photo=PhotoRecipe())}
            }
            compose.waitUntil(10_000) {shadowOf(android.os.Looper.getMainLooper()).idle();(!albums || vm.state.value.albums.size==24) && (!projects || vm.state.value.projects.size==24)}
            if(review) ReviewDraft(app).save(ReviewSession(vm.state.value.media.take(2).map {it.id}))
            val restore=StateRestorationTester(compose)
            restore.setContent {
                val renderedVm=remember {
                    contentGeneration++
                    if(restoreWithNewViewModel && contentGeneration>1) {
                        val replacementStore=ViewModelStore()
                        stores+=replacementStore
                        ViewModelProvider(replacementStore,viewModelFactory {initializer {GalleryViewModel(app,repository)}})[GalleryViewModel::class.java]
                    } else vm
                }
                if(show) GalleryApp(renderedVm)
            }
            check(vm,repository,restore)
        } finally {
            compose.runOnIdle {show=false}
            compose.waitForIdle()
            compose.runOnIdle {stores.forEach {it.clear()}}
            database.close()
            provider.folder.deleteRecursively()
            ReviewDraft(app).clear()
        }
    }
}
