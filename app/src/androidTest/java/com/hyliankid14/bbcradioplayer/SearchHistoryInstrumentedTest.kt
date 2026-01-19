package com.hyliankid14.bbcradioplayer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchHistoryInstrumentedTest {
    private lateinit var ctx: Context
    private lateinit var history: SearchHistory

    @Before
    fun setup() {
        ctx = ApplicationProvider.getApplicationContext()
        history = SearchHistory(ctx)
        history.clear()
    }

    @Test
    fun `adding longer query removes shorter prefix and avoids duplicates`() {
        history.add("barbers")
        history.add("barbershop")
        val recent = history.getRecent()
        // Expect only the longer query to remain and be first
        assertEquals(1, recent.size)
        assertEquals("barbershop", recent[0])

        // Adding the shorter again should not prepend since longer exists
        history.add("barbers")
        val recent2 = history.getRecent()
        assertEquals(1, recent2.size)
        assertEquals("barbershop", recent2[0])
    }
}
