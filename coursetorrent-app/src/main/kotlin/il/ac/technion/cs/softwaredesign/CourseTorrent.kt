package il.ac.technion.cs.softwaredesign

import com.google.inject.Inject
import il.ac.technion.cs.softwaredesign.exceptions.TrackerException
import kotlin.streams.toList


/**
 * This is the class implementing CourseTorrent, a BitTorrent client.
 *
 * Currently specified:
 * + Parsing torrent metainfo files (".torrent" files)
 * + Communication with trackers (announce, scrape).
 */
class CourseTorrent @Inject constructor(private val database: SimpleDB) {
    val alphaNumericID : String = Utils.getRandomChars(6)
    /**
     * Load in the torrent metainfo file from [torrent]. The specification for these files can be found here:
     * [Metainfo File Structure](https://wiki.theory.org/index.php/BitTorrentSpecification#Metainfo_File_Structure).
     *
     * After loading a torrent, it will be available in the system, and queries on it will succeed.
     *
     * This is a *create* command.
     *
     * @throws IllegalArgumentException If [torrent] is not a valid metainfo file.
     * @throws IllegalStateException If the infohash of [torrent] is already loaded.
     * @return The infohash of the torrent, i.e., the SHA-1 of the `info` key of [torrent].
     */
    fun load(torrent: ByteArray): String {
        val torrentData = TorrentFile.deserialize(torrent)
        val infohash = torrentData.infohash
        try {
            database.torrentsCreate(infohash, torrentData.announceList)
        } catch (e : IllegalStateException) {
            throw IllegalStateException("Same infohash already loaded")
        }
        database.peersCreate(infohash, listOf())
        database.statsCreate(infohash, mapOf())
        return infohash
    }

    /**
     * Remove the torrent identified by [infohash] from the system.
     *
     * This is a *delete* command.
     *
     * @throws IllegalArgumentException If [infohash] is not loaded.
     */
    fun unload(infohash: String): Unit {
        try {
            database.torrentsDelete(infohash)
            database.peersDelete(infohash)
            database.statsDelete(infohash)
        } catch (e : IllegalArgumentException) {
            throw IllegalArgumentException("Infohash doesn't exist")
        }
    }

    /**
     * Return the announce URLs for the loaded torrent identified by [infohash].
     *
     * See [BEP 12](http://bittorrent.org/beps/bep_0012.html) for more information. This method behaves as follows:
     * * If the "announce-list" key exists, it will be used as the source for announce URLs.
     * * If "announce-list" does not exist, "announce" will be used, and the URL it contains will be in tier 1.
     * * The announce URLs should *not* be shuffled.
     *
     * This is a *read* command.
     *
     * @throws IllegalArgumentException If [infohash] is not loaded.
     * @return Tier lists of announce URLs.
     */
    fun announces(infohash: String): List<List<String>> {
        val torrentData = TorrentFile(infohash, database.torrentsRead(infohash)) //throws IllegalArgumentException
        return torrentData.announceList
    }

