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
 * Returns true ONLY when the server has confirmed the feature is plan-
 * gated (HTTP 403 + FEATURE_NOT_AVAILABLE). The previous implementation
 * also returned true for ANY non-ApiException — meaning a transient
 * network blip would surface "Feature Not Available" to the user even
 * when the feature is actually included in their plan. Merchant
 * feedback: attendance/overtime screens flickered between "available"
 * and "feature unavailable" depending on momentary connectivity.
 *
 * We now distinguish:
 *   • Confirmed plan gate (server response) → this function returns true
 *   • Network / offline error → fall through to the screen's generic
 *     error handling (show retry, NOT "feature unavailable")
 *
 * The name is kept to avoid churning the 29 call sites; the semantics
 * are now strictly "the server explicitly said this feature is gated."
 */
fun Throwable.isFeatureNotAvailableOrOffline(): Boolean =
    isFeatureNotAvailable()

/**
 * Returns true if this exception represents a suspended vendor account (HTTP 403 + ACCOUNT_SUSPENDED).
 * When this occurs, the app should force-logout the user and show a suspension message.
 */
fun Throwable.isAccountSuspended(): Boolean =
    this is ApiException && statusCode == 403 && errorType == "ACCOUNT_SUSPENDED"

// `Throwable.isNetworkError()` lives in NetworkErrorClassifier.kt now —
// it's an expect/actual pair so iOS doesn't trip over the JVM-only
// `java.net.*` exception types this used to inline. Same return shape;
// every existing caller works unchanged.

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
