package com.example.online

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed interface GoogleAuthResult {
    data class Success(val user: FirebaseUser) : GoogleAuthResult
    data object Cancelled : GoogleAuthResult
    data object NoSavedCredential : GoogleAuthResult
    data class Failure(val message: String) : GoogleAuthResult
}

/**
 * Jetpack Credential Manager + Firebase Auth integration for Google Sign-In.
 * Strictly prohibits anonymous or password auth; uses one credential option per request.
 */
class AuthManager(
    private val appContext: Context,
    private val firebaseAuth: FirebaseAuth = Firebase.auth
) {
    private val credentialManager: CredentialManager = CredentialManager.create(appContext)

    val currentUser: FirebaseUser?
        get() = firebaseAuth.currentUser

    fun authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser)
        }
        firebaseAuth.addAuthStateListener(listener)
        trySend(firebaseAuth.currentUser)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    /**
     * Silent auto sign-in for returning users using [GetGoogleIdOption] in its own request.
     */
    suspend fun attemptAutoSignIn(activity: Activity): GoogleAuthResult {
        if (firebaseAuth.currentUser != null) {
            return GoogleAuthResult.Success(firebaseAuth.currentUser!!)
        }
        return try {
            val webClientId = appContext.getString(R.string.default_web_client_id)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(true)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val response = credentialManager.getCredential(activity, request)
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authResult = firebaseAuth
                    .signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
                    .await()
                val user = authResult.user
                if (user != null) {
                    GoogleAuthResult.Success(user)
                } else {
                    GoogleAuthResult.Failure("Google sign-in did not return a user.")
                }
            } else {
                GoogleAuthResult.NoSavedCredential
            }
        } catch (_: NoCredentialException) {
            GoogleAuthResult.NoSavedCredential
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Silent auto sign-in cancelled", e)
            GoogleAuthResult.Cancelled
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialException) {
            Log.w(TAG, "Silent auto sign-in unavailable: ${e.message}")
            GoogleAuthResult.NoSavedCredential
        } catch (e: Exception) {
            Log.w(TAG, "Silent auto sign-in failed: ${e.message}")
            GoogleAuthResult.NoSavedCredential
        }
    }

    /**
     * Explicit interactive Google Sign-In flow triggered by the user tapping "Sign in with Google".
     */
    suspend fun signInWithGoogle(activity: Activity): GoogleAuthResult {
        return try {
            val webClientId = appContext.getString(R.string.default_web_client_id)
            val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(webClientId).build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInWithGoogleOption)
                .build()

            val response = credentialManager.getCredential(activity, request)
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authResult = firebaseAuth
                    .signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
                    .await()
                val user = authResult.user
                if (user != null) {
                    GoogleAuthResult.Success(user)
                } else {
                    GoogleAuthResult.Failure("Unable to complete Google sign-in.")
                }
            } else {
                GoogleAuthResult.Failure("Unsupported credential returned.")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google sign-in cancelled by user", e)
            GoogleAuthResult.Cancelled
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Google sign-in credential error", e)
            GoogleAuthResult.Failure(e.message ?: "Google Sign-In failed. Please try again.")
        } catch (e: Exception) {
            Log.e(TAG, "Google sign-in failed", e)
            GoogleAuthResult.Failure(e.message ?: "Google Sign-In failed. Please try again.")
        }
    }

    /**
     * Signs out atomically inside NonCancellable: clears Credential Manager state FIRST,
     * then signs out FirebaseAuth so silent auto-sign-in doesn't immediately log back in.
     */
    suspend fun signOut() {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear credential state on sign out", e)
            }
            firebaseAuth.signOut()
        }
    }

    companion object {
        private const val TAG = "Auth"
    }
}
