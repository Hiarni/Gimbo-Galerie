package de.luma.gallery.model


fun PhotoRecipe.layerKeys(): List<String> {
    val keys = markups.filter { it.kind == MarkupKind.PIXELATE }.map { "mark:${it.id}" } +
        markups.filter { it.kind != MarkupKind.PIXELATE }.map { "mark:${it.id}" } + texts.map { "text:${it.id}" }
    return layerOrder.filter { it in keys } + keys.filter { it !in layerOrder }
}
fun PhotoRecipe.layerVisible(key: String) = if (key.startsWith("text:")) texts.first { "text:${it.id}" == key }.visible else markups.first { "mark:${it.id}" == key }.visible
fun PhotoRecipe.layerOpacity(key: String) = if (key.startsWith("text:")) texts.first { "text:${it.id}" == key }.opacity else markups.first { "mark:${it.id}" == key }.opacity
fun PhotoRecipe.withLayer(key: String, visible: Boolean = layerVisible(key), opacity: Float = layerOpacity(key)): PhotoRecipe =
    if (key.startsWith("text:")) copy(texts = texts.map { if ("text:${it.id}" == key) it.copy(visible = visible, opacity = opacity) else it })
    else copy(markups = markups.map { if ("mark:${it.id}" == key) it.copy(visible = visible, opacity = opacity) else it })
