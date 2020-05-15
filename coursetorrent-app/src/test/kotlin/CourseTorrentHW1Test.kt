package il.ac.technion.cs.softwaredesign

import com.google.inject.Guice
import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import dev.misfitlabs.kotlinguice4.getInstance
import il.ac.technion.cs.softwaredesign.exceptions.TrackerException
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import kotlin.or

class CourseTorrentHW1Test {
    private val injector = Guice.createInjector(CourseTorrentModule())
    private var torrent = injector.getInstance<CourseTorrent>()
    private var inMemoryDB = HashMap<String, ByteArray>()
    private val debian = this::class.java.getResource("/debian-10.3.0-amd64-netinst.iso.torrent").readBytes()
    private val ubuntu = this::class.java.getResource("/ubuntu-18.04.4-desktop-amd64.iso.torrent").readBytes()
    private val lame = this::class.java.getResource("/lame.torrent").readBytes()

    private var torrentsStorage = HashMap<String, ByteArray>()
    private var peersStorage = HashMap<String, ByteArray>()
    private var statsStorage = HashMap<String, ByteArray>()

    //TODO: stress test!!!
    //TODO: replace http requests since there is no Connection with the server
    //TODO: need to check the type of exceptions thrown by the mocked db
    //TODO: refactor the mocked db to a single class to be instance from each time for each test class
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
    fun `announce call returns an exception for wrong infohash`() {
        assertThrows<java.lang.IllegalArgumentException> {
            runWithTimeout(Duration.ofSeconds(10)){
            torrent.announce("invalid metainfo file", TorrentEvent.STARTED, 0, 0, 0) }}
    }

    @Test
    fun `announce call returns an exception for negative values for request params`() {
        val infohash = torrent.load(debian)
        assertThrows<TrackerException> {
            runWithTimeout(Duration.ofSeconds(10)){
                torrent.announce(infohash, TorrentEvent.STARTED, -1, 0, 0) }
        }
        assertThrows<TrackerException> {
            runWithTimeout(Duration.ofSeconds(10)){
                torrent.announce(infohash, TorrentEvent.STARTED, -1, -1, 0)
            }
        }
        assertThrows<TrackerException> {
            runWithTimeout(Duration.ofSeconds(10)){
                torrent.announce(infohash, TorrentEvent.STARTED, -1, 0, -1)
            }
        }
    }

    @Test
    fun `announce call returns update stats for negative values for request params`() {
        val infohash = torrent.load(debian)
        assertThrows<TrackerException> {
            runWithTimeout(Duration.ofSeconds(10)){
                torrent.announce(infohash, TorrentEvent.STARTED, -1, 0, 0)
            }
        }
        val stats = torrent.trackerStats(infohash)
        assert(stats.isNotEmpty())
    }

    @Test
    fun `announce list is shuffled after announce call`() {
        val infohash = torrent.load(debian)
        val annouceListBefore = torrent.announces(infohash)
        /* interval is 360 */
        val interval = torrent.announce(infohash, TorrentEvent.STARTED, 0, 0, 0)

        val annouceListAfter = torrent.announces(infohash)
        var diffrences = 0
        //TODO: improve the way the lists are compared
        for (i in annouceListBefore.indices) {
            if (annouceListBefore[i] != annouceListAfter[i] )
                diffrences++
                break
        }
        assert(diffrences != 0)
        /* Assertion to verify the the announce list was shuffled */
    }

    @Test
    fun `announce request update the peers DB`() {
        val infohash = torrent.load(debian)
        /* interval is 360 */
        val interval = torrent.announce(infohash, TorrentEvent.STARTED, 0, 0, 0)

        val peers = torrent.knownPeers(infohash)
        assert(peers.isNotEmpty())
        /* Assertion to verify the peers list is not empty */
    }

    @Test
    fun `client announces to tracker`() {
        val infohash = torrent.load(debian)

        /* interval is 360 */
        val interval = torrent.announce(infohash, TorrentEvent.STARTED, 0, 0, 0)

        assertThat(interval, equalTo(360))
        /* Assertion to verify that the tracker was actually called */
    }

    @Test
    fun `correct announces updates the peers DB`() {
        val infohash = torrent.load(debian)

        /* interval is 360 */
        torrent.announce(infohash, TorrentEvent.STARTED, 0, 0, 0)

        val peers = torrent.knownPeers(infohash)
        assert(peers.isNotEmpty())
        /* Assertion to verify that the tracker was actually called */
    }

    @Test
    fun `correct announces updates the stats DB`() {
        val infohash = torrent.load(debian)

        /* interval is 360 */
        torrent.announce(infohash, TorrentEvent.STARTED, 0, 0, 0)

        val stats = torrent.trackerStats(infohash)
        assert(stats.isNotEmpty())
        /* Assertion to verify that the tracker was actually called */
    }

