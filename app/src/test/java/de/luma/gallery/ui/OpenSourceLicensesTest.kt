package de.luma.gallery.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class OpenSourceLicensesTest {
    @Test fun wrappedParagraphsAreJoinedWhileListsAndHeadingsStay() {
        val text = """
            MIT License

            Permission is hereby granted, free of charge, to any person obtaining a copy
            of this software and associated documentation files (the "Software").

               1. Definitions.

                  "License" shall mean the terms and conditions for use, reproduction,
                  and distribution as defined by Sections 1 through 9 of this document.
                  (a) You must give any other recipients a copy of this License; and
        """.trimIndent()
        assertEquals("""
            MIT License

            Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software").

              1. Definitions.

              "License" shall mean the terms and conditions for use, reproduction, and distribution as defined by Sections 1 through 9 of this document.
              (a) You must give any other recipients a copy of this License; and
        """.trimIndent(), reflowLicense(text))
    }
}
