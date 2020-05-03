package il.ac.technion.cs.softwaredesign

import il.ac.technion.cs.softwaredesign.storage.read as storageRead
import il.ac.technion.cs.softwaredesign.storage.write as storageWrite
import java.lang.IllegalStateException
import java.nio.charset.Charset

/**
 * A simple string-byteArray database implementation using the provided read/write methods.
 * Supports the basic CRUD methods: create, read, update, and delete
 */
class SimpleDB(val charset : Charset = Charsets.UTF_8) {

    /**
     * Creates a key-value pair in the database
     * @throws IllegalStateException if the key already exists in the database
     */
    fun create(key: String, value: ByteArray) {
        val oldValueByteArray = storageRead(key.toByteArray(charset))
        if (oldValueByteArray != null && oldValueByteArray.size != 0) {
            throw IllegalStateException("Key already exists")
        }
        storageWrite(key.toByteArray(charset), value)
    }

    /**
     * Reads a value from the database that corresponds to the given key
     * @throws IllegalArgumentException if the key doesn't exist in the database
     * @returns the requested value
     */
    fun read(key: String) : ByteArray {
        val value = storageRead(key.toByteArray(charset))
        if(value == null || value.size == 0) {
            throw IllegalArgumentException("Key doesn't exist")
        }
        return value
    }

    /**
     * Updates a key-value pair in the database
     * @throws IllegalArgumentException if the key doesn't exists in the database
     */
    fun update(key: String, value: ByteArray) {
        val oldValueByteArray = storageRead(key.toByteArray(charset))
        if (oldValueByteArray == null || oldValueByteArray.size == 0) {
            throw IllegalArgumentException("Key doesn't exist")
        }
        storageWrite(key.toByteArray(charset), value)
    }


    /**
     * Deletes a key-value pair from the database
     * @throws IllegalArgumentException if the key doesn't exists in the database
     */
    fun delete(key: String) {
        update(key, ByteArray(0))
    }
}