    @Test
    fun `client scrapes tracker and updates statistics`() {
        val infohash = torrent.load(lame)

        /* Tracker has infohash, 0 complete, 0 downloaded, 0 incomplete, no name key */
        assertDoesNotThrow { torrent.scrape(infohash) }

        runWithTimeout(Duration.ofSeconds(10)){
            assertThat(
                torrent.trackerStats(infohash),
                equalTo(mapOf(Pair("http://127.0.0.1:8082", Scrape(0, 0, 0, null) as ScrapeData)))
            )
        }
        /* Assertion to verify that the tracker was actually called */
    }

    @Test
    fun `wrong announce change stats data from Scrape type to Failure type`() {
        val infohash = torrent.load(lame)

        /* Tracker has infohash, 0 complete, 0 downloaded, 0 incomplete, no name key */
        assertDoesNotThrow { torrent.scrape(infohash) }

        runWithTimeout(Duration.ofSeconds(10)){
            assertThat(
                torrent.trackerStats(infohash),
                equalTo(mapOf(Pair("http://127.0.0.1:8082", Scrape(0, 0, 0, null) as ScrapeData)))
            )
        }


        assertThrows<TrackerException> { torrent.announce(infohash, TorrentEvent.STARTED, -1, 0, 0) }


        assert(
            torrent.trackerStats(infohash).get("http://127.0.0.1:8082") is Failure
        )

        /* Assertion to verify that the tracker was actually called */    }

    @Test
    fun `client scrapes invalid info hash throws exception`() {
        /* Tracker has infohash, 0 complete, 0 downloaded, 0 incomplete, no name key */
        assertThrows<java.lang.IllegalArgumentException> {
            runWithTimeout(Duration.ofSeconds(10)){
                torrent.scrape("wrong infohash")
            }
        }

        /* Assertion to verify that the tracker was actually called */
    }

    @Test
    fun `after announce, client has up-to-date peer list`() {
        val infohash = torrent.load(lame)

        /* Returned peer list is: [("127.0.0.22", 6887)] */
        torrent.announce(infohash, TorrentEvent.STARTED, 0, 0, 2703360)
        /* Returned peer list is: [("127.0.0.22", 6887), ("127.0.0.21", 6889)] */
        torrent.announce(infohash, TorrentEvent.REGULAR, 0, 81920, 2621440)

        runWithTimeout(Duration.ofSeconds(10)) {
            assertThat(
                torrent.knownPeers(infohash),
                anyElement(has(KnownPeer::ip, equalTo("127.0.0.22")) and has(KnownPeer::port, equalTo(6887)))
            )
        }
        runWithTimeout(Duration.ofSeconds(10)) {
            assertThat(
                torrent.knownPeers(infohash),
                anyElement(has(KnownPeer::ip, equalTo("127.0.0.21")) and has(KnownPeer::port, equalTo(6889)))
            )
        }

        runWithTimeout(Duration.ofSeconds(10)) {
            assertThat(
                torrent.knownPeers(infohash), equalTo(torrent.knownPeers(infohash).distinct())
            )
        }
    }

    @Test
    fun `peers are invalidated correctly`() {
        val infohash = torrent.load(lame)
        /* Returned peer list is: [("127.0.0.22", 6887)] */
        torrent.announce(infohash, TorrentEvent.STARTED, 0, 0, 2703360)
        /* Returned peer list is: [("127.0.0.22", 6887), ("127.0.0.21", 6889)] */
        torrent.announce(infohash, TorrentEvent.REGULAR, 0, 81920, 2621440)

        torrent.invalidatePeer(infohash, KnownPeer("127.0.0.22", 6887, null))

        runWithTimeout(Duration.ofSeconds(10)) {
            assertThat(
                torrent.knownPeers(infohash),
                anyElement(has(KnownPeer::ip, equalTo("127.0.0.21")) and has(KnownPeer::port, equalTo(6889))).not()
            )
        }
    }

    @Test
    fun `invalidate peer will throw exception for wrong infohash`() {
        assertThrows<java.lang.IllegalArgumentException> {
            runWithTimeout(Duration.ofSeconds(10)) {
                torrent.invalidatePeer("wrong infohash", KnownPeer("127.1.1.23", 6887, null))
            }
        }
    }

    @Test
    fun `peer will not be invalidated if it's not in the peers list`() {
        val infohash = torrent.load(lame)
        /* Returned peer list is: [("127.0.0.22", 6887)] */
        torrent.announce(infohash, TorrentEvent.STARTED, 0, 0, 2703360)
        /* Returned peer list is: [("127.0.0.22", 6887), ("127.0.0.21", 6889)] */
        torrent.announce(infohash, TorrentEvent.REGULAR, 0, 81920, 2621440)
        /* nothing should happend to the list */
        torrent.invalidatePeer(infohash, KnownPeer("127.1.1.23", 6887, null))

        assertThat(
            torrent.knownPeers(infohash),
            anyElement(has(KnownPeer::port, equalTo(6887)) or has(KnownPeer::port, equalTo(6889)))
        )
    }

    @Test
    fun `trackerStats call throws exception for wrong infohash`() {
        assertThrows<java.lang.IllegalArgumentException> { torrent.trackerStats("wrong infohash") }
    }

}