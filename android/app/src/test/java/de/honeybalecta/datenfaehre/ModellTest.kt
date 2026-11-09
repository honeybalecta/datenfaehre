package de.honeybalecta.datenfaehre

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModellTest {
    @Test fun pfade() {
        assertEquals("/a/b", Pfad.verbinde("/a", "b"))
        assertEquals("/b", Pfad.verbinde("/", "b"))
        assertEquals("/", Pfad.eltern("/a"))
        assertEquals("/a", Pfad.eltern("/a/b/"))
        assertEquals("/", Pfad.eltern("/"))
        assertEquals("b.txt", Pfad.name("/a/b.txt"))
    }

    @Test fun groessen() {
        assertEquals("512 B", groesseText(512))
        assertEquals("1,5 KB", groesseText(1536))
        assertEquals("12 MB", groesseText(12L * 1024 * 1024))
        assertEquals("", groesseText(-1))
    }

    @Test fun schnellverbindung() {
        val a = schnellprofil("sftp://example.org", "u", "p", "")!!
        assertEquals(Protokoll.SFTP, a.protokoll)
        assertEquals(22, a.port)
        val b = schnellprofil("example.org", "u", "p", "")!!
        assertEquals(Protokoll.FTPS, b.protokoll)
        assertEquals(21, b.port)
        val c = schnellprofil("example.org:2222", "u", "p", "")!!
        assertEquals("example.org", c.host)
        assertEquals(2222, c.port)
        val d = schnellprofil("example.org", "u", "p", "22")!!
        assertEquals(Protokoll.SFTP, d.protokoll)
        val e = schnellprofil("ftp://example.org/", "u", "p", "")!!
        assertEquals(Protokoll.FTP, e.protokoll)
        assertEquals("example.org", e.host)
        assertNull(schnellprofil("", "u", "p", ""))
        assertNull(schnellprofil("http://example.org", "u", "p", ""))
    }
}
