package de.luma.gallery.model

import org.junit.Assert.*
import org.junit.Test

class ProjectNameTest {
    @Test fun namesAreTrimmedAndRejectBlankLongOrControlCharacters() {
        assertEquals("Urlaub", projectName("  Urlaub  "))
        assertEquals("x".repeat(80), projectName("x".repeat(80)))
        for (value in listOf("", "   ", "x".repeat(81), "Name\nZeile", "Name\u0000")) {
            assertThrows(IllegalArgumentException::class.java) { projectName(value) }
        }
    }
}
