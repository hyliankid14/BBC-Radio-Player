package com.hyliankid14.bbcradioplayer

import org.junit.Assert.*
import org.junit.Test

class PodcastsViewModelTest {
    @Test
    fun `setActiveSearch seeds cache and clearCachedSearch clears it`() {
        val vm = PodcastsViewModel()
        assertNull(vm.getCachedSearch())

        vm.setActiveSearch("kotlin")
        val cached = vm.getCachedSearch()
        assertNotNull("cache should be seeded after setActiveSearch", cached)
        assertEquals("kotlin", cached!!.query)

        vm.clearCachedSearch()
        assertNull(vm.getCachedSearch())
    }
}
