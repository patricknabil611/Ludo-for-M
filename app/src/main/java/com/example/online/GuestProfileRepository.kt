package com.example.online

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.guestDataStore: DataStore<Preferences> by preferencesDataStore(name = "ludo_guest_profile")

/**
 * Local DataStore repository for Guest nickname and local match stats.
 * Supports seamless, zero-loss stat migration when a guest upgrades to a Google Account.
 */
class GuestProfileRepository(private val context: Context) {

    private object Keys {
        val NICKNAME = stringPreferencesKey("guest_nickname")
        val AVATAR_ID = stringPreferencesKey("guest_avatar_id")
        val ACTIVE = booleanPreferencesKey("guest_mode_active")
        val WINS = intPreferencesKey("guest_wins")
        val GAMES = intPreferencesKey("guest_games")
        val WEEKLY_WINS = intPreferencesKey("guest_weekly_wins")
        val WEEKLY_GAMES = intPreferencesKey("guest_weekly_games")
        val WEEK_KEY = stringPreferencesKey("guest_week_key")
    }

    val guestProfileFlow: Flow<GuestProfile> = context.guestDataStore.data.map { prefs ->
        val currentWeek = WeekKeyUtil.currentWeekKey()
        val storedWeek = prefs[Keys.WEEK_KEY] ?: currentWeek
        val sameWeek = storedWeek == currentWeek
        GuestProfile(
            nickname = prefs[Keys.NICKNAME].orEmpty(),
            avatarId = prefs[Keys.AVATAR_ID].orEmpty(),
            guestModeActive = prefs[Keys.ACTIVE] ?: false,
            wins = prefs[Keys.WINS] ?: 0,
            gamesPlayed = prefs[Keys.GAMES] ?: 0,
            weeklyWins = if (sameWeek) (prefs[Keys.WEEKLY_WINS] ?: 0) else 0,
            weeklyGamesPlayed = if (sameWeek) (prefs[Keys.WEEKLY_GAMES] ?: 0) else 0,
            weekKey = currentWeek
        )
    }

    suspend fun getSnapshot(): GuestProfile = guestProfileFlow.first()

    suspend fun startGuestSession(nickname: String) {
        val clean = nickname.trim().take(24).ifBlank { "Guest Player" }
        context.guestDataStore.edit { prefs ->
            prefs[Keys.NICKNAME] = clean
            prefs[Keys.ACTIVE] = true
            if (prefs[Keys.WEEK_KEY] == null) {
                prefs[Keys.WEEK_KEY] = WeekKeyUtil.currentWeekKey()
            }
        }
    }

    suspend fun updateGuestAvatar(avatarId: String) {
        val clean = avatarId.trim().take(64)
        context.guestDataStore.edit { prefs ->
            prefs[Keys.AVATAR_ID] = clean
        }
    }

    suspend fun recordLocalGuestMatch(won: Boolean) {
        val currentWeek = WeekKeyUtil.currentWeekKey()
        context.guestDataStore.edit { prefs ->
            val storedWeek = prefs[Keys.WEEK_KEY] ?: currentWeek
            val sameWeek = storedWeek == currentWeek
            prefs[Keys.GAMES] = (prefs[Keys.GAMES] ?: 0) + 1
            if (won) prefs[Keys.WINS] = (prefs[Keys.WINS] ?: 0) + 1
            prefs[Keys.WEEK_KEY] = currentWeek
            prefs[Keys.WEEKLY_GAMES] = (if (sameWeek) (prefs[Keys.WEEKLY_GAMES] ?: 0) else 0) + 1
            prefs[Keys.WEEKLY_WINS] = (if (sameWeek) (prefs[Keys.WEEKLY_WINS] ?: 0) else 0) + if (won) 1 else 0
        }
    }

    /**
     * Called right after guest stats have been successfully merged into Firestore upon Google upgrade.
     */
    suspend fun clearMigratedGuestStats() {
        context.guestDataStore.edit { prefs ->
            prefs[Keys.ACTIVE] = false
            prefs[Keys.WINS] = 0
            prefs[Keys.GAMES] = 0
            prefs[Keys.WEEKLY_WINS] = 0
            prefs[Keys.WEEKLY_GAMES] = 0
        }
    }

    suspend fun endGuestSession() {
        context.guestDataStore.edit { prefs ->
            prefs[Keys.ACTIVE] = false
        }
    }
}
