package il.ac.technion.cs.softwaredesign

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException

class CourseTorrentTest {
    private var torrent = CourseTorrent()
    private var inMemoryDB = HashMap<String, ByteArray>()
    private val debian = this::class.java.getResource("/debian-10.3.0-amd64-netinst.iso.torrent").readBytes()
    private val ubuntu = this::class.java.getResource("/ubuntu-18.04.4-desktop-amd64.iso.torrent").readBytes()

    @BeforeEach
    fun `initialize CourseTorrent with mocked DB`() {
        val memoryDB = mockk<SimpleDB>()
        var key = slot<String>()
        var value = slot<ByteArray>()
        every { memoryDB.create(capture(key), capture(value)) } answers {
            if(inMemoryDB.containsKey(key.captured)) throw IllegalStateException()
            inMemoryDB[key.captured] = value.captured
        }
        every { memoryDB.update(capture(key), capture(value)) } answers {
            if(!inMemoryDB.containsKey(key.captured)) throw IllegalArgumentException()
            inMemoryDB[key.captured] = value.captured
        }
        every { memoryDB.read(capture(key)) } answers {
            inMemoryDB[key.captured] ?: throw IllegalArgumentException()
        }
        every { memoryDB.delete(capture(key)) } answers {
            inMemoryDB.remove(key.captured) ?: throw IllegalArgumentException()
        }
        torrent = CourseTorrent(memoryDB)
    }

    @Test
    fun `after load, infohash calculated correctly`() {
        val infohash = torrent.load(debian)

        assertThat(infohash, equalTo("5a8062c076fa85e8056451c0d9aa04349ae27909"))
    }

    @Test
    fun `load rejects invalid file`() {
        assertThrows<IllegalArgumentException> { torrent.load("invalid metainfo file".toByteArray(Charsets.UTF_8)) }
    }

    @Test
    fun `after load, can't load again`() {
        torrent.load(debian)

        assertThrows<IllegalStateException> { torrent.load(debian) }
    }

    @Test
    fun `after unload, can load again`() {
        val infohash = torrent.load(debian)

        torrent.unload(infohash)

        assertThat(torrent.load(debian), equalTo(infohash))
    }

    @Test
    fun `can't unload a new file`() {
        assertThrows<IllegalArgumentException> { torrent.unload("infohash") }
    }

    @Test
    fun `can't unload a file twice`() {
        val infohash = torrent.load(ubuntu)

        torrent.unload(infohash)

        assertThrows<IllegalArgumentException> { torrent.unload(infohash) }
    }

    @Test
    fun `torrent with announce-list works correctly`() {
        val infohash = torrent.load(ubuntu)

        val announces = torrent.announces(infohash)

        assertThat(announces, allElements(hasSize(equalTo(1))))
        assertThat(announces, hasSize(equalTo(2)))
        assertThat(announces[0][0], equalTo("https://torrent.ubuntu.com/announce"))
        assertThat(announces[1][0], equalTo("https://ipv6.torrent.ubuntu.com/announce"))
    }

    @Test
    fun `torrent with announce (not list) works correctly`() {
        val infohash = torrent.load(debian)

        val announces = torrent.announces(infohash)

        assertThat(announces, allElements(hasSize(equalTo(1))))
        assertThat(announces, hasSize(equalTo(1)))
        assertThat(announces, allElements(hasElement("http://bttracker.debian.org:6969/announce")))
    }

    @Test
    fun `announces rejects unloaded torrents`() {
        val infohash = torrent.load(ubuntu)
        torrent.unload(infohash)

        assertThrows<IllegalArgumentException> { torrent.announces(infohash) }
    }

    @Test
    fun `announces rejects unrecognized torrents`() {
        assertThrows<IllegalArgumentException> { torrent.announces("new infohash") }
    }

}