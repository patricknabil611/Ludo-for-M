package com.example.ui.online

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.online.AuthManager
import com.example.online.GoogleAuthResult
import com.example.online.GuestProfile
import com.example.online.GuestProfileRepository
import com.example.online.OnlineProfileAndLeaderboardRepository
import com.example.online.UserProfileDoc
import com.example.online.friendlyNetworkErrorMessage
import com.example.online.unwrapFirestoreError
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnlineAuthUiState(
    val firebaseUser: FirebaseUser? = null,
    val userProfile: UserProfileDoc? = null,
    val guestProfile: GuestProfile = GuestProfile(),
    val isSigningIn: Boolean = false,
    val errorMessage: String? = null,
    val statusBanner: String? = null
) {
    val isSignedInWithGoogle: Boolean
        get() = firebaseUser != null

    val effectiveNickname: String
        get() = when {
            userProfile != null && userProfile.nickname.isNotBlank() -> userProfile.nickname
            firebaseUser?.displayName?.isNotBlank() == true -> firebaseUser.displayName!!.take(24)
            guestProfile.nickname.isNotBlank() -> guestProfile.nickname
            else -> "Player"
        }

    val hasGuestStatsToUpgrade: Boolean
        get() = !isSignedInWithGoogle && (guestProfile.gamesPlayed > 0 || guestProfile.guestModeActive)
}

class OnlineAuthViewModel(
    private val authManager: AuthManager,
    private val guestRepo: GuestProfileRepository,
    private val profileRepo: OnlineProfileAndLeaderboardRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnlineAuthUiState(firebaseUser = authManager.currentUser))
    val uiState: StateFlow<OnlineAuthUiState> = _uiState.asStateFlow()

    private var profileObserverJob: Job? = null
    private var syncedUid: String? = null
    private var autoSignInAttempted = false

    init {
        viewModelScope.launch {
            guestRepo.guestProfileFlow.collect { guest ->
                _uiState.update { it.copy(guestProfile = guest) }
            }
        }

        viewModelScope.launch {
            authManager.authStateFlow().collect { user ->
                _uiState.update { it.copy(firebaseUser = user) }
                if (user != null) {
                    onSignedInUserDetected(user)
                } else {
                    syncedUid = null
                    profileObserverJob?.cancel()
                    profileObserverJob = null
                    _uiState.update { it.copy(userProfile = null) }
                }
            }
        }
    }

    fun attemptAutoSignInOnce(activity: Activity) {
        if (autoSignInAttempted || authManager.currentUser != null) return
        autoSignInAttempted = true
        viewModelScope.launch {
            authManager.attemptAutoSignIn(activity)
        }
    }

    fun signInWithGoogle(activity: Activity, onSuccess: () -> Unit = {}) {
        if (_uiState.value.isSigningIn) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSigningIn = true, errorMessage = null, statusBanner = null) }
            when (val result = authManager.signInWithGoogle(activity)) {
                is GoogleAuthResult.Success -> {
                    syncProfileAndMigrateGuest(result.user)
                    _uiState.update { it.copy(isSigningIn = false) }
                    onSuccess()
                }
                GoogleAuthResult.Cancelled, GoogleAuthResult.NoSavedCredential -> {
                    _uiState.update { it.copy(isSigningIn = false) }
                }
                is GoogleAuthResult.Failure -> {
                    _uiState.update {
                        it.copy(isSigningIn = false, errorMessage = result.message)
                    }
                }
            }
        }
    }

    fun continueAsGuest(nickname: String, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            guestRepo.startGuestSession(nickname)
            _uiState.update {
                it.copy(
                    errorMessage = null,
                    statusBanner = "Playing as Guest. Upgrade to Google anytime to join online rooms & leaderboard!"
                )
            }
            onSaved()
        }
    }

    fun recordLocalMatchOutcomeForGuest(won: Boolean) {
        if (_uiState.value.isSignedInWithGoogle) return
        if (!_uiState.value.guestProfile.guestModeActive) return
        viewModelScope.launch {
            guestRepo.recordLocalGuestMatch(won)
        }
    }

    fun updateNickname(newNickname: String) {
        val clean = newNickname.trim().take(24)
        if (clean.isEmpty()) return
        val uid = _uiState.value.firebaseUser?.uid
        viewModelScope.launch {
            if (uid == null) {
                guestRepo.startGuestSession(clean)
                _uiState.update { it.copy(statusBanner = "Guest nickname updated!") }
                return@launch
            }
            val res = profileRepo.updateNickname(uid, clean)
            if (res.isFailure) {
                _uiState.update {
                    it.copy(
                        errorMessage = friendlyNetworkErrorMessage(
                            res.exceptionOrNull(),
                            "Could not update nickname."
                        )
                    )
                }
            } else {
                _uiState.update { it.copy(statusBanner = "Nickname updated!") }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            profileObserverJob?.cancel()
            profileObserverJob = null
            syncedUid = null
            authManager.signOut()
            _uiState.update {
                it.copy(
                    firebaseUser = null,
                    userProfile = null,
                    statusBanner = "Signed out."
                )
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, statusBanner = null) }
    }

    private fun onSignedInUserDetected(user: FirebaseUser) {
        if (syncedUid != user.uid) {
            syncedUid = user.uid
            viewModelScope.launch {
                syncProfileAndMigrateGuest(user)
            }
        }
        profileObserverJob?.cancel()
        profileObserverJob = viewModelScope.launch {
            profileRepo.observeMyProfile(user.uid)
                .catch { error ->
                    val root = unwrapFirestoreError(error)
                    if (error is CancellationException) {
                        throw error
                    } else if (root is FirebaseFirestoreException) {
                        _uiState.update {
                            it.copy(
                                errorMessage = friendlyNetworkErrorMessage(
                                    error,
                                    "Unable to load cloud profile."
                                )
                            )
                        }
                    }
                }
                .collect { profile ->
                    _uiState.update { it.copy(userProfile = profile) }
                }
        }
    }

    private suspend fun syncProfileAndMigrateGuest(user: FirebaseUser) {
        val guest = guestRepo.getSnapshot()
        val hadGuestStats = guest.gamesPlayed > 0
        val guestToMerge = if (guest.guestModeActive || hadGuestStats) guest else null
        val defaultName = user.displayName?.take(24)?.ifBlank { "Player" } ?: "Player"

        val result = profileRepo.ensureProfileAndMergeGuestStats(
            uid = user.uid,
            defaultDisplayName = defaultName,
            guestStatsToMerge = guestToMerge
        )
        result.onSuccess { mergedProfile ->
            if (guestToMerge != null) {
                guestRepo.clearMigratedGuestStats()
            }
            _uiState.update {
                it.copy(
                    userProfile = mergedProfile,
                    statusBanner = if (hadGuestStats) {
                        "Upgraded to Google! Your ${guest.gamesPlayed} guest matches were preserved."
                    } else it.statusBanner
                )
            }
        }.onFailure { err ->
            _uiState.update {
                it.copy(
                    errorMessage = friendlyNetworkErrorMessage(
                        err,
                        "Could not sync cloud profile. Check your internet connection."
                    )
                )
            }
        }
    }

    class Factory(
        private val authManager: AuthManager,
        private val guestRepo: GuestProfileRepository,
        private val profileRepo: OnlineProfileAndLeaderboardRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return OnlineAuthViewModel(authManager, guestRepo, profileRepo) as T
        }
    }
}
