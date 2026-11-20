package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.engine.GameModeId
import com.example.engine.GameState
import com.example.online.GameStateFirestoreMapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.savedGameDataStore: DataStore<Preferences> by preferencesDataStore(name = "ludo_saved_game")

data class SavedGameSummary(
    val mode: GameModeId,
    val activePlayerName: String,
    val playerCount: Int,
    val turnNumber: Int,
    val savedAtMs: Long
)

/**
 * Persists an in-progress local (Pass-and-Play / vs Bots) match in DataStore
 * so players can resume exactly where they left off across all 9 game modes.
 */
class SavedGameRepository(private val context: Context) {

    private object Keys {
        val STATE_JSON = stringPreferencesKey("saved_game_state_json")
        val MODE = stringPreferencesKey("saved_game_mode")
        val ACTIVE_NAME = stringPreferencesKey("saved_game_active_name")
        val PLAYER_COUNT = stringPreferencesKey("saved_game_player_count")
        val TURN_NUMBER = stringPreferencesKey("saved_game_turn_number")
        val SAVED_AT_MS = longPreferencesKey("saved_game_saved_at_ms")
    }

    val savedGameSummaryFlow: Flow<SavedGameSummary?> = context.savedGameDataStore.data.map { prefs ->
        val json = prefs[Keys.STATE_JSON]
        if (json.isNullOrBlank()) return@map null
        val mode = GameModeId.entries.firstOrNull { it.name == prefs[Keys.MODE] } ?: GameModeId.CLASSIC
        SavedGameSummary(
            mode = mode,
            activePlayerName = prefs[Keys.ACTIVE_NAME].orEmpty().ifBlank { "Player 1" },
            playerCount = prefs[Keys.PLAYER_COUNT]?.toIntOrNull() ?: 2,
            turnNumber = prefs[Keys.TURN_NUMBER]?.toIntOrNull() ?: 1,
            savedAtMs = prefs[Keys.SAVED_AT_MS] ?: 0L
        )
    }

    suspend fun saveGame(state: GameState) {
        if (state.isMatchFinished) {
            clearSavedGame()
            return
        }
        val json = encodeGameStateToJson(state)
        context.savedGameDataStore.edit { prefs ->
            prefs[Keys.STATE_JSON] = json
            prefs[Keys.MODE] = state.mode.name
            prefs[Keys.ACTIVE_NAME] = state.activePlayer.nickname
            prefs[Keys.PLAYER_COUNT] = state.players.size.toString()
            prefs[Keys.TURN_NUMBER] = state.turnNumber.toString()
            prefs[Keys.SAVED_AT_MS] = System.currentTimeMillis()
        }
    }

    suspend fun loadSavedGame(): GameState? {
        val prefs = context.savedGameDataStore.data.first()
        val json = prefs[Keys.STATE_JSON] ?: return null
        return decodeGameStateFromJson(json)
    }

    suspend fun clearSavedGame() {
        context.savedGameDataStore.edit { prefs ->
            prefs.remove(Keys.STATE_JSON)
            prefs.remove(Keys.MODE)
            prefs.remove(Keys.ACTIVE_NAME)
            prefs.remove(Keys.PLAYER_COUNT)
            prefs.remove(Keys.TURN_NUMBER)
            prefs.remove(Keys.SAVED_AT_MS)
        }
    }

    companion object {
        fun encodeGameStateToJson(state: GameState): String {
            val map = GameStateFirestoreMapper.toFirestoreMap(state)
            return mapToJsonObject(map).toString()
        }

        fun decodeGameStateFromJson(json: String): GameState? {
            return try {
                val obj = JSONObject(json)
                val map = jsonObjectToMap(obj)
                GameStateFirestoreMapper.fromFirestoreMap(map)
            } catch (_: Exception) {
                null
            }
        }

        private fun mapToJsonObject(map: Map<*, *>): JSONObject {
            val obj = JSONObject()
            for ((k, v) in map) {
                val key = k as? String ?: continue
                obj.put(key, toJsonValue(v))
            }
            return obj
        }

        private fun toJsonValue(value: Any?): Any? = when (value) {
            null -> JSONObject.NULL
            is Map<*, *> -> mapToJsonObject(value)
            is Iterable<*> -> JSONArray().apply {
                for (item in value) put(toJsonValue(item))
            }
            else -> value
        }

        private fun jsonObjectToMap(obj: JSONObject): Map<String, Any?> {
            val result = mutableMapOf<String, Any?>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                result[key] = fromJsonValue(obj.get(key))
            }
            return result
        }

        private fun fromJsonValue(value: Any?): Any? = when (value) {
            null, JSONObject.NULL -> null
            is JSONObject -> jsonObjectToMap(value)
            is JSONArray -> List(value.length()) { idx -> fromJsonValue(value.get(idx)) }
            else -> value
        }
    }
}
