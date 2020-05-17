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
For this assignment were we given the SecureStorageFactory which enables us to use guice and inject its constructor in order to create 3 different DB for out storage: torrents, peers and stats.
In library/src/main/kotlin/, We made a class called SimpleDB. SimpleDB is a simple persistent database that supports the basic CRUD operations on String-ByteArray key-value pairs. It uses the provided read/write methods for persistence.
We used the basic methods CRUD methods from the previous assignment( create(key, value), read(key), update(key, value), and delete(key)) and implemented CRUD functions for each of the DBs, thus allowing us to guarantee type safety. Basically, the DB-specific CRUD functions are wrappers for the general-purose CRUD functions, which ensure type safety for the types we want in our DBs.
Each of the DB's read, update, and delete methods throw an IllegalArgumentException when the supplied key does not exist in the database.
Each of the DB's create methods throws an IllegalStateException when the supplied key already exists in the database.

###### Ben
This is a Bencoding library which is based on an implementation that we did not write. Original source code is here: https://gist.github.com/omarmiatello/b09d4ba995c8c0881b0921c0e7aa9bdc
we modified it such that:
1. The "pieces" property of the info dictionary would be parsed as a raw byte array and held in a map data structure.
2. The "info" property of the torrent metainfo file would hold a dictionary, that includes "pieces" as a raw byte array
3. The "peers" property received from the http get request, in case it was a string, would be parsed as a raw byte array rather than a string.
4. We added a byte array encoder to this library in order to support encoding raw byte arrays using bencoding. We mainly used it for testing purposes, but may use it in the future if we intend on storing byte arrays.

###### TorrentFile
This class represents a torrent file. The class is stateless and immutable. It is initialized using a torrent byte array, which is parsed into the TorrentFile object.
Its public only property is announce List which is immutable, and it consists of announce list a proper torrent file has.
The public methods are getInfohash() and shuffleAnnounceList() which are self-explanatory, and announceTracker() and scrapeTrackers() which are only used by the announce and scrape call respectively. The latter two methods basically exist here because they operate directly on the announce list, and it made better sense they would be here rather than in CourseTorrent. We realize this increases coupling between the two classes, but we think it's okay because no other class should depend on TorrentFile.
The TorrentFile API will continue to be expanded and modified in future assignments.

###### Fuel
In our implementations of announce and scrape methods we used the external library fuel (https://github.com/kittinunf/fuel.git)
in order to facilitate the way we send http GET requests and receive response from the server.
It is included in the gradle files as a dependency of ours, and we mocked it in the tests.

### Testing Summary
The following components were thoroughly tested:
* **CourseTorrent**: tested in CourseTorrentHW0Test (methods implemented in the assigment #0) and CourseTorrentHW1Test (methods implemented in the assigment #1) . All the methods were tested and every important scenerio has been checked as well (with conceren to time limitations).
* **TorrentFile**: all the methods added in this assigment were created as part of a refactoring process of CourseTorrent class and are called only once from the courseTorrent method,
therefore the composed tests for this assignment (CourseTorrentHW1Test) covers their functionality as well.
* **Utils**: tested in UtilsTest.
* **Ben**: tested in BenTest.

In total the tests span nearly 100% code coverage across all the classes we've implemented.

The tests make use of the `initialize CourseTorrent with mocked DB`, `mockHttp` and `mockHttpStringStartsWith` functions in order to mock the missing behavior of the remote servers and to simulate the behavior of the http requests.
Guice is used to provide the constructor parameter for CourseTorrent and bind the DB to the implementations.

### Difficulties
Testing the methods announce and scrape calls posed a difficulty because we had to face the unstability of the responses we got after sending http requests (to actual trackers), and also because mocking the server was a bit challenging.
The BitTorrent spec itself has a lot of details and we sometimes got lost in the middle of all of them.

### Feedback
Most of the time spent on understanding on how to implement and handle BitTorrent http requests and properly test them since we've got unstable results, rather than engaging us in one of the main purposes of this assigment (Guice).