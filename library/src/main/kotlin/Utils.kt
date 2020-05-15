package il.ac.technion.cs.softwaredesign

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.lang.StringBuilder
import java.security.MessageDigest
import kotlin.random.Random
import java.util.*
import kotlin.Comparator

class Utils {

    companion object{

        /**
         * Hashes a byte array with SHA1 in ISO-8859-1 encoding
         */
        fun sha1hash(input: ByteArray) : String {
            val bytes = MessageDigest.getInstance("SHA-1").digest(input)
            return bytes.fold("", { str, it -> str + "%02x".format(it) })
        }

        fun hexEncode(str:String):String {
            val allowedChars : List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9') + ('.') + ('-') + ('_') + ('~')
            val strHexByteArray = UByteArray(str.length / 2)
            for(i in str.indices step 2) {
                val byte = str.substring(i, i+2)
                strHexByteArray[i/2] = byte.toUByte(radix = 16)
            }
            val myHex = strHexByteArray.fold("", {str, it -> str + (when {
                it.toByte().toChar() in allowedChars -> it.toByte().toChar().toString()
                else -> "%%%02x".format(it.toByte()).toLowerCase()
            })})
            return myHex
        }

        fun getRandomChars(length: Int):String {
            val allowedChars : List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
            return (1..length)
                    .map { i -> Random.nextInt(0, allowedChars.size) }
                    .map(allowedChars::get).joinToString("")
        }

        fun compareIPs(ip1: String?, ip2: String?):Int {
            try {
                if (ip1 == null || ip1.toString().isEmpty()) return -1
                if (ip2 == null || ip2.toString().isEmpty()) return 1
                val ba1: List<String> = ip1.split(".")
                val ba2: List<String> = ip2.split(".")
                for (i in ba1.indices) {
                    val b1 = ba1[i].toInt()
                    val b2 = ba2[i].toInt()
                    if (b1 == b2) continue
                    return if (b1 < b2) -1 else 1
                }
                return 0
            } catch (ex: Exception) {
                return 0
            }
        }

        /**
         * If the response is not compact, return string as-is. Otherwise, turn the compact string
         * into non-compact and then return
         */
        fun getPeersFromResponse(response: Map<String, Any>):List<Map<String, String>> {
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

        fun String.withParams(params: List<Pair<String, String>>) : String {
            var toReturn = this + "?"
            for((key, value) in params.dropLast(1)) {
                toReturn = toReturn + key + "=" + value + "&"
            }
            toReturn = toReturn + params.last().first + "=" + params.last().second
            return toReturn
        }
    }
}