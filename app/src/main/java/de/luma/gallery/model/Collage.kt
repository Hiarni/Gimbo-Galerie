package de.luma.gallery.model

import de.luma.gallery.R
import kotlin.math.ceil
import kotlin.math.sqrt

enum class CollageLayout(val labelRes:Int) { GRID(R.string.collage_grid), HERO(R.string.collage_hero), ROWS(R.string.collage_rows), COLUMNS(R.string.collage_columns) }
data class CollageTile(val zoom:Float=1f,val panX:Float=0f,val panY:Float=0f,val quarterTurns:Int=0) {
    init {require(zoom.isFinite() && zoom in 1f..4f && panX.isFinite() && panY.isFinite() && panX in -1f..1f && panY in -1f..1f && quarterTurns in 0..3)}
}
data class CollageSpec(val layout:CollageLayout=CollageLayout.GRID,val ratio:Float=1f,val gap:Float=.015f,val corner:Float=.025f,val background:Long=0xFFF8F7FA,val tiles:List<CollageTile>) {
    init {require(tiles.size in 2..9 && ratio.isFinite() && ratio in .5f..2f && gap.isFinite() && gap in 0f.. .05f && corner.isFinite() && corner in 0f.. .12f && background in 0L..0xFFFFFFFFL)}
}
data class CollageCell(val left:Float,val top:Float,val right:Float,val bottom:Float)
fun CollageSpec.cells():List<CollageCell> {
    fun grid(count:Int,left:Float=0f,right:Float=1f):List<CollageCell> {
        val columns=ceil(sqrt(count*ratio*(right-left))).toInt().coerceIn(1,count)
        val rows=ceil(count.toFloat()/columns).toInt()
        return (0 until rows).flatMap {row->
            val length=minOf(columns,count-row*columns)
            (0 until length).map {col->CollageCell(left+(right-left)*col/length,row.toFloat()/rows,left+(right-left)*(col+1)/length,(row+1f)/rows)}
        }
    }
    val raw=when(layout) {
        CollageLayout.GRID->grid(tiles.size)
        CollageLayout.HERO->listOf(CollageCell(0f,0f,.6f,1f))+grid(tiles.size-1,.6f,1f)
        CollageLayout.ROWS->tiles.indices.map {CollageCell(0f,it.toFloat()/tiles.size,1f,(it+1f)/tiles.size)}
        CollageLayout.COLUMNS->tiles.indices.map {CollageCell(it.toFloat()/tiles.size,0f,(it+1f)/tiles.size,1f)}
    }
    return raw.map {cell->
        val x=minOf(gap/2,(cell.right-cell.left)/4)
        val y=minOf(gap*ratio/2,(cell.bottom-cell.top)/4)
        CollageCell(cell.left+x,cell.top+y,cell.right-x,cell.bottom-y)
    }
}
