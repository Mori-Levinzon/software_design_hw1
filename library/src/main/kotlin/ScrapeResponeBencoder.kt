package il.ac.technion.cs.softwaredesign

import java.util.*
import kotlin.collections.LinkedHashMap
import kotlin.collections.set

class ScrapeResponeBencoder (response : String) {
    private var i : Int = 0

    private var infoBegin = 0
    private var infoEnd = 0

    private var piecesBegin = 0
    private var piecesEnd = 0

    private val responseData = response

    val responseDictionary = parseRespone() as LinkedHashMap<String, Any>


    private fun parseRespone(): Any? {
        if (i >= responseData.length ){
            throw IllegalArgumentException()
        }
        val type: Char = responseData[i].toChar()
        i++

/*
Byte Strings
Byte strings are encoded as follows: <string length encoded in base ten ASCII>:<string data>
Note that there is no constant beginning delimiter, and no ending delimiter.
*/
        if (type in '0'..'9') {
            var len = type.toInt() - 48
            val limit: Int = i + 11
            while (i <= limit) {
                val c: Char = responseData[i].toChar()
                if (c == ':') {

                    val out: String = responseData.substring(i + 1, i + len + 1)
                    i += len
                    return out
                }
                len = len * 10 + (c.toInt() - 48)
                ++i
            }
        }
/*
Integers
Integers are encoded as follows: i<integer encoded in base ten ASCII>e
The initial i and trailing e are beginning and ending delimiters.
*/
        else if (type == 'i') {
            var out = 0
            val start: Int = i
            val limit: Int = i + 22
            var neg = false
            while (i <= limit) {
                val c: Char = responseData[i].toChar()
                if (i == start && c == '-') {
                    neg = true
                    ++i
                    continue
                }
                if (c == 'e') return if (neg) -out else out
                out = out * 10 + (c.toInt() - 48)
                ++i
            }
        }

/*
Lists
Lists are encoded as follows: l<bencoded values>e
The initial l and trailing e are beginning and ending delimiters. Lists may contain any bencoded type, including integers, strings, dictionaries, and even lists within other lists.
*/
        else if (type == 'l') {
            val out = ArrayList<Any?>()
            while (true) {
                if (responseData[i].toChar() == 'e') return out
                out.add(parseRespone())
                ++i
            }
        }

/*
Dictionaries
Dictionaries are encoded as follows: d<bencoded string><bencoded element>e
The initial d and trailing e are the beginning and ending delimiters. Note that the keys must be bencoded strings. The values may be any bencoded type, including integers, strings, lists, and other dictionaries. Keys must be strings and appear in sorted order (sorted as raw strings, not alphanumerics). The strings should be compared using a binary comparison, not a culture-specific "natural" comparison.
*/
        else if (type == 'd') {

            val out = LinkedHashMap<Any?, Any?>()
            while (true) {
                if (responseData[i].toChar() == 'e') return out
                val key = parseRespone()
                ++i
                val value = parseRespone()
                out[key] = value
                ++i
            }
        }
        //non of the above is appropriate
        throw IllegalArgumentException()
    }

}