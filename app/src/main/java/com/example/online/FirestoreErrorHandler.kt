package com.example.online

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

/**
 * Standardized structured JSON Firestore error logger.
 * Always call when any Firestore query, write, or listener fails.
 */
fun handleFirestoreError(exception: Throwable, operationType: OperationType, path: String?): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val auth = try {
        FirebaseAuth.getInstance()
    } catch (_: Exception) {
        null
    }
    val currentUser = auth?.currentUser

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

fun unwrapFirestoreError(error: Throwable): Throwable {
    return generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
        ?: error
}

/**
 * Converts low-level network and Firestore exceptions into friendly user-facing messages.
 */
fun friendlyNetworkErrorMessage(
    error: Throwable?,
    fallback: String = "Something went wrong. Please try again."
): String {
    if (error == null) return fallback
    val root = unwrapFirestoreError(error)
    if (root is FirebaseFirestoreException) {
        return when (root.code) {
            FirebaseFirestoreException.Code.UNAVAILABLE ->
                "No internet connection. Please check your Wi-Fi or mobile data and try again."
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                "Connection timed out. Please check your signal and try again."
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "You don't have permission for this action. Try signing in again."
            FirebaseFirestoreException.Code.UNAUTHENTICATED ->
                "Your session expired. Please sign in with Google again."
            FirebaseFirestoreException.Code.NOT_FOUND ->
                "We couldn't find that room or match. It may have already ended."
            FirebaseFirestoreException.Code.ABORTED,
            FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
                "The game state just updated. Please try again."
            FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED ->
                "Server is busy right now. Please wait a moment and retry."
            else -> root.localizedMessage?.takeIf { it.isNotBlank() } ?: fallback
        }
    }

    val chain = generateSequence(error) { it.cause }.toList()
    if (chain.any { it is UnknownHostException || it is SocketTimeoutException || it is IOException }) {
        return "No internet connection. Please check your Wi-Fi or mobile data and try again."
    }

    val rawMsg = error.localizedMessage.orEmpty()
    return when {
        rawMsg.contains("Unable to resolve host", ignoreCase = true) ||
            rawMsg.contains("network", ignoreCase = true) ||
            rawMsg.contains("offline", ignoreCase = true) ||
            rawMsg.contains("UNAVAILABLE", ignoreCase = true) ->
            "No internet connection. Please check your Wi-Fi or mobile data and try again."
        rawMsg.isNotBlank() -> rawMsg
        else -> fallback
    }
}

/**
 * Lightweight connectivity check using Android's ConnectivityManager.
 */
fun isDeviceOnline(context: Context): Boolean {
    return try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } catch (_: Exception) {
        true
    }
}
