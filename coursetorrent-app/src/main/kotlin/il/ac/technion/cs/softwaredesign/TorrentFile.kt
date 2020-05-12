package il.ac.technion.cs.softwaredesign

import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.result.Result
import il.ac.technion.cs.softwaredesign.exceptions.TrackerException
import java.lang.Exception
import java.lang.IllegalArgumentException

class TorrentFile(torrent: ByteArray) {
    val info : ByteArray
    var announceList : MutableList<MutableList<String>>
        private set
    val creationDate : String?
    val comment : String?
    val createdBy : String?

    /**
     * Initialize torrent file from torrent byte array
     * @throws IllegalArgumentException if the supplied byte array is an not a valid metainfo file
     */
    init {
        val torrentData : Map<String, Any>
        try {
            torrentData = Ben(torrent).decode() as Map<String, Any>
        } catch (e : Exception) {
            throw IllegalArgumentException("Invalid metainfo file")
        }
        if(!torrentData.containsKey("infoEncoded")) {
            info = ByteArray(0)
        }
        else {
            info = torrentData["infoEncoded"] as ByteArray
        }

        creationDate = torrentData["creation date"] as? String?
        comment = torrentData["comment"] as? String?
        createdBy = torrentData["created by"] as? String?
        if(torrentData.containsKey("announce-list")) {
            try {
                announceList = torrentData["announce-list"] as MutableList<MutableList<String>>
            } catch (e : Exception) {
                throw IllegalArgumentException("Invalid metainfo file")
            }
        }
        else {
            val announce : String = torrentData["announce"] as String
            announceList = arrayListOf(arrayListOf(announce))
        }
    }

    fun toByteArray() : ByteArray {
        val map : HashMap<String, Any> = hashMapOf("infoEncoded" to info, "announce-list" to announceList)
        if(creationDate != null) map["creation date"] = creationDate
        if(comment != null) map["comment"] = comment
        if(createdBy != null) map["created by"] = createdBy
        return Ben.encodeStr(map).toByteArray()
    }

    fun getInfohash() : String = Utils.sha1hash(info)

    public fun getBencodedString() : String {
        val torrentMap = HashMap<String, Any>()
        torrentMap.put("announce-list", announceList)
        return Ben.encodeStr(torrentMap)
    }

    /**
     * Shuffles the order in each tier of the announcelist
     */
    fun shuffleAnnounceList():Unit {
        announceList.map{ it.shuffle() }
    }

    /**
     * -Announces to a tracker, by the order specified in the BitTorrent specification
     * -The trackerid functionality is *not* required in the assignment (Matan said)
     * -This function is also responsible for reordering the trackers if necessary,
     *  as requested by the BitTorrent specification
     * -This function also updates the tracker stats for every tracker it attempts
     *  to get a response from
     * @throws TrackerException if no trackers return a non-failure response
     * @returns the response string from the tracker, de-bencoded
     */
    fun announceTracker(params: List<Pair<String, String>>, database: SimpleDB, dbKey: String) : Map<String, Any> {
        database.setCurrentStorage(Databases.STATS)
        val trackerStats = (Ben(database.read(dbKey)).decode() as Map<String, Any>).toMutableMap()
        var lastErrorMessage = "Empty announce list"
        for(tier in this.announceList) {
            for(trackerURL in tier) {
                val (_, _, result) = trackerURL.httpGet(params).response()
                if(result is Result.Failure) {
                    lastErrorMessage = "Connection failed"
                    trackerStats[trackerURL] = mapOf("failure reason" to lastErrorMessage)
                    continue
                }
                else {
                    //successful connection
                    val responseMap : Map<String, Any>? = Ben(result.get()).decode() as? Map<String, Any>?
                    if(responseMap == null || !responseMap.containsKey("peers")) {
                        lastErrorMessage = "Connection failed" //response invalid
                        trackerStats[trackerURL] = mapOf("failure reason" to lastErrorMessage)
                        continue
                    }
                    if(responseMap.containsKey("failure reason")) {
                        lastErrorMessage = responseMap["failure reason"] as String
                        trackerStats[trackerURL] = mapOf("failure reason" to lastErrorMessage)
                        continue
                    }
                    //reorder the tier to have the successful tracker at index 0
                    tier.remove(trackerURL)
                    tier.add(0, trackerURL)
                    //update tracker stats
                    trackerStats[trackerURL] = Scrape(responseMap["complete"] as Int,
                            (trackerStats[trackerURL] as? Map<String, Any>?)?.get("downloaded") as? Int ?: 0,
                            responseMap["incomplete"] as Int,
                            (trackerStats[trackerURL] as? Map<String, Any>?)?.get("name") as? String?)
                    database.update(dbKey, Ben.encodeStr(trackerStats).toByteArray())
                    //return the response map
                    return responseMap
                }
            }
        }
        database.update(dbKey, Ben.encodeStr(trackerStats).toByteArray())
        throw TrackerException(lastErrorMessage)
    }

