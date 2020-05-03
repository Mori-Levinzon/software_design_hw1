package il.ac.technion.cs.softwaredesign

import java.lang.Exception
import java.lang.IllegalArgumentException
import java.security.MessageDigest

class TorrentFile(torrent: ByteArray) {
    val info : ByteArray
    val announceList : List<List<String>>
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
        createdBy = torrentData["createdBy"] as? String?
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
            announceList = ArrayList<ArrayList<String>>()
            announceList.add(ArrayList<String>())
            announceList[0].add(announce)
        }
    }

    /**
     * Hashes a byte array with SHA1 in ISO-8859-1 encoding
     */
    private fun sha1hash(input: ByteArray) : String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(input)
        return bytes.fold("", { str, it -> str + "%02x".format(it) })
    }

    fun getInfohash() : String = sha1hash(info)


}