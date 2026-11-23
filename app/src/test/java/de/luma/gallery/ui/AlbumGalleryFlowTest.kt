package de.luma.gallery.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import de.luma.gallery.GalleryViewModel
import de.luma.gallery.model.*
import de.luma.gallery.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "de")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalCoroutinesApi::class)
class AlbumGalleryFlowTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    @Before fun setMain() { Dispatchers.setMain(android.os.Handler(android.os.Looper.getMainLooper()).asCoroutineDispatcher()) }
    @After fun resetMain() { Dispatchers.resetMain() }

    @Test fun gallerySelectionCreatesAlbumAndViewerAndRemovalStayWithinTheCollection() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("luma", 0).edit().clear().commit()
        val provider=de.luma.gallery.data.DocumentToolsTest.TestProvider()
        provider.attachInfo(app,android.content.pm.ProviderInfo().apply {authority="luma.test.docs";exported=true;grantUriPermissions=true;readPermission=android.Manifest.permission.MANAGE_DOCUMENTS;writePermission=android.Manifest.permission.MANAGE_DOCUMENTS})
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("luma.test.docs",provider)
        val bitmap=android.graphics.Bitmap.createBitmap(8,8,android.graphics.Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.BLUE)}
        val bytes=java.io.ByteArrayOutputStream().apply {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,this)}.toByteArray();bitmap.recycle()
        val tree=android.provider.DocumentsContract.buildTreeDocumentUri("luma.test.docs","root")
        val imported=(0..8).map {i->provider.add("actual-$i","root","Actual-$i.jpg",bytes);android.provider.DocumentsContract.buildDocumentUriUsingTree(tree,"actual-$i").toString()}.toSet()
        app.getSharedPreferences("luma",0).edit().putStringSet("imports",imported).commit()
        val database = Room.inMemoryDatabaseBuilder(app, ProjectDatabase::class.java).build()
        val store = ViewModelStore()
        val vm = GalleryViewModel(app, ProjectRepository(database))
        ViewModelProvider(store, viewModelFactory { initializer { vm } })[GalleryViewModel::class.java]
        try {

            compose.setContent { GalleryApp(vm) }
            compose.waitUntil(10_000) {
                shadowOf(android.os.Looper.getMainLooper()).idle()
                !vm.state.value.loading && !vm.state.value.albumsLoading && vm.state.value.media.size == 9
            }
            val source = vm.state.value.media.first()
            compose.onNodeWithContentDescription(source.name).performScrollTo().performTouchInput { longClick() }
            val selectionY=compose.onNodeWithText("1 ausgewählt").fetchSemanticsNode().boundsInRoot.top
            val gridBottom=compose.onNodeWithContentDescription("Medienraster").fetchSemanticsNode().boundsInRoot.bottom
            assertTrue(selectionY>=gridBottom)
            compose.onNodeWithContentDescription("Auswahlaktionen").performScrollToNode(hasText("Löschen"))
            compose.onNodeWithText("Löschen").assertIsDisplayed()
            capture("gallery-selection")
            compose.onNodeWithContentDescription("Auswahl aufheben").performClick()
            compose.onNodeWithContentDescription(source.name).performScrollTo().performTouchInput {longClick()}
            assertEquals(selectionY,compose.onNodeWithText("1 ausgewählt").fetchSemanticsNode().boundsInRoot.top,1f)
            compose.onNodeWithContentDescription("Auswahlaktionen").performScrollToNode(hasText("Album"))
            compose.onNodeWithText("Album").performClick()
            compose.onNode(hasSetTextAction()).performTextInput("Auswahl")
            compose.onNodeWithText("Anlegen und hinzufügen").performClick()
            compose.waitUntil(10_000) {
                shadowOf(android.os.Looper.getMainLooper()).idle()
                vm.state.value.albums.any { it.name == "Auswahl" && it.mediaIds == setOf(source.id) } && !vm.state.value.savingAlbum
            }

            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(5_000)
            compose.waitForIdle()
            compose.onNodeWithText("Alben").performClick()
            compose.onNodeWithContentDescription("Albumraster").performScrollToNode(hasText("Auswahl"))
            compose.onNodeWithText("Auswahl").performClick()
            compose.onNodeWithContentDescription(source.name).performScrollTo().performClick()
            compose.onNodeWithContentDescription("Vorheriges Medium").assertDoesNotExist()
            compose.onNodeWithContentDescription("Nächstes Medium").assertDoesNotExist()
            compose.onNodeWithContentDescription("Zurück").assertDoesNotExist()
            compose.onNodeWithContentDescription(source.name).performTouchInput {click()}
            compose.mainClock.advanceTimeBy(400)
            compose.waitForIdle()
            compose.onNodeWithContentDescription("Zurück").performClick()
            compose.onNodeWithContentDescription(source.name).performScrollTo().performTouchInput { longClick() }
            compose.onNodeWithContentDescription("Auswahlaktionen").performScrollToNode(hasText("Aus Album entfernen"))
            compose.onNodeWithText("Aus Album entfernen").performClick()
            compose.onNodeWithText("Abbrechen").performClick()
            compose.runOnIdle { assertEquals(setOf(source.id), vm.state.value.albums.single().mediaIds) }
            compose.onNodeWithContentDescription("Auswahlaktionen").performScrollToNode(hasText("Aus Album entfernen"))
            compose.onNodeWithText("Aus Album entfernen").performClick()
            compose.onNodeWithText("Zuordnung entfernen").performClick()
            compose.waitUntil(10_000) {
                shadowOf(android.os.Looper.getMainLooper()).idle()
                vm.state.value.albums.single().mediaIds.isEmpty() && !vm.state.value.savingAlbum
            }
            compose.runOnIdle { assertEquals(9, vm.state.value.media.size); assertTrue(vm.state.value.media.any { it.id == source.id }) }
            compose.onNodeWithText("Hier ist noch Platz.").assertExists()
        } finally { compose.runOnIdle { store.clear() }; database.close(); provider.folder.deleteRecursively() }
    }
    @Test fun returningFromVideosStartsPhotoGridAtTheTop() {
        val app=RuntimeEnvironment.getApplication()
        app.getSharedPreferences("luma",0).edit().clear().commit()
        val provider=de.luma.gallery.data.DocumentToolsTest.TestProvider()
        provider.attachInfo(app,android.content.pm.ProviderInfo().apply {authority="luma.test.docs";exported=true;grantUriPermissions=true;readPermission=android.Manifest.permission.MANAGE_DOCUMENTS;writePermission=android.Manifest.permission.MANAGE_DOCUMENTS})
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("luma.test.docs",provider)
        val bitmap=android.graphics.Bitmap.createBitmap(8,8,android.graphics.Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.BLUE)}
        val bytes=java.io.ByteArrayOutputStream().apply {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,this)}.toByteArray();bitmap.recycle()
        val tree=android.provider.DocumentsContract.buildTreeDocumentUri("luma.test.docs","root")
        val imported=(0..35).map {i->provider.add("nav-$i","root","Nav-$i.jpg",bytes);android.provider.DocumentsContract.buildDocumentUriUsingTree(tree,"nav-$i").toString()}.toSet()
        app.getSharedPreferences("luma",0).edit().putStringSet("imports",imported).commit()
        val database=Room.inMemoryDatabaseBuilder(app,ProjectDatabase::class.java).build()
        val store=ViewModelStore();val vm=GalleryViewModel(app,ProjectRepository(database))
        ViewModelProvider(store,viewModelFactory {initializer {vm}})[GalleryViewModel::class.java]
        try {
            compose.setContent {GalleryApp(vm)}
            compose.waitUntil(10_000) {shadowOf(android.os.Looper.getMainLooper()).idle();!vm.state.value.loading&&vm.state.value.media.size==36}
            compose.onNodeWithContentDescription("Papierkorb").performClick()
            compose.onNodeWithText("Ab Android 11 verfügbar").assertExists()
            compose.onNodeWithContentDescription("Zurück").performClick()
            compose.onNodeWithText("Alben").performClick()
            compose.onNodeWithText("Schnellzugriff").assertExists()
            capture("gallery-albums")
            compose.onNodeWithText("Papierkorb").performClick()
            compose.onNodeWithText("Ab Android 11 verfügbar").assertExists()
            compose.onNodeWithContentDescription("Zurück").performClick()
            compose.onNodeWithText("Archiv").performClick()
            compose.onNodeWithText("Fotos").performClick()
            val first=selectMedia(vm.state.value.media,MediaFilter.PHOTOS,emptySet()).first().name
            compose.onNodeWithContentDescription("Medienraster").performScrollToIndex(25)
            compose.onNodeWithText("Videos").performClick()
            compose.onNodeWithText("Fotos").performClick()
            compose.onNodeWithContentDescription(first).assertIsDisplayed()
            val range=compose.onNodeWithContentDescription("Medienraster").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange]
            assertEquals(0f,range.value(),0f)
        } finally {compose.runOnIdle {store.clear()};database.close();provider.folder.deleteRecursively()}
    }

    @Test fun topFavoritesAndCentralReviewResumeReturnToTheSameCollection() {
        val app=RuntimeEnvironment.getApplication()
        app.getSharedPreferences("luma",0).edit().clear().commit()
        val draft=ReviewDraft(app);draft.clear()
        val provider=DocumentToolsTest.TestProvider()
        provider.attachInfo(app,android.content.pm.ProviderInfo().apply {authority="luma.test.docs";exported=true;grantUriPermissions=true;readPermission=android.Manifest.permission.MANAGE_DOCUMENTS;writePermission=android.Manifest.permission.MANAGE_DOCUMENTS})
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("luma.test.docs",provider)
        val bitmap=android.graphics.Bitmap.createBitmap(8,8,android.graphics.Bitmap.Config.ARGB_8888)
        val bytes=java.io.ByteArrayOutputStream().apply {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,this)}.toByteArray();bitmap.recycle()
        val tree=android.provider.DocumentsContract.buildTreeDocumentUri("luma.test.docs","root")
        val imported=(0..2).map {i->provider.add("shortcut-$i","root","Shortcut-$i.jpg",bytes);android.provider.DocumentsContract.buildDocumentUriUsingTree(tree,"shortcut-$i").toString()}.toSet()
        app.getSharedPreferences("luma",0).edit().putStringSet("imports",imported).commit()
        val database=Room.inMemoryDatabaseBuilder(app,ProjectDatabase::class.java).build()
        val store=ViewModelStore();val vm=GalleryViewModel(app,ProjectRepository(database))
        ViewModelProvider(store,viewModelFactory {initializer {vm}})[GalleryViewModel::class.java]
        try {
            compose.waitUntil(10_000) {shadowOf(android.os.Looper.getMainLooper()).idle();!vm.state.value.loading && vm.state.value.media.size==3}
            val media=vm.state.value.media
            draft.save(ReviewSession(media.take(2).map {it.id},listOf(ReviewAction.KEEP)))
            compose.runOnIdle {vm.favorite(media.last().id)}
            compose.setContent {GalleryApp(vm)}
            compose.onNodeWithContentDescription("Favoriten").performClick()
            compose.onNodeWithContentDescription(media.last().name).assertIsDisplayed()
            compose.onNodeWithContentDescription(media.first().name).assertDoesNotExist()
            compose.onNodeWithContentDescription("Fotos").assertIsNotSelected()
            val heart=compose.onNodeWithContentDescription("Favoriten").fetchSemanticsNode().boundsInRoot
            val trash=compose.onNodeWithContentDescription("Papierkorb").fetchSemanticsNode().boundsInRoot
            assertEquals(heart.center.y,trash.center.y,1f)
            compose.onNodeWithContentDescription("Aussortieren fortsetzen").performClick()
            compose.onNodeWithText("1 von 2").assertExists()
            compose.onNodeWithContentDescription("Zurück").performClick()
            compose.onNodeWithText("Entscheidungen verwerfen").performClick()
            compose.onNodeWithContentDescription(media.last().name).assertIsDisplayed()
            compose.onNodeWithContentDescription(media.first().name).assertDoesNotExist()
            assertNull(draft.read())
            compose.onNodeWithContentDescription("Fotos").performClick().assertIsSelected()
            media.forEach {compose.onNodeWithContentDescription(it.name).assertIsDisplayed()}
            capture("gallery-navigation86")
        } finally {compose.runOnIdle {store.clear()};database.close();provider.folder.deleteRecursively();draft.clear()}
    }


    private fun capture(name:String) {
        val output=java.io.File("build/design86","$name.png");output.parentFile!!.mkdirs()
        compose.runOnIdle {
            val view=compose.activity.window.decorView;val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap));output.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        }
    }
}
