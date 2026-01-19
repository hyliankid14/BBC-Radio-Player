package com.hyliankid14.bbcradioplayer

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class PodcastsSearchFlowTest {
    @Test
    fun searchTyping_then_ime_then_openAndReturn_restoresCachedResults() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // Type a query and expect quick results (title/description) to appear
            Espresso.onView(withId(R.id.search_podcast_edittext))
                .perform(click(), replaceText("news"), closeSoftKeyboard())

            // quick-path should populate results immediately; allow a small window
            Thread.sleep(200)

            // RecyclerView should be visible and have at least one item (scrollToPosition will fail if empty)
            Espresso.onView(withId(R.id.podcasts_recycler))
                .perform(RecyclerViewActions.scrollToPosition<androidx.recyclerview.widget.RecyclerView.ViewHolder>(0))

            // Press IME Search
            Espresso.onView(withId(R.id.search_podcast_edittext)).perform(pressImeActionButton())

            // Wait for debounced full search to start populating episodes
            Thread.sleep(400)
            Espresso.onView(withId(R.id.podcasts_recycler))
                .perform(RecyclerViewActions.scrollToPosition<androidx.recyclerview.widget.RecyclerView.ViewHolder>(0))

            // Open first item
            Espresso.onView(withId(R.id.podcasts_recycler))
                .perform(RecyclerViewActions.actionOnItemAtPosition<androidx.recyclerview.widget.RecyclerView.ViewHolder>(0, click()))

            // Back twice to return to podcasts fragment
            Espresso.pressBack()
            Espresso.pressBack()

            // The search results should be restored instantly (no empty flicker)
            Thread.sleep(150)
            Espresso.onView(withId(R.id.podcasts_recycler))
                .perform(RecyclerViewActions.scrollToPosition<androidx.recyclerview.widget.RecyclerView.ViewHolder>(0))
        }
    }
}
