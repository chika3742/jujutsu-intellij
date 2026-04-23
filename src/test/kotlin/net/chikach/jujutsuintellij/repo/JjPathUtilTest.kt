package net.chikach.jujutsuintellij.repo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JjPathUtilTest {

    @Test
    fun `normalizes relative path separators`() {
        assertEquals("dir/file.txt", JjPathUtil.normalizeRelativePath("dir\\file.txt"))
    }

    @Test
    fun `relativizes paths under repository root`() {
        assertEquals("", JjPathUtil.relativize("/repo/", "/repo"))
        assertEquals("dir/file.txt", JjPathUtil.relativize("/repo/", "/repo/dir/file.txt"))
    }

    @Test
    fun `rejects paths outside repository root`() {
        assertNull(JjPathUtil.relativize("/repo", "/repo-other/file.txt"))
        assertFalse(JjPathUtil.isUnderRoot("/repo", "/repo-other/file.txt"))
        assertTrue(JjPathUtil.isUnderRoot("/repo", "/repo/dir/file.txt"))
    }
}
