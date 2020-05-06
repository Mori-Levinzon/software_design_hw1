package il.ac.technion.cs.softwaredesign

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.lang.StringBuilder
import java.security.MessageDigest
import kotlin.random.Random
import java.util.*
import kotlin.Comparator

class Utils: Comparator<String> {

    companion object{

        /**
         * Hashes a byte array with SHA1 in ISO-8859-1 encoding
         */
        fun sha1hash(input: ByteArray) : String {
            val bytes = MessageDigest.getInstance("SHA-1").digest(input)
            return bytes.fold("", { str, it -> str + "%02x".format(it) })
        }

        fun hexEncode(str:String):String {
            return URLEncoder.encode(str, StandardCharsets.UTF_8.toString())
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
         * If the response is compact, return string as-is. Otherwise, turn the non-compact list
         * into compact and then return
         */
        fun getPeersStringFromResponse(response: Map<String, Any>):String =TODO("Implement me! @Bahjat")

    }

    override fun compare(ip1: String?, ip2: String?): Int {
        return compareIPs(ip1,ip2)
    }
}