    /**
     * Send an "announce" HTTP request to a single tracker of the torrent identified by [infohash], and update the
     * internal state according to the response. The specification for these requests can be found here:
     * [Tracker Protocol](https://wiki.theory.org/index.php/BitTorrentSpecification#Tracker_HTTP.2FHTTPS_Protocol).
     *
     * If [event] is [TorrentEvent.STARTED], shuffle the announce-list before selecting a tracker (future calls to
     * [announces] should return the shuffled list). See [BEP 12](http://bittorrent.org/beps/bep_0012.html) for more
     * information on shuffling and selecting a tracker.
     *
     * [event], [uploaded], [downloaded], and [left] should be included in the tracker request.
     *
     * The "compact" parameter in the request should be set to "1", and the implementation should support both compact
     * and non-compact peer lists.
     *
     * Peer ID should be set to "-CS1000-{Student ID}{Random numbers}", where {Student ID} is the first 6 characters
     * from the hex-encoded SHA-1 hash of the student's ID numbers (i.e., `hex(sha1(student1id + student2id))`), and
     * {Random numbers} are 6 random characters in the range [0-9a-zA-Z] generated at instance creation.
     *
     * If the connection to the tracker failed or the tracker returned a failure reason, the next tracker in the list
     * will be contacted and the announce-list will be updated as per
     * [BEP 12](http://bittorrent.org/beps/bep_0012.html).
     * If the final tracker in the announce-list has failed, then a [TrackerException] will be thrown.
     *
     * This is an *update* command.
     *
     * @throws TrackerException If the tracker returned a "failure reason". The failure reason will be the exception
     * message.
     * @throws IllegalArgumentException If [infohash] is not loaded.
     * @return The interval in seconds that the client should wait before announcing again.
     */
    fun announce(infohash: String, event: TorrentEvent, uploaded: Long, downloaded: Long, left: Long): Int {
        val torrentFile = TorrentFile(infohash, database.torrentsRead(infohash)) //throws IllegalArgumentException
        if(event == TorrentEvent.STARTED) torrentFile.shuffleAnnounceList()
        val params = listOf("info_hash" to Utils.urlEncode(infohash),
                    "peer_id" to this.getPeerID(),
                    "port" to "6881", //Matan said to leave it like that, will be changed in future assignments
                    "uploaded" to uploaded.toString(),
                    "downloaded" to downloaded.toString(),
                    "left" to left.toString(),
                    "compact" to "1",
                    "event" to event.asString)
        val response : Map<String, Any>
        try {
            response = torrentFile.announceTracker(params, database) //throws TrackerException
        }
        finally {
            database.torrentsUpdate(infohash, torrentFile.announceList)
        }
        val peers:List<Map<String, String>> = getPeersFromResponse(response)
        addToPeers(infohash, peers)
        return (response["interval"] as Long).toInt()
    }

    /**
     * Scrape all trackers identified by a torrent, and store the statistics provided. The specification for the scrape
     * request can be found here:
     * [Scrape Protocol](https://wiki.theory.org/index.php/BitTorrentSpecification#Tracker_.27scrape.27_Convention).
     *
     * All known trackers for the torrent will be scraped.
     *
     * This is an *update* command.
     *
     * @throws IllegalArgumentException If [infohash] is not loaded.
     */
    fun scrape(infohash: String): Unit {
        val torrentFile = TorrentFile(infohash, database.torrentsRead(infohash)) //throws IllegalArgumentException
        val torrentAllStats = torrentFile.scrapeTrackers(database)
        //update the current stats of the torrent file in the stats db
        database.statsUpdate(infohash, torrentAllStats)
    }

    /**
     * Invalidate a previously known peer for this torrent.
     *
     * If [peer] is not a known peer for this torrent, do nothing.
     *
     * This is an *update* command.
     *
     * @throws IllegalArgumentException If [infohash] is not loaded.
     */
    fun invalidatePeer(infohash: String, peer: KnownPeer): Unit {
        val peersList = database.peersRead(infohash) //throws IllegalArgumentException
        val newPeerslist = peersList.filter { it->  (it["ip"] != peer.ip)  || (it["port"] != peer.port.toString()) }
        database.peersUpdate(infohash, newPeerslist)
    }

    /**
     * Return all known peers for the torrent identified by [infohash], in sorted order. This list should contain all
     * the peers that the client can attempt to connect to, in ascending numerical order. Note that this is not the
     * lexicographical ordering of the string representation of the IP addresses: i.e., "127.0.0.2" should come before
     * "127.0.0.100".
     *
     * The list contains unique peers, and does not include peers that have been invalidated.
     *
     * This is a *read* command.
     *
     * @throws IllegalArgumentException If [infohash] is not loaded.
     * @return Sorted list of known peers.
     */
    fun knownPeers(infohash: String): List<KnownPeer> {
        val peersList = database.peersRead(infohash) //throws IllegalArgumentException
        val sortedPeers = peersList.stream().map {
            it -> KnownPeer(it["ip"] as String,it["port"]?.toInt() ?: 0,it["peer id"] as String?)
        }.sorted { o1, o2 -> Utils.compareIPs(o1.ip,o2.ip)}.toList()
        return sortedPeers
    }

