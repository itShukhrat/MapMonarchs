package com.mapmonarchs.domain.repository

import com.mapmonarchs.domain.model.Identity

/**
 * Session source.
 *
 * Returns Identity directly rather than Outcome<Identity> because Identity
 * already models failure explicitly -- wrapping it would produce
 * Outcome.Failure and Identity.Unavailable meaning the same thing.
 */
interface AuthRepository {

    /**
     * Reuses an existing persisted session if there is one; otherwise attempts
     * anonymous sign-in. Never fabricates a user: a failure returns
     * Identity.Unavailable carrying the cause.
     */
    suspend fun ensureSession(): Identity
}