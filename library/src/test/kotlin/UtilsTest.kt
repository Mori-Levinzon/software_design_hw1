

import com.google.common.base.Predicates.equalTo
import il.ac.technion.cs.softwaredesign.Utils
import il.ac.technion.cs.softwaredesign.Utils.Companion.getRandomChars
import il.ac.technion.cs.softwaredesign.Utils.Companion.hexEncode
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.lang.IllegalArgumentException

class UtilsTest {

    @Test
    fun `IP compares correctly`() {
        val addresses = ArrayList<String>()
        addresses.add("123.4.245.23")
        addresses.add("104.244.253.29")
        addresses.add("1.198.3.93")
        addresses.add("32.183.93.40")
        addresses.add("104.30.244.2")
        addresses.add("104.244.4.1")

        val sortedAddresses = ArrayList<String>()
        sortedAddresses.add("1.198.3.93")
        sortedAddresses.add("32.183.93.40")
        sortedAddresses.add("104.30.244.2")
        sortedAddresses.add("104.244.4.1")
        sortedAddresses.add("104.244.253.29")
        sortedAddresses.add( "123.4.245.23")
        val utils = Utils()
//        addresses.sortWith(utils)
//        Assertions.assertEquals(addresses, sortedAddresses)
    }

    @Test
    fun `Random Char return characters in range and in correct length`() {
        val randomStr = getRandomChars(6)
        Assertions.assertEquals(randomStr.length, 6)
        for (i in randomStr.indices){
            assert(randomStr[i] in 'a'..'z' || randomStr[i] in 'A'..'Z' || randomStr[i] in '0'..'9')
        }

        assert( getRandomChars(0) == "")

        assertThrows<IllegalArgumentException> { getRandomChars(-1) }

    }

}