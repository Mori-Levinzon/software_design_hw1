package il.ac.technion.cs.softwaredesign

import com.natpryce.hamkrest.allElements
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.hasElement
import com.natpryce.hamkrest.hasSize
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.nio.charset.Charset

class MockedStaffTest {
    private var torrent = CourseTorrent()
    private var inMemoryDB = HashMap<String, ByteArray>()
    private val debian = this::class.java.getResource("/debian-10.3.0-amd64-netinst.iso.torrent").readBytes()

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
    fun `after load, announce is correct`() {
        val infohash = torrent.load(debian)

        val announces = torrent.announces(infohash)

        assertThat(announces, allElements(hasSize(equalTo(1))))
        assertThat(announces, hasSize(equalTo(1)))
        assertThat(announces, allElements(hasElement("http://bttracker.debian.org:6969/announce")))
    }
}