package il.ac.technion.cs.softwaredesign

import java.lang.StringBuilder
import java.security.MessageDigest
import kotlin.random.Random

class Utils {

    companion object{

        /**
         * Hashes a byte array with SHA1 in ISO-8859-1 encoding
         */
        fun sha1hash(input: ByteArray) : String {
            val bytes = MessageDigest.getInstance("SHA-1").digest(input)
            return bytes.fold("", { str, it -> str + "%02x".format(it) })
        }

        fun hexEncode(str:String):String=TODO("Implement me!")

        fun getRandomChars(length: Int):String {
            val allowedChars : List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
            return (1..length)
                    .map { i -> Random.nextInt(0, allowedChars.size) }
                    .map(allowedChars::get).joinToString("")
        }

        fun compareIPs(ip1: String, ip2 : String):String=TODO("Implement me!")

        /**
         * If the response is not compact, return string as-is. Otherwise, turn the compact string
         * into non-compact and then return
         */
        fun getPeersFromResponse(response: Map<String, Any>):List<Map<String, Any>> {
            assert(response.containsKey("peers"))
            if(response["peers"] is List<*>) {
                return response["peers"] as List<Map<String, Any>>
            }
            else {
                val peersByteArray = (response["peers"] as String).toByteArray()
                val peers = mutableListOf<Map<String, Any>>()
                var i = 0
                while(i < peersByteArray.size) {
                    peers.add(mapOf(
                            "peer id" to "",
                            "ip" to (peersByteArray[i].toString() + "." + peersByteArray[i+1].toString() + "."
                                    + peersByteArray[i+2].toString() + "." + peersByteArray[i+3].toString()),
                            "port" to (peersByteArray[i+4]* 256 + peersByteArray[i+5]).toString()
                    ))
                    i += 6
                }
                return peers
            }
        }

    }
}