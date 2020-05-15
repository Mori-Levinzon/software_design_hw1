package il.ac.technion.cs.softwaredesign

import com.google.inject.Guice
import com.natpryce.hamkrest.allElements
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.hasElement
import com.natpryce.hamkrest.hasSize
import dev.misfitlabs.kotlinguice4.getInstance
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
    private val injector = Guice.createInjector(CourseTorrentModule())
    private var torrent = injector.getInstance<CourseTorrent>()
    private var inMemoryDB = HashMap<String, ByteArray>()
    private val debian = this::class.java.getResource("/debian-10.3.0-amd64-netinst.iso.torrent").readBytes()
    private var torrentsStorage = HashMap<String, ByteArray>()
    private var peersStorage = HashMap<String, ByteArray>()
    private var statsStorage = HashMap<String, ByteArray>()


    @BeforeEach
    fun `initialize CourseTorrent with mocked DB`() {
        val memoryDB = mockk<SimpleDB>()
        var key = slot<String>()
        var torrentsValue = slot<List<List<String>>>()
        var peersValue = slot<List<Map<String, String>>>()
        var statsValue = slot<Map<String, Map<String, Any>>>()


        every { memoryDB.torrentsCreate(capture(key), capture(torrentsValue)) } answers {
            if(torrentsStorage.containsKey(key.captured)) throw IllegalStateException()
            torrentsStorage[key.captured] = Ben.encodeStr(torrentsValue.captured).toByteArray()
        }
        every { memoryDB.peersCreate(capture(key), capture(peersValue)) } answers {
            if(peersStorage.containsKey(key.captured)) throw IllegalStateException()
            peersStorage[key.captured] = Ben.encodeStr(peersValue.captured).toByteArray()
        }
        every { memoryDB.statsCreate(capture(key), capture(statsValue)) } answers {
            if(statsStorage.containsKey(key.captured)) throw IllegalStateException()
            statsStorage[key.captured] = Ben.encodeStr(statsValue.captured).toByteArray()
        }

        every { memoryDB.torrentsUpdate(capture(key), capture(torrentsValue)) } answers {
            if(!torrentsStorage.containsKey(key.captured)) throw IllegalArgumentException()
            torrentsStorage[key.captured] = Ben.encodeStr(torrentsValue.captured).toByteArray()
        }
        every { memoryDB.peersUpdate(capture(key), capture(peersValue)) } answers {
            if(!peersStorage.containsKey(key.captured)) throw IllegalArgumentException()
            peersStorage[key.captured] = Ben.encodeStr(peersValue.captured).toByteArray()
        }
        every { memoryDB.statsUpdate(capture(key), capture(statsValue)) } answers {
            if(!statsStorage.containsKey(key.captured)) throw IllegalArgumentException()
            statsStorage[key.captured] = Ben.encodeStr(statsValue.captured).toByteArray()
        }

        every { memoryDB.torrentsRead(capture(key)) } answers {
            Ben(torrentsStorage[key.captured] as ByteArray).decode() as List<List<String>>? ?: throw IllegalArgumentException()
        }
        every { memoryDB.peersRead(capture(key)) } answers {
            Ben(peersStorage[key.captured] as ByteArray).decode() as List<Map<String, String>>? ?: throw IllegalArgumentException()
        }
        every { memoryDB.statsRead(capture(key)) } answers {
            Ben(statsStorage[key.captured] as ByteArray).decode() as Map<String, Map<String, Any>>? ?: throw IllegalArgumentException()
        }

        every { memoryDB.torrentsDelete(capture(key)) } answers {
            torrentsStorage.remove(key.captured) ?: throw IllegalArgumentException()
        }
        every { memoryDB.peersDelete(capture(key)) } answers {
            peersStorage.remove(key.captured) ?: throw IllegalArgumentException()
        }
        every { memoryDB.statsDelete(capture(key)) } answers {
            statsStorage.remove(key.captured) ?: throw IllegalArgumentException()
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