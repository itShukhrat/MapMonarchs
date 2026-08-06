package com.mapmonarchs.feature.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mapmonarchs.domain.model.AppError
import com.mapmonarchs.ui.theme.MapMonarchsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The sign-in surface, which had no instrumented coverage at all.
 *
 * The assertions that matter most here are the error ones. Every auth failure
 * looks the same to a player -- a code that does not arrive -- and collapsing
 * them into one generic apology is what makes an email wall feel broken. A
 * rate-limited player must be told to WAIT, because trying again immediately is
 * the one action guaranteed to fail.
 */
@RunWith(AndroidJUnit4::class)
class SignInScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private fun show(state: SignInState) {
        rule.setContent {
            MapMonarchsTheme {
                SignInScreen(
                    state = state,
                    onEmailChanged = {},
                    onCodeChanged = {},
                    onRequestCode = {},
                    onVerifyCode = {},
                    onResend = {},
                    onChangeEmail = {},
                    onBack = {}
                )
            }
        }
    }

    // --- the address step ----------------------------------------------------

    @Test
    fun sendIsDisabledUntilTheAddressCouldPlausiblyBeReal() {
        show(SignInState.EnteringEmail(email = "not-an-address"))
        rule.onNodeWithTag(SignInTags.PRIMARY).assertTextEquals("SEND CODE").assertIsNotEnabled()
    }

    @Test
    fun sendIsDisabledOnAnEmptyField() {
        show(SignInState.EnteringEmail())
        rule.onNodeWithTag(SignInTags.PRIMARY).assertIsNotEnabled()
    }

    @Test
    fun sendBecomesAvailableForAPlausibleAddress() {
        show(SignInState.EnteringEmail(email = "player@example.com"))
        rule.onNodeWithTag(SignInTags.PRIMARY).assertIsEnabled()
    }

    /** An in-flight request must not be fireable twice. */
    @Test
    fun sendingStateIsLabelledAndNotClickable() {
        show(SignInState.EnteringEmail(email = "player@example.com", sending = true))
        rule.onNodeWithTag(SignInTags.PRIMARY).assertTextEquals("SENDING").assertIsNotEnabled()
    }

    @Test
    fun theScreenStatesWhatTheAddressIsUsedFor() {
        show(SignInState.EnteringEmail())
        // An unexplained login wall in a walking game is where players leave.
        rule.onNodeWithTag(SignInTags.PROVIDER_NOTE).assertIsDisplayed()
    }

    // --- the code step -------------------------------------------------------

    @Test
    fun confirmIsDisabledUntilSixDigitsArePresent() {
        show(SignInState.EnteringCode(email = "player@example.com", code = "123"))
        rule.onNodeWithTag(SignInTags.PRIMARY).assertTextEquals("CONFIRM").assertIsNotEnabled()
    }

    @Test
    fun confirmBecomesAvailableOnASixDigitCode() {
        show(SignInState.EnteringCode(email = "player@example.com", code = "123456"))
        rule.onNodeWithTag(SignInTags.PRIMARY).assertIsEnabled()
    }

    @Test
    fun verifyingStateIsLabelledAndNotClickable() {
        show(SignInState.EnteringCode("player@example.com", code = "123456", verifying = true))
        rule.onNodeWithTag(SignInTags.PRIMARY).assertTextEquals("VERIFYING").assertIsNotEnabled()
    }

    @Test
    fun theCodeStepNamesTheAddressItWasSentTo() {
        show(SignInState.EnteringCode(email = "player@example.com"))
        // Without this a typo means waiting forever for mail sent somewhere else.
        // Substring so the surrounding copy can change without breaking the intent.
        rule.onNodeWithText("player@example.com", substring = true).assertIsDisplayed()
    }

    @Test
    fun duringCooldownResendBecomesACalmCountdownNotAButton() {
        // The point of the cooldown: the player waits instead of jabbing RESEND
        // into the backend's rate limit. The control becomes a timer naming when
        // it returns, rather than a live button or a silent disabled one.
        show(SignInState.EnteringCode(email = "player@example.com", resendInSeconds = 25))
        rule.onNodeWithText("25", substring = true).assertIsDisplayed()
    }

    @Test
    fun aResendIsAcknowledgedSoTheActionDoesNotFeelIgnored() {
        show(SignInState.EnteringCode(email = "player@example.com", resent = true))
        rule.onNodeWithText("A new code has been sent.").assertIsDisplayed()
    }

    // --- failures stay distinguishable --------------------------------------

    /**
     * The specific wording the brief demands: rate limiting must say WAIT.
     * "Something went wrong" invites an immediate retry, which is precisely the
     * action guaranteed to fail again and deepen the rate limit.
     */
    @Test
    fun rateLimitingTellsThePlayerToWaitRatherThanRetry() {
        show(
            SignInState.EnteringEmail(
                email = "player@example.com",
                error = AppError.RateLimited("for security purposes, you can only request this after 51 seconds")
            )
        )
        val message = AppError.RateLimited("x").playerMessage()
        rule.onNodeWithText(message).assertIsDisplayed()
        assertEquals(
            "a rate-limited player must be told to wait",
            true,
            message.contains("Wait", ignoreCase = true)
        )
        assertEquals(
            "must not collapse into a generic apology",
            false,
            message.contains("Something went wrong", ignoreCase = true)
        )
    }

    @Test
    fun undeliverableMailIsNamedExplicitlyNotHiddenAsUnexpected() {
        val error = AppError.MailNotDelivered("smtp not configured")
        show(SignInState.EnteringEmail(email = "player@example.com", error = error))
        rule.onNodeWithTag(SignInTags.ERROR).assertIsDisplayed()
        rule.onNodeWithText(error.playerMessage()).assertIsDisplayed()
        assertEquals(
            "the player must learn that delivery, not their typing, is the problem",
            true,
            error.playerMessage().contains("could not be sent", ignoreCase = true)
        )
    }

    @Test
    fun anInvalidCodeReadsDifferentlyFromAnInvalidAddress() {
        val code = AppError.InvalidCode("expired").playerMessage()
        val email = AppError.InvalidEmail("malformed").playerMessage()
        assertEquals("two different failures must not read identically", false, code == email)

        show(SignInState.EnteringCode(email = "player@example.com", error = AppError.InvalidCode("expired")))
        rule.onNodeWithText(code).assertIsDisplayed()
    }

    @Test
    fun anErrorOnTheAddressStepOffersAWayForward() {
        show(
            SignInState.EnteringEmail(
                email = "player@example.com",
                error = AppError.AuthUnavailable("provider disabled")
            )
        )
        // A stated failure with no next action is a dead end.
        rule.onNodeWithText("TRY AGAIN").assertIsDisplayed()
    }

    // --- the escape hatches actually fire ------------------------------------

    @Test
    fun resendAndChangeAddressAreWiredToRealCallbacks() {
        var resends = 0
        var changes = 0
        rule.setContent {
            MapMonarchsTheme {
                SignInScreen(
                    state = SignInState.EnteringCode(email = "player@example.com"),
                    onEmailChanged = {},
                    onCodeChanged = {},
                    onRequestCode = {},
                    onVerifyCode = {},
                    onResend = { resends++ },
                    onChangeEmail = { changes++ },
                    onBack = {}
                )
            }
        }
        rule.onNodeWithTag(SignInTags.RESEND).performClick()
        rule.onNodeWithTag(SignInTags.CHANGE_EMAIL).performClick()
        assertEquals(1, resends)
        assertEquals("a typo must be recoverable without restarting the app", 1, changes)
    }
}
