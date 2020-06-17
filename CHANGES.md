We made 2 changes:
1. we used 3 different DB in for our storage : torrents , peers and stats and each of them has their related CRUD  functions.
    The tests failed because instead of deleting the stats or peers from their statsStorage or peersStorage respectively in their delete function,
    we deleted the relevant data from the torrentStorage after it was already been deleted from there, which caused illegal argument exception to be throwed. 
We kindly request that you will consider our mistakes as minor and won't penal us with points reduction since it was caused by a typo mistake and not any kind of logic or structure mistake.

Overall:
2 lines changed overall ( changed in SimpleDB.kt)