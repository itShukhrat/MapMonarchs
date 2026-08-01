package com.mapmonarchs.feature.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.mapmonarchs.R
import com.mapmonarchs.domain.model.AppError
import com.mapmonarchs.domain.model.Blocker
import com.mapmonarchs.domain.validation.RejectionReason

/**
 * Localized renderings of the domain types [AppError] and [Blocker].
 *
 * WHY THESE ARE SEPARATE FROM THE DOMAIN FUNCTIONS: [AppError.playerMessage],
 * [Blocker.explanation] and [Blocker.hudLabel] stay as plain-Kotlin, English
 * functions so they remain callable from tests, logs and any non-Compose code
 * with no Android context. These @Composable resolvers are the ONLY thing the UI
 * calls; they map the exact same exhaustive `when` onto string resources so a new
 * variant added to the sealed type breaks compilation here too, not just in the
 * domain. The English text in strings.xml is kept byte-identical to the domain
 * functions so the two never drift.
 */
@Composable
fun AppError.localizedMessage(): String = when (this) {
    is AppError.Offline -> stringResource(R.string.error_offline)
    is AppError.SchemaMissing -> stringResource(R.string.error_schema_missing)
    is AppError.Unauthorized -> stringResource(R.string.error_unauthorized)
    is AppError.Malformed -> stringResource(R.string.error_malformed)
    is AppError.BackendFailure -> stringResource(R.string.error_backend_failure)
    is AppError.AuthUnavailable -> stringResource(R.string.error_auth_unavailable)
    is AppError.InvalidEmail -> stringResource(R.string.error_invalid_email)
    is AppError.InvalidCode -> stringResource(R.string.error_invalid_code)
    is AppError.RateLimited -> retryAfterSeconds?.let { s ->
        pluralStringResource(R.plurals.error_rate_limited, s, s)
    } ?: stringResource(R.string.error_rate_limited_unknown)
    is AppError.MailNotDelivered -> stringResource(R.string.error_mail_not_delivered)
    is AppError.Unexpected -> stringResource(R.string.error_unexpected)
}

/** Short, upper-case capture-button label. Mirrors [Blocker.hudLabel]. */
@Composable
fun Blocker.localizedHudLabel(): String = when (this) {
    is Blocker.None -> stringResource(R.string.blocker_hud_claim)
    is Blocker.LocationPermissionMissing -> stringResource(R.string.blocker_hud_location_off)
    is Blocker.AcquiringFix -> stringResource(R.string.blocker_hud_acquiring)
    is Blocker.FixRejected -> when (reason) {
        RejectionReason.MOCK_LOCATION -> stringResource(R.string.blocker_hud_mock)
        RejectionReason.LOW_ACCURACY -> stringResource(R.string.blocker_hud_low_accuracy)
        RejectionReason.TOO_FAST -> stringResource(R.string.blocker_hud_too_fast)
        RejectionReason.TELEPORT -> stringResource(R.string.blocker_hud_teleport)
    }
    is Blocker.OutsidePlayableWorld ->
        stringResource(R.string.blocker_hud_outside, stringResource(R.string.country_name).uppercase())
    is Blocker.IdentityResolving -> stringResource(R.string.blocker_hud_identifying)
    is Blocker.IdentityUnavailable -> stringResource(R.string.blocker_hud_no_identity)
    is Blocker.NotSignedIn -> stringResource(R.string.blocker_hud_sign_in)
}

/** Player-facing prose for diagnostics / help. Mirrors [Blocker.explanation]. */
@Composable
fun Blocker.localizedExplanation(): String = when (this) {
    is Blocker.None -> stringResource(R.string.blocker_exp_none)
    is Blocker.LocationPermissionMissing -> stringResource(R.string.blocker_exp_location_missing)
    is Blocker.AcquiringFix -> stringResource(R.string.blocker_exp_acquiring)
    is Blocker.FixRejected -> when (reason) {
        RejectionReason.MOCK_LOCATION -> stringResource(R.string.blocker_exp_mock)
        RejectionReason.LOW_ACCURACY -> stringResource(R.string.blocker_exp_low_accuracy)
        RejectionReason.TOO_FAST -> stringResource(R.string.blocker_exp_too_fast)
        RejectionReason.TELEPORT -> stringResource(R.string.blocker_exp_teleport)
    }
    is Blocker.OutsidePlayableWorld -> {
        val world = stringResource(R.string.country_name)
        if (country != null) {
            stringResource(R.string.blocker_exp_outside_known, world, country!!)
        } else {
            stringResource(R.string.blocker_exp_outside_unknown, world)
        }
    }
    is Blocker.IdentityResolving -> stringResource(R.string.blocker_exp_identity_resolving)
    is Blocker.IdentityUnavailable ->
        stringResource(R.string.blocker_exp_identity_unavailable, error.localizedMessage())
    is Blocker.NotSignedIn -> stringResource(R.string.blocker_exp_not_signed_in)
}
