package com.example.online

import android.content.Context
import com.example.R
import com.example.engine.GameModeId
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
                val guestAvatar = guestStatsToMerge?.avatarId.orEmpty().trim().take(64)
                val finalAvatarId = existingUser?.avatarId?.takeIf { it.isNotBlank() } ?: guestAvatar
                val baseWins = existingUser?.wins ?: 0L
                val baseGames = existingUser?.gamesPlayed ?: 0L
                val sameWeek = existingUser?.weekKey == currentWeek
                val baseWeeklyWins = if (sameWeek) (existingUser?.weeklyWins ?: 0L) else 0L
                val baseWeeklyGames = if (sameWeek) (existingUser?.weeklyGamesPlayed ?: 0L) else 0L

                val newWins = baseWins + guestWins.toLong()
                val newGames = maxOf(baseGames + guestGames.toLong(), newWins)
                val newWeeklyWins = baseWeeklyWins + guestWeeklyWins.toLong()
                val newWeeklyGames = maxOf(baseWeeklyGames + guestWeeklyGames.toLong(), newWeeklyWins)

                val shouldSyncAvatar = finalAvatarId.isNotBlank() && existingUser?.avatarId != finalAvatarId

                if (!userSnap.exists()) {
                    tx.set(
                        userRef,
                        mapOf(
                            "userId" to uid,
                            "nickname" to finalNickname,
                            "avatarId" to finalAvatarId,
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
                } else if (guestGames > 0 || !sameWeek || existingUser?.googleLinked != true || shouldSyncAvatar) {
                    tx.update(
                        userRef,
                        mapOf(
                            "nickname" to finalNickname,
                            "avatarId" to finalAvatarId,
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

                val shouldSyncLbAvatar = finalAvatarId.isNotBlank() && existingLb?.avatarId != finalAvatarId
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
                            "avatarId" to finalAvatarId,
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
                } else if (guestGames > 0 || !sameWeek || shouldSyncLbAvatar) {
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
                            "avatarId" to finalAvatarId,
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
                    avatarId = finalAvatarId,
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

    suspend fun updateAvatar(uid: String, avatarId: String): Result<Unit> {
        val clean = avatarId.trim().take(64)
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
                            "avatarId" to clean,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                if (lbSnap.exists()) {
                    tx.update(
                        lbRef,
                        mapOf(
                            "avatarId" to clean,
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
     * Records a completed online match result (+1 game, optional +1 win, and per-mode win count)
     * in both /users/{uid} and /leaderboard/{uid} atomically.
     */
    suspend fun recordOnlineMatchResult(
        uid: String,
        fallbackNickname: String,
        won: Boolean,
        mode: GameModeId = GameModeId.CLASSIC
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
                val avatarId = existingUser?.avatarId.orEmpty().trim().take(64)
                val winDelta = if (won) 1L else 0L

                val nextWins = (existingUser?.wins ?: 0L) + winDelta
                val nextGames = maxOf((existingUser?.gamesPlayed ?: 0L) + 1L, nextWins)
                val sameWeek = existingUser?.weekKey == currentWeek
                val nextWeeklyWins = (if (sameWeek) (existingUser?.weeklyWins ?: 0L) else 0L) + winDelta
                val nextWeeklyGames = maxOf(
                    (if (sameWeek) (existingUser?.weeklyGamesPlayed ?: 0L) else 0L) + 1L,
                    nextWeeklyWins
                )

                val nextUserModeWins = existingUser?.modeWins.orEmpty().toMutableMap().apply {
                    if (won) {
                        this[mode.name] = (this[mode.name] ?: 0L) + 1L
                    }
                }

                if (!userSnap.exists()) {
                    tx.set(
                        userRef,
                        mapOf(
                            "userId" to uid,
                            "nickname" to nickname,
                            "avatarId" to avatarId,
                            "googleLinked" to true,
                            "wins" to nextWins,
                            "gamesPlayed" to nextGames,
                            "weeklyWins" to nextWeeklyWins,
                            "weeklyGamesPlayed" to nextWeeklyGames,
                            "weekKey" to currentWeek,
                            "modeWins" to nextUserModeWins,
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
                            "modeWins" to nextUserModeWins,
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
                            "avatarId" to avatarId,
                            "googleLinked" to true,
                            "wins" to nextWins.coerceAtMost(50L),
                            "gamesPlayed" to nextGames.coerceAtMost(50L),
                            "weeklyWins" to nextWeeklyWins.coerceAtMost(50L),
                            "weeklyGamesPlayed" to nextWeeklyGames.coerceAtMost(50L),
                            "weekKey" to currentWeek,
                            "modeWins" to nextUserModeWins,
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
                    val nextLbModeWins = existingLb?.modeWins.orEmpty().toMutableMap().apply {
                        if (won) {
                            this[mode.name] = (this[mode.name] ?: 0L) + 1L
                        }
                    }
                    tx.update(
                        lbRef,
                        mapOf(
                            "nickname" to nickname,
                            "avatarId" to avatarId,
                            "wins" to lbWins,
                            "gamesPlayed" to lbGames,
                            "weeklyWins" to lbWeeklyWins,
                            "weeklyGamesPlayed" to lbWeeklyGames,
                            "weekKey" to currentWeek,
                            "modeWins" to nextLbModeWins,
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
    fun observeLeaderboard(
        myUid: String,
        weekly: Boolean,
        modeFilter: GameModeId? = null
    ): Flow<LeaderboardSnapshot> = callbackFlow {
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
                    val wins = when {
                        modeFilter != null -> (entry.modeWins[modeFilter.name] ?: 0L).toInt()
                        weekly -> if (sameWeek) entry.weeklyWins.toInt() else 0
                        else -> entry.wins.toInt()
                    }
                    val games = when {
                        modeFilter != null -> maxOf(entry.gamesPlayed.toInt(), wins)
                        weekly -> if (sameWeek) entry.weeklyGamesPlayed.toInt() else 0
                        else -> entry.gamesPlayed.toInt()
                    }
                    val winRate = if (games > 0) ((wins * 100.0) / games).toInt().coerceIn(0, 100) else 0
                    Triple(entry, wins to games, winRate)
                }
                .filter { (entry, stats, _) ->
                    when {
                        modeFilter != null -> stats.first > 0 || entry.userId == myUid
                        weekly -> stats.second > 0 || entry.userId == myUid
                        else -> true
                    }
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

    private fun parseModeWins(raw: Any?): Map<String, Long> {
        val map = raw as? Map<*, *> ?: return emptyMap()
        val normalized = mutableMapOf<String, Long>()
        for ((k, v) in map) {
            val keyStr = k as? String ?: continue
            val count = (v as? Number)?.toLong()?.coerceAtLeast(0L) ?: continue
            val migratedKey = GameModeId.fromLegacyName(keyStr).first.name
            normalized[migratedKey] = (normalized[migratedKey] ?: 0L) + count
        }
        return normalized
    }

    private fun parseUserProfile(doc: DocumentSnapshot): UserProfileDoc? {
        if (!doc.exists()) return null
        val est = DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        return UserProfileDoc(
            docId = doc.id,
            userId = doc.getString("userId") ?: doc.id,
            nickname = doc.getString("nickname") ?: "Player",
            avatarId = doc.getString("avatarId").orEmpty(),
            googleLinked = doc.getBoolean("googleLinked") ?: true,
            wins = doc.getLong("wins") ?: 0L,
            gamesPlayed = doc.getLong("gamesPlayed") ?: 0L,
            weeklyWins = doc.getLong("weeklyWins") ?: 0L,
            weeklyGamesPlayed = doc.getLong("weeklyGamesPlayed") ?: 0L,
            weekKey = doc.getString("weekKey") ?: WeekKeyUtil.currentWeekKey(),
            modeWins = parseModeWins(doc.get("modeWins")),
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
            avatarId = doc.getString("avatarId").orEmpty(),
            googleLinked = doc.getBoolean("googleLinked") ?: true,
            wins = doc.getLong("wins") ?: 0L,
            gamesPlayed = doc.getLong("gamesPlayed") ?: 0L,
            weeklyWins = doc.getLong("weeklyWins") ?: 0L,
            weeklyGamesPlayed = doc.getLong("weeklyGamesPlayed") ?: 0L,
            weekKey = doc.getString("weekKey") ?: WeekKeyUtil.currentWeekKey(),
            modeWins = parseModeWins(doc.get("modeWins")),
            createdAt = doc.getTimestamp("createdAt", est) ?: Timestamp.now(),
            updatedAt = doc.getTimestamp("updatedAt", est) ?: Timestamp.now()
        )
    }
}
