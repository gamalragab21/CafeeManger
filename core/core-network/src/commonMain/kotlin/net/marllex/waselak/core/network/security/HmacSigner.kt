package net.marllex.waselak.core.network.security

expect object HmacSigner {
    fun hmacSha256(key: String, data: String): String
    fun sha256(data: String): String
}
