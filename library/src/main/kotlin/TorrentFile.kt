package il.ac.technion.cs.softwaredesign

import java.lang.Exception
import java.lang.IllegalArgumentException
import java.security.MessageDigest

class TorrentFile(torrent: ByteArray) {
    val info : ByteArray
    var announceList : List<List<String>>
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
        if(!torrentData.containsKey("info")) throw IllegalArgumentException("Invalid metainfo file")

        creationDate = torrentData["creation date"] as? String?
        comment = torrentData["comment"] as? String?
        createdBy = torrentData["created by"] as? String?
        info = torrentData["infoEncoded"] as ByteArray
        if(torrentData.containsKey("announce-list")) {
            try {
                announceList = torrentData["announce-list"] as List<List<String>>
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

    /**
     * Shuffles the order in each tier of the announcelist
     */
    fun shuffleAnnounceList():Unit {
        announceList = announceList.map { it.shuffled() }
    }

    /**
     * -Announces to a tracker, by the order specified in the BitTorrent specification
     * -This function is responsible for attaching the trackerid to the params if necessary,
     *  which is why the params parameter is mutable
     * -This function is also responsible for reordering the trackers if necessary,
     *  as requested by the BitTorrent specification
     * @throws TrackerException if no trackers return a non-failure response
     * @returns the response string from the tracker, de-bencoded
     */
    fun announceTracker(params: MutableList<Pair<String, String>>) : Map<String, Any> = TODO("Implement me!")
}