    /**
     * Return all known statistics from trackers of the torrent identified by [infohash]. The statistics displayed
     * represent the latest information seen from a tracker.
     *
     * The statistics are updated by [announce] and [scrape] calls. If a response from a tracker was never seen, it
     * will not be included in the result. If one of the values of [ScrapeData] was not included in any tracker response
     * (e.g., "downloaded"), it would be set to 0 (but if there was a previous result that did include that value, the
     * previous result would be shown).
     *
     * If the last response from the tracker was a failure, the failure reason would be returned ([ScrapeData] is
     * defined to allow for this). If the failure was a failed connection to the tracker, the reason should be set to
     * "Connection failed".
     *
     * This is a *read* command.
     *
     * @throws IllegalArgumentException If [infohash] is not loaded.
     * @return A mapping from tracker announce URL to statistics.
     */
    fun trackerStats(infohash: String): Map<String, ScrapeData> {
        val dbStatsMap = database.statsRead(infohash) //throws IllegalArgumentException
        val trackerStatsMap = hashMapOf<String, ScrapeData>()
        for ((trackerUrl, trackerValue) in dbStatsMap) {
            val trackerMap = trackerValue as Map<String, Any>
            if(trackerMap.containsKey("failure reason")) {
                trackerStatsMap[trackerUrl] = Failure(trackerMap["failure reason"] as String)
            } else {
                trackerStatsMap[trackerUrl] = Scrape((trackerMap["complete"]as Long).toInt(),
                        (trackerMap["downloaded"]as Long).toInt(),
                        (trackerMap["incomplete"]as Long).toInt(),
                        trackerMap["name"] as? String?)
            }
        }
        return trackerStatsMap
    }

    /**
     * Returns the peer ID of the client
     * Peer ID should be set to "-CS1000-{Student ID}{Random numbers}", where {Student ID} is the first 6 characters
     * from the hex-encoded SHA-1 hash of the student's ID numbers (i.e., `hex(sha1(student1id + student2id))`), and
     * {Random numbers} are 6 random characters in the range [0-9a-zA-Z] generated at instance creation.
     */
    private fun getPeerID(): String {
        val studentIDs = "206989105308328467"
        val builder = StringBuilder()
        builder.append("-CS1000-")
        builder.append(Utils.sha1hash(studentIDs.toByteArray()).substring(0, 6))
        builder.append(alphaNumericID)
        return builder.toString()
    }

    private fun addToPeers(infohash : String, newPeers : List<Map<String, String>>) {
        val currPeers = database.peersRead(infohash).toSet().toMutableSet()
        currPeers.addAll(newPeers) //Matan said that peers with same IP, same port, but different peer id will not be tested
        database.peersUpdate(infohash, currPeers.toList())
    }

    /**
     * If the response is not compact, return string as-is. Otherwise, turn the compact string
     * into non-compact and then return
     */
    private fun getPeersFromResponse(response: Map<String, Any>):List<Map<String, String>> {
        assert(response.containsKey("peers"))
        if(response["peers"] is List<*>) {
            return response["peers"] as List<Map<String, String>>
        }
        else {
            val peersByteArray = response["peers"] as ByteArray
            val peers = mutableListOf<Map<String, String>>()
            var i = 0
            while(i < peersByteArray.size) {
                peers.add(mapOf(
                        "ip" to (peersByteArray[i].toUByte().toInt().toString() + "." + peersByteArray[i+1].toUByte().toInt().toString() + "."
                                + peersByteArray[i+2].toUByte().toInt().toString() + "." + peersByteArray[i+3].toUByte().toInt().toString()),
                        "port" to (peersByteArray[i+4].toUByte().toInt() * 256 + peersByteArray[i+5].toUByte().toInt()).toString()
                ))
                i += 6
            }
            return peers
        }
    }
}