package com.example.ui.online

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.online.LeaderboardSnapshot
import com.example.online.OnlineProfileAndLeaderboardRepository
import com.example.online.friendlyNetworkErrorMessage
import com.example.online.unwrapFirestoreError
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LeaderboardTab {
    ALL_TIME,
    THIS_WEEK
}

sealed interface LeaderboardUiState {
    data object Loading : LeaderboardUiState
    data class Success(val snapshot: LeaderboardSnapshot) : LeaderboardUiState
    data class Error(val message: String) : LeaderboardUiState
}

/**
 * Auth-scoped ViewModel (keyed by uid) for the Google-only Global Leaderboard
 * with All-Time & This Week tabs and sticky player rank card.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LeaderboardViewModel(
    private val uid: String,
    private val repo: OnlineProfileAndLeaderboardRepository
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(LeaderboardTab.ALL_TIME)
    val selectedTab: StateFlow<LeaderboardTab> = _selectedTab.asStateFlow()

    private val _uiState = MutableStateFlow<LeaderboardUiState>(LeaderboardUiState.Loading)
    val uiState: StateFlow<LeaderboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _selectedTab
                .flatMapLatest { tab ->
                    _uiState.value = LeaderboardUiState.Loading
                    repo.observeLeaderboard(
                        myUid = uid,
                        weekly = tab == LeaderboardTab.THIS_WEEK
                    )
                }
                .catch { error ->
                    if (error is CancellationException) {
                        throw error
                    } else {
                        _uiState.update {
                            LeaderboardUiState.Error(
                                friendlyNetworkErrorMessage(
                                    error,
                                    "Unable to load leaderboard. Check your internet connection."
                                )
                            )
                        }
                    }
                }
                .collect { snapshot ->
                    _uiState.value = LeaderboardUiState.Success(snapshot)
                }
        }
    }

    fun selectTab(tab: LeaderboardTab) {
        if (_selectedTab.value != tab) {
            _selectedTab.value = tab
        }
    }

    class Factory(
        private val uid: String,
        private val repo: OnlineProfileAndLeaderboardRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LeaderboardViewModel(uid, repo) as T
        }
    }
}
