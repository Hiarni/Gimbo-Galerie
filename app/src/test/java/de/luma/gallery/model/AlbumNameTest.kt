package de.luma.gallery.model

import org.junit.Assert.*
import org.junit.Test

class AlbumNameTest {
    @Test fun namesAreBoundedAndDuplicateKeysNormalizeCaseAndUnicode() {
        assertEquals("Urlaub", albumName(" Urlaub "))
        for (value in listOf("", " ", "a".repeat(81), "A\nB")) assertThrows(IllegalArgumentException::class.java) { albumName(value) }
        assertEquals(80, albumName("a".repeat(80)).length)
        assertEquals(albumNameKey("FÄCHER"), albumNameKey("Fa\u0308cher"))
        assertEquals(albumNameKey("ABC"), albumNameKey("ＡＢＣ"))
    }
}
