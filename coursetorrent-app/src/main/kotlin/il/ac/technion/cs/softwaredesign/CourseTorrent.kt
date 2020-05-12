package il.ac.technion.cs.softwaredesign

import com.github.kittinunf.fuel.httpGet
import com.google.inject.Inject
import il.ac.technion.cs.softwaredesign.exceptions.TrackerException
import java.lang.Exception
import kotlin.collections.LinkedHashMap
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
        val torrentData = TorrentFile(torrent)
        val infohash = torrentData.getInfohash()
        database.setCurrentStorage(Databases.TORRENTS)
        try {
            database.create(infohash, torrentData.getBencodedString().toByteArray())
        } catch (e : IllegalStateException) {
            throw IllegalStateException("Same infohash already loaded")
        }
        database.setCurrentStorage(Databases.PEERS)
        database.create(infohash, Ben.encodeStr(listOf<Map<String, Any>>()).toByteArray())
        database.setCurrentStorage(Databases.STATS)
        database.create(infohash, Ben.encodeStr(mapOf<String, Any>()).toByteArray())
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
            database.setCurrentStorage(Databases.TORRENTS)
            database.delete(infohash)
            database.setCurrentStorage(Databases.PEERS)
            database.delete(infohash)
            database.setCurrentStorage(Databases.STATS)
            database.delete(infohash)
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
        database.setCurrentStorage(Databases.TORRENTS)
        val torrent : ByteArray
        try {
            torrent = database.read(infohash)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Infohash doesn't exist")
        }
        val torrentData =
                TorrentFile(torrent) //safe because it was checked in load
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
        database.setCurrentStorage(Databases.TORRENTS)
        val torrentFile = TorrentFile(database.read(infohash)) //throws IllegalArgumentException
        if(event == TorrentEvent.STARTED) torrentFile.shuffleAnnounceList()
        val params = listOf("info_hash" to infohash,
                    "peer_id" to this.getPeerID(),
                    "port" to "6881", //Matan said to leave it like that, will be changed in future assignments
                    "uploaded" to uploaded.toString(),
                    "downloaded" to downloaded.toString(),
                    "left" to left.toString(),
                    "compact" to "1",
                    "event" to event.asString)
        val response = torrentFile.announceTracker(params, database, infohash) //throws TrackerException
        val peers:List<Map<String, Any>> = Utils.getPeersFromResponse(response)
        database.setCurrentStorage(Databases.TORRENTS)
        database.update(infohash, torrentFile.toByteArray())
        database.setCurrentStorage(Databases.PEERS)
        database.update(infohash, Ben.encodeStr(peers).toByteArray())
        return response["interval"] as Int
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
        //TODO function is too big, split into helper functions (maybe in TorrentFile.kt?)
        //TODO read the old STATS and don't change the name if it was set before and is now null
        val torrentAllStats = HashMap<String,Any>()
        database.setCurrentStorage(Databases.TORRENTS)
        val torrentFile = TorrentFile(database.read(infohash)) //throws IllegalArgumentException
        for(tier in torrentFile.announceList) {
            for(trackerURL in tier) {
                val scrapeURL =trackerURL
                //find the last accourance of '/' and if it followed by "announce" then change to string to "scrape" and send request
                var lastSlash = scrapeURL.lastIndexOf('/')
                if (scrapeURL.substring(lastSlash,lastSlash+"announce".length) == "announce"){
                    scrapeURL.replaceAfterLast("/announce","/scrape")
                    val params = listOf("info_hash" to infohash)

                    var currentStatsDict : Map<String, Any>
                    try {
                        val responseMessageString = scrapeURL.httpGet(params).response().second.responseMessage
                        currentStatsDict = Ben(responseMessageString.toByteArray()).decode() as Map<String, Any>
                    } catch (e : Exception) {
                        currentStatsDict = mapOf("failure reason" to "Connection failed")
                    }


                    if (currentStatsDict.isEmpty()) {
                        currentStatsDict = mapOf("failure reason" to "Connection failed")
                    }

                    when {
                        currentStatsDict.containsKey("failure reason") -> {
                            torrentAllStats[trackerURL] = currentStatsDict
                        }
                        else -> {
                            //insert the stats about the current files
                            torrentAllStats[trackerURL] = (currentStatsDict["files"] as Map<String, Any>)[infohash] as Map<String, Any>
                        }
                    }


                }
            }
        }
        //update the current stats of the torrent file in the stats db
        database.setCurrentStorage(Databases.STATS)
        database.update(infohash,Ben.encodeStr(torrentAllStats).toByteArray())

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
        database.setCurrentStorage(Databases.PEERS)
        val peersByteArray = database.read(infohash) //throws IllegalArgumentException
        val peersList :List<Map<String, Any>> = Ben(peersByteArray).decode() as List<Map<String, Any>>
        val newPeerslist = peersList.filter { it->  (it["IP"] != peer.ip)  }

        database.update(infohash, Ben.encodeStr(newPeerslist).toByteArray())

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
        database.setCurrentStorage(Databases.PEERS)
        val peersByteArray = database.read(infohash) //throws IllegalArgumentException
        val peersList :List<Map<String, Any>> = Ben(peersByteArray).decode() as List<Map<String, Any>>

        val sortedPeers = peersList.stream().map { it -> KnownPeer(it["ip"] as String,it["port"] as Int,it["peerId"] as String?)}.sorted { o1, o2 -> Utils.compareIPs(o1.ip,o2.ip)}.toList()

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
        database.setCurrentStorage(Databases.STATS)
        val statsByteArray = database.read(infohash) //throws IllegalArgumentException
        val dbStatsMap = Ben(statsByteArray).decode() as Map<String,Any>
        val trackerStatsMap = hashMapOf<String, ScrapeData>()
        for ((trackerUrl, trackerValue) in dbStatsMap) {
            val trackerMap = trackerValue as Map<String, Any>
            if(trackerMap.containsKey("failure reason")) {
                trackerStatsMap[trackerUrl] = Failure(trackerMap["failure reason"] as String)
            } else {
                trackerStatsMap[trackerUrl] = Scrape(trackerMap["complete"] as Int,
                        trackerMap["downloaded"] as Int,
                        trackerMap["incomplete"] as Int,
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
        builder.append(Utils.sha1hash(studentIDs.toByteArray()))
        builder.append(alphaNumericID)
        return builder.toString()
    }
}