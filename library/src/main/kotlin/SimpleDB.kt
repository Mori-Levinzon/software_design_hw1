package il.ac.technion.cs.softwaredesign

import com.google.inject.Inject
import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import il.ac.technion.cs.softwaredesign.storage.SecureStorageFactory
import java.lang.IllegalStateException
import java.nio.charset.Charset

/**
 * A simple string-byteArray database implementation using the provided read/write methods.
 * Supports the basic CRUD methods: create, read, update, and delete
 * Supports multiple databases which are specific to the problem, and defined in the enum class Databases
 */
class SimpleDB @Inject constructor(private val storageFactory: SecureStorageFactory, private val charset: Charset = Charsets.UTF_8) {
    private var currStorage : SecureStorage = storageFactory.open("torrents".toByteArray(charset))

    /**
     * Sets the current active storage database to the one given
     */
    fun setCurrentStorage(type: Databases) : Unit = when(type) {
        Databases.TORRENTS -> currStorage = storageFactory.open("torrents".toByteArray(charset))
        Databases.PEERS -> currStorage = storageFactory.open("peers".toByteArray(charset))
        Databases.STATS -> currStorage = storageFactory.open("stats".toByteArray(charset))
    }
    /**
     * Creates a key-value pair in the active database
     * @throws IllegalStateException if the key already exists in the database
     */
    fun create(key: String, value: ByteArray) {
        val oldValueByteArray = currStorage.read(key.toByteArray(charset))
        if (oldValueByteArray != null && oldValueByteArray.size != 0) {
            throw IllegalStateException("Key already exists")
        }

        currStorage.write(key.toByteArray(charset), value)
    }

    /**
     * Reads a value from the active database that corresponds to the given key
     * @throws IllegalArgumentException if the key doesn't exist in the database
     * @returns the requested value
     */
    fun read(key: String) : ByteArray {
        val value = currStorage.read(key.toByteArray(charset))
        if(value == null || value.size == 0) {
            throw IllegalArgumentException("Key doesn't exist")
        }
        return value
    }

    /**
     * Updates a key-value pair in the active database
     * @throws IllegalArgumentException if the key doesn't exists in the database
     */
    fun update(key: String, value: ByteArray) {
        val oldValueByteArray = currStorage.read(key.toByteArray(charset))
        if (oldValueByteArray == null || oldValueByteArray.size == 0) {
            throw IllegalArgumentException("Key doesn't exist")
        }
        currStorage.write(key.toByteArray(charset), value)
    }


    /**
     * Deletes a key-value pair from the active database
     * @throws IllegalArgumentException if the key doesn't exists in the database
     */
    fun delete(key: String) {
        update(key, ByteArray(0))
    }
}