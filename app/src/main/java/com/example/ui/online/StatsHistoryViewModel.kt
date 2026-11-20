package com.example.ui.online

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.MatchHistoryEntry
import com.example.data.MatchHistoryRepository
import com.example.engine.GameModeId
import com.example.online.friendlyNetworkErrorMessage
import com.example.online.unwrapFirestoreError
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ModeAggregateStats(
    val gamesPlayed: Int = 0,
    val wins: Int = 0,
    val capturesMade: Int = 0,
    val tokensHome: Int = 0,
    val powerUpsUsed: Int = 0
) {
    val winRatePercent: Int
        get() = if (gamesPlayed > 0) ((wins * 100.0) / gamesPlayed).toInt().coerceIn(0, 100) else 0
}

data class StatsOverview(
    val overall: ModeAggregateStats = ModeAggregateStats(),
    val byMode: Map<GameModeId, ModeAggregateStats> = emptyMap(),
    val favoriteMode: GameModeId? = null,
    val bestWinRateMode: Pair<GameModeId, Int>? = null
)

data class StatsHistoryUiState(
    val entries: List<MatchHistoryEntry> = emptyList(),
    val overview: StatsOverview = StatsOverview(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

/**
 * ViewModel for Player Stats & Match History (keyed by uid or "guest").
 * Merges cloud history for authenticated Google accounts with local history and computes
 * overall and per-mode statistics (games, wins, win rate, captures, tokens home, favorite & best mode).
 */
class StatsHistoryViewModel(
    private val uid: String?,
    private val repo: MatchHistoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsHistoryUiState())
    val uiState: StateFlow<StatsHistoryUiState> = _uiState.asStateFlow()

    private var localEntries: List<MatchHistoryEntry> = emptyList()
    private var cloudEntries: List<MatchHistoryEntry> = emptyList()

    init {
        viewModelScope.launch {
            repo.localHistoryFlow.collect { local ->
                localEntries = local
                publishMerged(isLoading = false)
            }
        }

        if (!uid.isNullOrBlank()) {
            viewModelScope.launch {
                repo.observeCloudHistory(uid)
                    .catch { error ->
                        val root = unwrapFirestoreError(error)
                        if (root is FirebaseFirestoreException) {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    errorMessage = friendlyNetworkErrorMessage(
                                        root,
                                        "Showing local history — couldn't sync cloud matches."
                                    )
                                )
                            }
                        } else if (error is CancellationException) {
                            throw error
                        }
                    }
                    .collect { cloud ->
                        cloudEntries = cloud
                        publishMerged(isLoading = false)
                    }
            }
        }
    }

    private fun publishMerged(isLoading: Boolean) {
        val byId = linkedMapOf<String, MatchHistoryEntry>()
        for (item in cloudEntries) byId[item.id] = item
        for (item in localEntries) byId.putIfAbsent(item.id, item)
        val sorted = byId.values.sortedByDescending { it.playedAtMs }.take(100)
        _uiState.update {
            it.copy(
                entries = sorted,
                overview = computeOverview(sorted),
                isLoading = isLoading
            )
        }
    }

    companion object {
        fun computeOverview(entries: List<MatchHistoryEntry>): StatsOverview {
            if (entries.isEmpty()) return StatsOverview()
            val overall = ModeAggregateStats(
                gamesPlayed = entries.size,
                wins = entries.count { it.won },
                capturesMade = entries.sumOf { it.capturesMade },
                tokensHome = entries.sumOf { it.tokensHome },
                powerUpsUsed = entries.sumOf { it.powerUpsUsed }
            )
            val byMode = entries.groupBy { it.mode }.mapValues { (_, list) ->
                ModeAggregateStats(
                    gamesPlayed = list.size,
                    wins = list.count { it.won },
                    capturesMade = list.sumOf { it.capturesMade },
                    tokensHome = list.sumOf { it.tokensHome },
                    powerUpsUsed = list.sumOf { it.powerUpsUsed }
                )
            }
            val favoriteMode = byMode.entries
                .maxWithOrNull(compareBy<Map.Entry<GameModeId, ModeAggregateStats>> { it.value.gamesPlayed }
                    .thenBy { it.value.wins })
                ?.key
            val bestEntry = byMode.entries
                .filter { it.value.gamesPlayed > 0 }
                .maxWithOrNull(compareBy<Map.Entry<GameModeId, ModeAggregateStats>> { it.value.winRatePercent }
                    .thenBy { it.value.wins }
                    .thenBy { it.value.gamesPlayed })
            val bestWinRateMode = bestEntry?.let { it.key to it.value.winRatePercent }

            return StatsOverview(
                overall = overall,
                byMode = byMode,
                favoriteMode = favoriteMode,
                bestWinRateMode = bestWinRateMode
            )
        }
    }

    class Factory(
        private val uid: String?,
        private val repo: MatchHistoryRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StatsHistoryViewModel(uid, repo) as T
        }
    }
}
