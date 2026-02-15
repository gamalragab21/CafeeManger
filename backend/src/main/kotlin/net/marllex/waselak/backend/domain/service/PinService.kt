package net.marllex.waselak.backend.domain.service

import org.mindrot.jbcrypt.BCrypt
import java.util.concurrent.ConcurrentHashMap

/**
 * Service for managing worker PINs
 * - Hash PINs using bcrypt
 * - Verify PINs against stored hashes
 * - Validate PIN format (4-6 digits, numeric only)
 * - Rate limit PIN verification attempts
 */
class PinService {
    private val rateLimiter = PinRateLimiter()

    companion object {
        private const val BCRYPT_COST = 10
        private const val MIN_PIN_LENGTH = 4
        private const val MAX_PIN_LENGTH = 6
    }

    /**
     * Hash a PIN using bcrypt
     */
    fun hashPin(pin: String): String {
        require(isValidPin(pin)) { "PIN must be 4-6 digits" }
        return BCrypt.hashpw(pin, BCrypt.gensalt(BCRYPT_COST))
    }

    /**
     * Verify a PIN against a stored hash
     */
    fun verifyPin(pin: String, hash: String): Boolean {
        return try {
            BCrypt.checkpw(pin, hash)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Validate PIN format (4-6 digits, numeric only)
     */
    fun isValidPin(pin: String): Boolean {
        return pin.length in MIN_PIN_LENGTH..MAX_PIN_LENGTH && pin.all { it.isDigit() }
    }

    /**
     * Generate a default PIN for a worker
     * Uses last 4 digits of phone if available, otherwise random
     */
    fun generateDefaultPin(workerId: String, phone: String?): String {
        return if (!phone.isNullOrBlank() && phone.length >= 4) {
            phone.takeLast(4)
        } else {
            // Generate random 4-digit PIN
            (1000..9999).random().toString()
        }
    }

    /**
     * Check if worker can attempt PIN verification (rate limiting)
     */
    fun canAttemptPin(workerId: String): Boolean {
        return rateLimiter.checkAndRecord(workerId)
    }

    /**
     * Reset rate limit for a worker (e.g., after successful auth)
     */
    fun resetRateLimit(workerId: String) {
        rateLimiter.reset(workerId)
    }

    /**
     * Get remaining lockout time in seconds
     */
    fun getRemainingLockoutTime(workerId: String): Long {
        return rateLimiter.getRemainingLockoutTime(workerId)
    }
}

/**
 * Rate limiter for PIN verification attempts
 * - Max 3 failed attempts per worker
 * - 5-minute lockout after exceeding limit
 */
class PinRateLimiter {
    private val attempts = ConcurrentHashMap<String, MutableList<Long>>()

    companion object {
        private const val MAX_ATTEMPTS = 3
        private const val WINDOW_MS = 5 * 60 * 1000L // 5 minutes
    }

    /**
     * Check if worker can attempt PIN verification and record the attempt
     * Returns true if allowed, false if rate limited
     */
    fun checkAndRecord(workerId: String): Boolean {
        val now = System.currentTimeMillis()
        val workerAttempts = attempts.getOrPut(workerId) { mutableListOf() }

        synchronized(workerAttempts) {
            // Remove old attempts outside the window
            workerAttempts.removeAll { it < now - WINDOW_MS }

            if (workerAttempts.size >= MAX_ATTEMPTS) {
                return false // Rate limited
            }

            workerAttempts.add(now)
            return true
        }
    }

    /**
     * Reset attempts for a worker (e.g., after successful authentication)
     */
    fun reset(workerId: String) {
        attempts.remove(workerId)
    }

    /**
     * Get remaining lockout time in seconds
     */
    fun getRemainingLockoutTime(workerId: String): Long {
        val now = System.currentTimeMillis()
        val workerAttempts = attempts[workerId] ?: return 0

        synchronized(workerAttempts) {
            workerAttempts.removeAll { it < now - WINDOW_MS }

            if (workerAttempts.size < MAX_ATTEMPTS) {
                return 0
            }

            val oldestAttempt = workerAttempts.minOrNull() ?: return 0
            val lockoutEnd = oldestAttempt + WINDOW_MS
            val remaining = (lockoutEnd - now) / 1000

            return if (remaining > 0) remaining else 0
        }
    }
}
