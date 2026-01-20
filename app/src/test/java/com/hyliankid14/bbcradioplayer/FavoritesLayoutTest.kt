package com.hyliankid14.bbcradioplayer

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class FavoritesLayoutTest {
    @Test
    fun `activity_main contains favorites toggle group`() {
        val layout = File("src/main/res/layout/activity_main.xml").readText()
        assertTrue("activity_main should declare the favorites toggle group id",
            layout.contains("@+id/favorites_toggle_group") || layout.contains("@id/favorites_toggle_group"))
    }
}
