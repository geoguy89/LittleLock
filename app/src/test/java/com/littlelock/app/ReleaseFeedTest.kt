package com.littlelock.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReleaseFeedTest {

    private val json = """{"tag_name":"v1.7.0","html_url":"https://github.com/x/y/releases/tag/v1.7.0","draft":false,"prerelease":false,
        "body":"Itemised daily summary","assets":[{"name":"notes.txt","browser_download_url":"https://x/notes.txt","size":3},
        {"name":"LittleLock-release.apk","browser_download_url":"https://x/LittleLock-release.apk","size":14176648}]}"""

    @Test
    fun the_apk_is_found_among_the_release_files() {
        val r = ReleaseFeed.parse(json)!!
        assertEquals("1.7.0", r.version)
        assertEquals("https://x/LittleLock-release.apk", r.apkUrl)
        assertEquals(14176648L, r.sizeBytes)
        assertEquals("Itemised daily summary", r.notes)
    }

    @Test
    fun drafts_prereleases_and_releases_without_an_apk_are_ignored() {
        assertNull(ReleaseFeed.parse(json.replace("\"draft\":false", "\"draft\":true")))
        assertNull(ReleaseFeed.parse(json.replace("\"prerelease\":false", "\"prerelease\":true")))
        assertNull(ReleaseFeed.parse(json.replace(".apk", ".zip")))
        assertNull(ReleaseFeed.parse("not json"))
        assertNotNull(ReleaseFeed.parse(json))
    }

    @Test
    fun versions_are_compared_number_by_number() {
        assertTrue(ReleaseFeed.isNewer("v1.7.0", "1.6.6"))
        assertTrue(ReleaseFeed.isNewer("v1.10.0", "1.9.9"))
        assertTrue(ReleaseFeed.isNewer("v2.0", "1.99.99"))
        assertFalse(ReleaseFeed.isNewer("v1.6.6", "1.6.6"))
        assertFalse(ReleaseFeed.isNewer("v1.6.5", "1.6.6"))
        assertFalse(ReleaseFeed.isNewer("v1.7", "1.7.0"))
    }
}
