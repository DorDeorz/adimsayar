package com.dordeorz.adimsayar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateWatchTest {

    private val release = UpdateResult.Available("0.21.1", "https://github.com/DorDeorz/adimsayar/releases/tag/v0.21.1", null)

    @Test
    fun showsANewerRelease() {
        assertEquals(release, UpdateWatch.pending(release, null, "0.21.0"))
        assertEquals(release, UpdateWatch.pending(release, "0.21.0", "0.21.0"))
    }

    @Test
    fun hidesWhatIsInstalledOrPutAway() {
        assertNull(UpdateWatch.pending(null, null, "0.21.0"))
        assertNull(UpdateWatch.pending(release, null, "0.21.1"))
        assertNull(UpdateWatch.pending(release, null, "0.22.0"))
        assertNull(UpdateWatch.pending(release, "0.21.1", "0.21.0"))
    }
}