    /**
     * -Scrapes tracker
     * -This function also updates the tracker stats for every tracker if it can be scraped
     * @throws IllegalArgumentException If [infohash] is not loaded.
     */
    fun scrapeTrackers(infohash: String, database: SimpleDB):  Map<String, Any>{
        database.setCurrentStorage(Databases.STATS)
        val torrentAllStats = (Ben(database.read(infohash)).decode() as Map<String, Any>).toMutableMap()

        database.setCurrentStorage(Databases.TORRENTS)
        val torrentFile = TorrentFile(database.read(infohash)) //throws IllegalArgumentException
        for (tier in torrentFile.announceList) {
            for (trackerURL in tier) {
                val scrapeURL = trackerURL
                //find the last accourance of '/' and if it followed by "announce" then change to string to "scrape" and send request
                var lastSlash = scrapeURL.lastIndexOf('/')
                if (scrapeURL.substring(lastSlash, lastSlash + "announce".length) == "announce") {

                    var newTrackerStatsMap: Map<String, Any> = sendScrapeRequest(scrapeURL, infohash)

                    newTrackerStatsMap = updateCurrentTrackerStats(newTrackerStatsMap, torrentAllStats, trackerURL, infohash)

                }
            }
        }
        return torrentAllStats
    }

    /**
     * -Update the tracker stats according to the response map received from the HTTP get request
     */
    private fun updateCurrentTrackerStats(newTrackerStatsMap: Map<String, Any>, torrentAllStats: MutableMap<String, Any>, trackerURL: String, infohash: String): Map<String, Any> {
        var changedTrackerStats = newTrackerStatsMap
        if (changedTrackerStats.isEmpty()) {
            changedTrackerStats = mapOf("failure reason" to "Connection failed")
        }

        var oldName = (torrentAllStats[trackerURL] as? Map<String, Any>?)?.get("name") as? String?

        when {
            changedTrackerStats.containsKey("failure reason") -> {
                torrentAllStats[trackerURL] = mapOf("name" to ((changedTrackerStats[trackerURL] as? Map<String, Any>?)?.get("name") as? String
                        ?: oldName),
                        "failure reason" to changedTrackerStats["recievedDict"]) as Map<String, Any>
            }
            else -> {
                //insert the stats about the current files
                torrentAllStats[trackerURL] = (changedTrackerStats["files"] as Map<String, Any>)[infohash] as Map<String, Any>
                torrentAllStats[trackerURL] = Scrape((changedTrackerStats["files"] as? Map<String, Any>?)?.get("complete") as? Int
                        ?: 0,
                        (changedTrackerStats["files"] as? Map<String, Any>?)?.get("downloaded") as? Int ?: 0,
                        (changedTrackerStats["files"] as? Map<String, Any>?)?.get("incomplete") as? Int ?: 0,
                        (changedTrackerStats[trackerURL] as? Map<String, Any>?)?.get("name") as? String ?: oldName)
            }
        }
        return changedTrackerStats
    }

    /**
     * -Send HTTP ger request and decode the received map
     */
    private fun sendScrapeRequest(scrapeURL: String, infohash: String): Map<String, Any> {
        scrapeURL.replaceAfterLast("/announce", "/scrape")
        val params = listOf("info_hash" to infohash)

        var newTrackerStatsMap: Map<String, Any>
        try {
            val responseMessageString = scrapeURL.httpGet(params).response().second.responseMessage
            newTrackerStatsMap = Ben(responseMessageString.toByteArray()).decode() as Map<String, Any>
        } catch (e: Exception) {
            newTrackerStatsMap = mapOf("failure reason" to "Connection failed")
        }
        return newTrackerStatsMap
    }
}