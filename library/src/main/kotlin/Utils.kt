package il.ac.technion.cs.softwaredesign

import java.security.MessageDigest

class Utils {

    companion object{
        fun sha1hash(input: ByteArray) : String {
            val bytes = MessageDigest.getInstance("SHA-1").digest(input)
            return bytes.fold("", { str, it -> str + "%02x".format(it) })
        }

        fun hexEncode(str:String):String=TODO("Implement me!")

        fun getRandomChars(length: Int):String=TODO("Implement me!")

        fun compareIPs(ip1: String, ip2 : String):String=TODO("Implement me!")

    }
}