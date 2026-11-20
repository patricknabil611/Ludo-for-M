package com.example.online

import android.content.Context
import com.example.R
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore repository for authenticated user profiles (/users/{userId}) and
 * Google-only global leaderboard entries (/leaderboard/{userId}).
 */
class OnlineProfileAndLeaderboardRepository(
    private val firestore: FirebaseFirestore
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val usersCol get() = firestore.collection("users")
    private val leaderboardCol get() = firestore.collection("leaderboard")

    fun observeMyProfile(uid: String): Flow<UserProfileDoc?> = callbackFlow {
        val path = "users/$uid"
        val registration = usersCol.document(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.GET, path)
                close(error)
                return@addSnapshotListener
            }
            val doc = snapshot?.let(::parseUserProfile)
            trySend(doc)
        }
        awaitClose { registration.remove() }
    }

    /**
     * Ensures /users/{uid} and /leaderboard/{uid} exist after Google Sign-In,
     * and atomically merges any accumulated Guest stats without data loss.
     */
    suspend fun ensureProfileAndMergeGuestStats(
        uid: String,
        defaultDisplayName: String,
        guestStatsToMerge: GuestProfile?
    ): Result<UserProfileDoc> {
        val userPath = "users/$uid"
        val lbPath = "leaderboard/$uid"
        val currentWeek = WeekKeyUtil.currentWeekKey()
        val guestGames = (guestStatsToMerge?.gamesPlayed ?: 0).coerceIn(0, 50)
        val guestWins = (guestStatsToMerge?.wins ?: 0).coerceIn(0, guestGames)
        val guestSameWeek = guestStatsToMerge?.weekKey == currentWeek
        val guestWeeklyGames = if (guestSameWeek) (guestStatsToMerge?.weeklyGamesPlayed ?: 0).coerceIn(0, guestGames) else 0
        val guestWeeklyWins = if (guestSameWeek) (guestStatsToMerge?.weeklyWins ?: 0).coerceIn(0, guestWeeklyGames) else 0

        val preferredNickname = when {
            !guestStatsToMerge?.nickname.isNullOrBlank() -> guestStatsToMerge!!.nickname
            defaultDisplayName.isNotBlank() -> defaultDisplayName
            else -> "Player"
        }.trim().take(24).ifBlank { "Player" }

        return try {
            val resultDoc = firestore.runTransaction { tx ->
                val userRef = usersCol.document(uid)
                val lbRef = leaderboardCol.document(uid)
                val userSnap = tx.get(userRef)
                val lbSnap = tx.get(lbRef)

                val existingUser = if (userSnap.exists()) parseUserProfile(userSnap) else null
                val existingLb = if (lbSnap.exists()) parseLeaderboardEntry(lbSnap) else null

                val finalNickname = existingUser?.nickname?.takeIf { it.isNotBlank() } ?: preferredNickname
                val baseWins = existingUser?.wins ?: 0L
                val baseGames = existingUser?.gamesPlayed ?: 0L
                val sameWeek = existingUser?.weekKey == currentWeek
                val baseWeeklyWins = if (sameWeek) (existingUser?.weeklyWins ?: 0L) else 0L
                val baseWeeklyGames = if (sameWeek) (existingUser?.weeklyGamesPlayed ?: 0L) else 0L

                val newWins = baseWins + guestWins.toLong()
                val newGames = maxOf(baseGames + guestGames.toLong(), newWins)
                val newWeeklyWins = baseWeeklyWins + guestWeeklyWins.toLong()
                val newWeeklyGames = maxOf(baseWeeklyGames + guestWeeklyGames.toLong(), newWeeklyWins)

                if (!userSnap.exists()) {
                    tx.set(
                        userRef,
                        mapOf(
                            "userId" to uid,
                            "nickname" to finalNickname,
                            "googleLinked" to true,
                            "wins" to newWins,
                            "gamesPlayed" to newGames,
                            "weeklyWins" to newWeeklyWins,
                            "weeklyGamesPlayed" to newWeeklyGames,
                            "weekKey" to currentWeek,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else if (guestGames > 0 || !sameWeek || existingUser?.googleLinked != true) {
                    tx.update(
                        userRef,
                        mapOf(
                            "nickname" to finalNickname,
                            "googleLinked" to true,
                            "wins" to newWins,
                            "gamesPlayed" to newGames,
                            "weeklyWins" to newWeeklyWins,
                            "weeklyGamesPlayed" to newWeeklyGames,
                            "weekKey" to currentWeek,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                if (!lbSnap.exists()) {
                    val lbCreateGames = newGames.coerceAtMost(50L)
                    val lbCreateWins = newWins.coerceAtMost(lbCreateGames)
                    val lbCreateWeeklyGames = newWeeklyGames.coerceAtMost(lbCreateGames)
                    val lbCreateWeeklyWins = newWeeklyWins.coerceAtMost(lbCreateWeeklyGames)
                    tx.set(
                        lbRef,
                        mapOf(
                            "userId" to uid,
                            "nickname" to finalNickname,
                            "googleLinked" to true,
                            "wins" to lbCreateWins,
                            "gamesPlayed" to lbCreateGames,
                            "weeklyWins" to lbCreateWeeklyWins,
                            "weeklyGamesPlayed" to lbCreateWeeklyGames,
                            "weekKey" to currentWeek,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else if (guestGames > 0 || !sameWeek) {
                    val prevLbWins = existingLb?.wins ?: 0L
                    val prevLbGames = existingLb?.gamesPlayed ?: 0L
                    val lbSameWeek = existingLb?.weekKey == currentWeek
                    val prevLbWeeklyWins = if (lbSameWeek) (existingLb?.weeklyWins ?: 0L) else 0L
                    val prevLbWeeklyGames = if (lbSameWeek) (existingLb?.weeklyGamesPlayed ?: 0L) else 0L

                    val nextLbWins = prevLbWins + guestWins.toLong()
                    val nextLbGames = maxOf(prevLbGames + guestGames.toLong(), nextLbWins)
                    val nextLbWeeklyWins = prevLbWeeklyWins + guestWeeklyWins.toLong()
                    val nextLbWeeklyGames = maxOf(prevLbWeeklyGames + guestWeeklyGames.toLong(), nextLbWeeklyWins)

                    tx.update(
                        lbRef,
                        mapOf(
                            "nickname" to finalNickname,
                            "wins" to nextLbWins,
                            "gamesPlayed" to nextLbGames,
                            "weeklyWins" to nextLbWeeklyWins,
                            "weeklyGamesPlayed" to nextLbWeeklyGames,
                            "weekKey" to currentWeek,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                UserProfileDoc(
                    docId = uid,
                    userId = uid,
                    nickname = finalNickname,
                    googleLinked = true,
                    wins = newWins,
                    gamesPlayed = newGames,
                    weeklyWins = newWeeklyWins,
                    weeklyGamesPlayed = newWeeklyGames,
                    weekKey = currentWeek
                )
            }.await()

            Result.success(resultDoc)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "$userPath | $lbPath")
            Result.failure(e)
        }
    }

    suspend fun updateNickname(uid: String, newNickname: String): Result<Unit> {
        val clean = newNickname.trim().take(24)
        if (clean.isEmpty()) return Result.failure(IllegalArgumentException("Nickname cannot be empty"))
        return try {
            firestore.runTransaction { tx ->
                val userRef = usersCol.document(uid)
                val lbRef = leaderboardCol.document(uid)
                val userSnap = tx.get(userRef)
                val lbSnap = tx.get(lbRef)
                if (userSnap.exists()) {
                    tx.update(
                        userRef,
                        mapOf(
                            "nickname" to clean,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                if (lbSnap.exists()) {
                    tx.update(
                        lbRef,
                        mapOf(
                            "nickname" to clean,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid")
            Result.failure(e)
        }
    }

    /**
     * Records a completed online match result (+1 game, optional +1 win) in both
     * /users/{uid} and /leaderboard/{uid} atomically.
     */
    suspend fun recordOnlineMatchResult(
        uid: String,
        fallbackNickname: String,
        won: Boolean
    ): Result<Unit> {
        val currentWeek = WeekKeyUtil.currentWeekKey()
        val cleanFallback = fallbackNickname.trim().take(24).ifBlank { "Player" }
        return try {
            firestore.runTransaction { tx ->
                val userRef = usersCol.document(uid)
                val lbRef = leaderboardCol.document(uid)
                val userSnap = tx.get(userRef)
                val lbSnap = tx.get(lbRef)

                val existingUser = if (userSnap.exists()) parseUserProfile(userSnap) else null
                val nickname = existingUser?.nickname?.takeIf { it.isNotBlank() } ?: cleanFallback
                val winDelta = if (won) 1L else 0L

                val nextWins = (existingUser?.wins ?: 0L) + winDelta
                val nextGames = maxOf((existingUser?.gamesPlayed ?: 0L) + 1L, nextWins)
                val sameWeek = existingUser?.weekKey == currentWeek
                val nextWeeklyWins = (if (sameWeek) (existingUser?.weeklyWins ?: 0L) else 0L) + winDelta
                val nextWeeklyGames = maxOf(
                    (if (sameWeek) (existingUser?.weeklyGamesPlayed ?: 0L) else 0L) + 1L,
                    nextWeeklyWins
                )

                if (!userSnap.exists()) {
                    tx.set(
                        userRef,
                        mapOf(
                            "userId" to uid,
                            "nickname" to nickname,
                            "googleLinked" to true,
                            "wins" to nextWins,
                            "gamesPlayed" to nextGames,
                            "weeklyWins" to nextWeeklyWins,
                            "weeklyGamesPlayed" to nextWeeklyGames,
                            "weekKey" to currentWeek,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    tx.update(
                        userRef,
                        mapOf(
                            "wins" to nextWins,
                            "gamesPlayed" to nextGames,
                            "weeklyWins" to nextWeeklyWins,
                            "weeklyGamesPlayed" to nextWeeklyGames,
                            "weekKey" to currentWeek,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                if (!lbSnap.exists()) {
                    tx.set(
                        lbRef,
                        mapOf(
                            "userId" to uid,
                            "nickname" to nickname,
                            "googleLinked" to true,
                            "wins" to nextWins.coerceAtMost(50L),
                            "gamesPlayed" to nextGames.coerceAtMost(50L),
                            "weeklyWins" to nextWeeklyWins.coerceAtMost(50L),
                            "weeklyGamesPlayed" to nextWeeklyGames.coerceAtMost(50L),
                            "weekKey" to currentWeek,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    val existingLb = parseLeaderboardEntry(lbSnap)
                    val lbWins = (existingLb?.wins ?: 0L) + winDelta
                    val lbGames = maxOf((existingLb?.gamesPlayed ?: 0L) + 1L, lbWins)
                    val lbSameWeek = existingLb?.weekKey == currentWeek
                    val lbWeeklyWins = (if (lbSameWeek) (existingLb?.weeklyWins ?: 0L) else 0L) + winDelta
                    val lbWeeklyGames = maxOf(
                        (if (lbSameWeek) (existingLb?.weeklyGamesPlayed ?: 0L) else 0L) + 1L,
                        lbWeeklyWins
                    )
                    tx.update(
                        lbRef,
                        mapOf(
                            "nickname" to nickname,
                            "wins" to lbWins,
                            "gamesPlayed" to lbGames,
                            "weeklyWins" to lbWeeklyWins,
                            "weeklyGamesPlayed" to lbWeeklyGames,
                            "weekKey" to currentWeek,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "leaderboard/$uid")
            Result.failure(e)
        }
    }

    /**
     * Observes the global Google-only leaderboard.
     * Queries with `.whereEqualTo("googleLinked", true)` to align strictly with `allow list` rules
     * and computes top 100 + the signed-in user's exact rank even if outside the top 100.
     */
    fun observeLeaderboard(myUid: String, weekly: Boolean): Flow<LeaderboardSnapshot> = callbackFlow {
        val currentWeek = WeekKeyUtil.currentWeekKey()
        val query = leaderboardCol.whereEqualTo("googleLinked", true)
        val registration = query.addSnapshotListener { snapshots, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, "leaderboard")
                close(error)
                return@addSnapshotListener
            }

            val allEntries = snapshots?.documents.orEmpty().mapNotNull(::parseLeaderboardEntry)

            val rankedAll: List<RankedLeaderboardRow> = allEntries
                .map { entry ->
                    val sameWeek = entry.weekKey == currentWeek
                    val wins = if (weekly) {
                        if (sameWeek) entry.weeklyWins.toInt() else 0
                    } else {
                        entry.wins.toInt()
                    }
                    val games = if (weekly) {
                        if (sameWeek) entry.weeklyGamesPlayed.toInt() else 0
                    } else {
                        entry.gamesPlayed.toInt()
                    }
                    val winRate = if (games > 0) ((wins * 100.0) / games).toInt().coerceIn(0, 100) else 0
                    Triple(entry, wins to games, winRate)
                }
                .filter { (entry, stats, _) ->
                    // Show players who have played in the selected timeframe, or always include self
                    !weekly || stats.second > 0 || entry.userId == myUid
                }
                .sortedWith(
                    compareByDescending<Triple<LeaderboardEntryDoc, Pair<Int, Int>, Int>> { it.second.first }
                        .thenByDescending { it.third }
                        .thenByDescending { it.second.second }
                        .thenBy { it.first.nickname.lowercase() }
                )
                .mapIndexed { index, (entry, stats, winRate) ->
                    RankedLeaderboardRow(
                        rank = index + 1,
                        entry = entry,
                        wins = stats.first,
                        gamesPlayed = stats.second,
                        winRatePercent = winRate
                    )
                }

            val myRow = rankedAll.firstOrNull { it.entry.userId == myUid }
            trySend(
                LeaderboardSnapshot(
                    topEntries = rankedAll.take(100),
                    myRow = myRow,
                    totalPlayers = rankedAll.size
                )
            )
        }
        awaitClose { registration.remove() }
    }

    private fun parseUserProfile(doc: DocumentSnapshot): UserProfileDoc? {
        if (!doc.exists()) return null
        val est = DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        return UserProfileDoc(
            docId = doc.id,
            userId = doc.getString("userId") ?: doc.id,
            nickname = doc.getString("nickname") ?: "Player",
            googleLinked = doc.getBoolean("googleLinked") ?: true,
            wins = doc.getLong("wins") ?: 0L,
            gamesPlayed = doc.getLong("gamesPlayed") ?: 0L,
            weeklyWins = doc.getLong("weeklyWins") ?: 0L,
            weeklyGamesPlayed = doc.getLong("weeklyGamesPlayed") ?: 0L,
            weekKey = doc.getString("weekKey") ?: WeekKeyUtil.currentWeekKey(),
            createdAt = doc.getTimestamp("createdAt", est) ?: Timestamp.now(),
            updatedAt = doc.getTimestamp("updatedAt", est) ?: Timestamp.now()
        )
    }

    private fun parseLeaderboardEntry(doc: DocumentSnapshot): LeaderboardEntryDoc? {
        if (!doc.exists()) return null
        val est = DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        return LeaderboardEntryDoc(
            docId = doc.id,
            userId = doc.getString("userId") ?: doc.id,
            nickname = doc.getString("nickname") ?: "Player",
            googleLinked = doc.getBoolean("googleLinked") ?: true,
            wins = doc.getLong("wins") ?: 0L,
            gamesPlayed = doc.getLong("gamesPlayed") ?: 0L,
            weeklyWins = doc.getLong("weeklyWins") ?: 0L,
            weeklyGamesPlayed = doc.getLong("weeklyGamesPlayed") ?: 0L,
            weekKey = doc.getString("weekKey") ?: WeekKeyUtil.currentWeekKey(),
            createdAt = doc.getTimestamp("createdAt", est) ?: Timestamp.now(),
            updatedAt = doc.getTimestamp("updatedAt", est) ?: Timestamp.now()
        )
    }
}
