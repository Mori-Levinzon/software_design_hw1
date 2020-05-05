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
         * If the response is compact, return string as-is. Otherwise, turn the non-compact list
         * into compact and then return
         */
        fun getPeersStringFromResponse(response: Map<String, Any>):String =TODO("Implement me! @Bahjat")

    }
}