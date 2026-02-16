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
