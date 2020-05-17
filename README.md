#TODO review it
# CourseTorrent: Assignment 1

## Authors
* Bahjat Kawar, 206989105
* Mori Levinzon, 308328467

## Notes

### Implementation Summary
#### The CourseTorrent Application

In coursetorrent-app/src/main/kotlin, we put the class **CourseTorrent**

**CourseTorrent** is the main application class. The API was specified by the TA. We added a database property so that a different CRUD key-value database implementation may be used (for example, the mocked in-memory DB in testing), but of course specifying it on initialization is optional.
Its methods are load(torrent), unload(infohash), announces(infohash), announces(infohash,TorrentEvent,uploaded,downloaded,left), scrape(infohash) , knownPeers(infohash) invalidatePeer(infohash,peer) and trackerStats(infohash) they are all well-documented in the code (Javadoc).

##### The External Library
###### SimpleDB
For this assignment were we given the SecureStorageFactory which enables us to use guice and inject its constructor in order to create 3 different DB for out storage: torrents, peers and stats
In library/src/main/kotlin/, We made a class called SimpleDB. SimpleDB is a simple general-purpose persistent database that supports the basic CRUD operations on String-ByteArray key-value pairs. It uses the provided read/write methods for persistence.
We used the basic methods CRUD methods from the previous assignment( create(key, value), read(key), update(key, value), and delete(key)) and implemented crud functions for each of the DB, thus allowing us to guarantee type safety.
each of the DB's read, update, and delete methods throw an IllegalArgumentException when the supplied key does not exist in the database.
each of the DB's create methods throws an IllegalStateException when the supplied key already exists in the database.

###### Ben
This is a Bencoding library which is based on an implementation that we did not write. Original source code is here: https://gist.github.com/omarmiatello/b09d4ba995c8c0881b0921c0e7aa9bdc
we modified it such that:
1. The "pieces" property of the info dictionary would be parsed as a raw byte array and held in a map data structure.
2. The "info" property of the torrent metainfo file would hold a dictionary, that includes "pieces" as a raw byte array
3. The "peers" property received form the http get request would hold a dictionary, that includes "peers" as a raw byte array
4. We added another encoder to this library in order to encode the responses we received from the http requests.

###### TorrentFile
This class represents a torrent file. The class is stateless and immutable. It is initialized using a torrent byte array, which is parsed into the TorrentFile object.
Its public only property is announce List which is immutable, and it consists of announce list a proper torrent file has.
The public method are getInfohash() and shuffleAnnounceList() which are self-explanatory, and announceTracker and scrapeTrackers which are only used by the announce and scrape call respectively 
The TorrentFile API will continue to be expanded and modified in future assignments.

###### Fuel
In our implementations of announce and scrape methods we used the external library fuel (https://github.com/kittinunf/fuel.git)
in order to facilitate the way we send http GET requests and receive response from the server.

### Testing Summary
The following components were thoroughly tested:
* **CourseTorrent**: tested in CourseTorrentHW0Test (methods implemented in the assigment #0) and CourseTorrentHW1Test (methods implemented in the assigment #1) . All the methods were tested and every possible scenerios were been checked as well (With conceren about time limitations).
* **TorrentFile**: all the method added in this assigment were created as part of a refactoring process of CourseTorrent class and are called only once from the courseTorrent method,
therefore the composed tests for this assignment ( CourseTorrentHW1Test) covers their functionality as well.
* **Utils**: tested in UtilsTest.
* **Ben**: tested in BenTest.

In total the tests span nearly 100% code coverage across all the classes we've implemented.

The tests use the `initialize CourseTorrent with mocked DB`, `mockHttp` and `mockHttpStringStartsWith` function to mock the missing behavior of the remote storage and to simulate the behavior of the http requests.
Guice is used to provide the constructor parameter for CourseTorrent and bind the DB to the implementations.

### Difficulties
Testing the methods announce and scrape calls pose a difficulty since we had to face the instability of the responses we got after sending http requests.

### Feedback
Most of the time spent on understanding on how to implement and handle http requests and properly test them since we've got unstable results, rather than engaging us in the purpose of this assigment(Guice).