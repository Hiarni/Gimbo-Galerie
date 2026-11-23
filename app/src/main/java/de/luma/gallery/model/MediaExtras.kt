package de.luma.gallery.model

import de.luma.gallery.R
import java.text.Normalizer
import java.util.Locale

private val accents=Regex("\\p{M}+")
private val screenshotNames=Regex("(?:^|[/ _.-])(?:screenshots?|screen[_ -]?shots?|screencaptures?|bildschirmfoto(?:s)?|bildschirmaufnahme(?:n)?|captur[ae][ _-]?(?:de[ _-]?)?(?:pantalla|ecran|tela))(?:$|[/ _.-]|\\d)")
val GalleryMedia.isScreenshot:Boolean
    get()=!isVideo && screenshotNames.containsMatchIn(Normalizer.normalize("$album/$name",Normalizer.Form.NFD).lowercase(Locale.ROOT).replace(accents,""))

enum class ReviewAction(val labelRes:Int) {
    KEEP(R.string.review_keep), REMOVE(R.string.review_remove), ARCHIVE(R.string.library_archive), FAVORITE(R.string.favorite), SKIP(R.string.review_skip)
}

data class ReviewSession(val ids:List<String>,val decisions:List<ReviewAction> = emptyList()) {
    init {require(ids.distinct().size==ids.size && ids.size<=MEDIA_ACTION_LIMIT && decisions.size<=ids.size)}
    val currentId get()=ids.getOrNull(decisions.size)
    val finished get()=decisions.size==ids.size
    fun choose(action:ReviewAction)=if(finished) this else copy(decisions=decisions+action)
    fun undo()=copy(decisions=decisions.dropLast(1))
    fun selected(action:ReviewAction)=ids.zip(decisions).filter {it.second==action}.map {it.first}.toSet()
}
