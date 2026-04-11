package net.marllex.waselak.core.network

/**
 * Exception thrown when the API returns a non-success HTTP status code.
 * Contains the parsed error message from the server's ErrorResponse body.
 */
class ApiException(
    val statusCode: Int,
    val errorMessage: String,
    val errorType: String? = null,
) : Exception(errorMessage)

/**
 * Returns true if this exception represents a plan-gated feature (HTTP 403 + FEATURE_NOT_AVAILABLE).
 * Use this to distinguish plan restrictions from other 403 errors (e.g., ACCOUNT_SUSPENDED).
 */
fun Throwable.isFeatureNotAvailable(): Boolean =
    this is ApiException && statusCode == 403 && errorType == "FEATURE_NOT_AVAILABLE"

/**
 * Returns true if this exception represents a plan limit exceeded (HTTP 403 + PLAN_LIMIT_EXCEEDED).
 * Thrown when the vendor tries to create more resources than their plan allows.
 */
fun Throwable.isPlanLimitExceeded(): Boolean =
    this is ApiException && statusCode == 403 && errorType == "PLAN_LIMIT_EXCEEDED"

/**
 * Returns true if the error is either a plan-gated feature (403) OR a network/connection error.
 * When offline, plan-gated features cannot be verified, so they should be treated as unavailable.
 */
fun Throwable.isFeatureNotAvailableOrOffline(): Boolean =
    isFeatureNotAvailable() || this !is ApiException

/**
 * Returns true if this exception represents a suspended vendor account (HTTP 403 + ACCOUNT_SUSPENDED).
 * When this occurs, the app should force-logout the user and show a suspension message.
 */
fun Throwable.isAccountSuspended(): Boolean =
    this is ApiException && statusCode == 403 && errorType == "ACCOUNT_SUSPENDED"

/**
 * Returns true if this is a network/connection error (offline, timeout, DNS failure).
 */
fun Throwable.isNetworkError(): Boolean =
    this !is ApiException && (
        this is java.net.ConnectException ||
        this is java.net.UnknownHostException ||
        this is java.net.SocketTimeoutException ||
        this.message?.contains("No route to host") == true ||
        this.message?.contains("Network is unreachable") == true ||
        this.message?.contains("Unable to resolve host") == true ||
        this.message?.contains("timeout") == true ||
        this.cause?.let { it is java.net.ConnectException || it is java.net.UnknownHostException } == true
    )

/**
 * Returns a user-friendly Arabic error message for any exception.
 */
fun Throwable.userFriendlyMessage(): String = when {
    isNetworkError() -> "لا يوجد اتصال بالإنترنت. يرجى التحقق من الشبكة والمحاولة مرة أخرى."
    isFeatureNotAvailable() -> "هذه الميزة غير متاحة في باقتك الحالية."
    isPlanLimitExceeded() -> "لقد تجاوزت الحد المسموح به في باقتك."
    isAccountSuspended() -> "تم إيقاف حسابك. يرجى التواصل مع الدعم."
    this is ApiException && statusCode == 401 -> "انتهت صلاحية الجلسة. يرجى تسجيل الدخول مرة أخرى."
    this is ApiException -> errorMessage
    else -> "حدث خطأ غير متوقع. يرجى المحاولة مرة أخرى."
}
