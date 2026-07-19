package com.mapmonarchs.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mapmonarchs.ui.components.BodyNote
import com.mapmonarchs.ui.components.CommandAction
import com.mapmonarchs.ui.components.CommandScreen
import com.mapmonarchs.ui.components.ErrorStrip
import com.mapmonarchs.ui.components.SectionHeader
import com.mapmonarchs.ui.theme.Slate
import com.mapmonarchs.ui.theme.SlateEdge
import com.mapmonarchs.ui.theme.Sovereign
import com.mapmonarchs.ui.theme.SovereignGlow
import com.mapmonarchs.ui.theme.TextMuted
import com.mapmonarchs.ui.theme.TextPrimary

object SignInTags {
    const val EMAIL_FIELD = "signin_email"
    const val CODE_FIELD = "signin_code"
    const val PRIMARY = "signin_primary"
    const val RESEND = "signin_resend"
    const val CHANGE_EMAIL = "signin_change_email"
    const val ERROR = "signin_error"
    const val PROVIDER_NOTE = "signin_provider_note"
}

/**
 * PROBLEM SOLVED: territory has to belong to someone. Without a session the
 * server cannot attribute a claim, so nothing a player walks can be theirs.
 *
 * WHY NOT GENERIC: no password, no social buttons, no "create account" second
 * screen, no terms checkbox. One field at a time, and the copy states plainly
 * why an address is being asked for -- in a game about walking outdoors, an
 * unexplained login wall is where players leave.
 *
 * WHY A CODE AND NOT A LINK: a magic link needs deep-link infrastructure, a
 * server-side redirect, and App Links verification, and it bounces through a
 * browser that may not return. A six-digit code never leaves the app.
 */
@Composable
fun SignInScreen(
    state: SignInState,
    onEmailChanged: (String) -> Unit,
    onCodeChanged: (String) -> Unit,
    onRequestCode: () -> Unit,
    onVerifyCode: () -> Unit,
    onResend: () -> Unit,
    onChangeEmail: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    CommandScreen(title = "SIGN IN", onBack = onBack, modifier = modifier) {
        when (state) {
            is SignInState.EnteringEmail -> EmailStep(state, onEmailChanged, onRequestCode)
            is SignInState.EnteringCode -> CodeStep(state, onCodeChanged, onVerifyCode, onResend, onChangeEmail)
            is SignInState.Complete -> BodyNote("Signed in.")
        }
        Box(modifier = Modifier.padding(bottom = 40.dp))
    }
}

@Composable
private fun EmailStep(
    state: SignInState.EnteringEmail,
    onEmailChanged: (String) -> Unit,
    onRequestCode: () -> Unit
) {
    SectionHeader("YOUR ADDRESS")
    BodyNote(
        "Territory has to belong to someone. We send a six-digit code to sign you " +
            "in -- there is no password to remember and nothing to install."
    )

    FieldBox(
        value = state.email,
        onValueChange = onEmailChanged,
        placeholder = "you@example.com",
        keyboardType = KeyboardType.Email,
        tag = SignInTags.EMAIL_FIELD
    )

    state.error?.let {
        ErrorStrip(
            message = it.playerMessage(),
            onRetry = onRequestCode,
            retryLabel = "TRY AGAIN",
            modifier = Modifier
                .padding(top = 12.dp)
                .testTag(SignInTags.ERROR)
        )
    }

    CommandAction(
        label = if (state.sending) "SENDING" else "SEND CODE",
        onClick = onRequestCode,
        enabled = state.email.looksLikeEmail() && !state.sending,
        modifier = Modifier
            .padding(top = 20.dp)
            .testTag(SignInTags.PRIMARY)
    )

    SectionHeader("WHAT WE USE IT FOR")
    BodyNote(
        "Signing in. That is all. Your address is not shown to other players, not " +
            "used for marketing, and not linked to your location history.",
        modifier = Modifier.testTag(SignInTags.PROVIDER_NOTE)
    )
}

@Composable
private fun CodeStep(
    state: SignInState.EnteringCode,
    onCodeChanged: (String) -> Unit,
    onVerifyCode: () -> Unit,
    onResend: () -> Unit,
    onChangeEmail: () -> Unit
) {
    SectionHeader("ENTER THE CODE")
    BodyNote("Sent to ${state.email}. It expires shortly.")

    FieldBox(
        value = state.code,
        onValueChange = onCodeChanged,
        placeholder = "000000",
        keyboardType = KeyboardType.NumberPassword,
        tag = SignInTags.CODE_FIELD
    )

    state.error?.let {
        ErrorStrip(
            message = it.playerMessage(),
            onRetry = onResend,
            retryLabel = "RESEND",
            modifier = Modifier
                .padding(top = 12.dp)
                .testTag(SignInTags.ERROR)
        )
    }

    if (state.resent) {
        BodyNote("A new code has been sent.", modifier = Modifier.padding(top = 10.dp))
    }

    CommandAction(
        label = if (state.verifying) "VERIFYING" else "CONFIRM",
        onClick = onVerifyCode,
        enabled = state.code.looksLikeCode() && !state.verifying,
        modifier = Modifier
            .padding(top = 20.dp)
            .testTag(SignInTags.PRIMARY)
    )

    SectionHeader("NO CODE?")
    BodyNote(
        "Check spam. Delivery depends on the mail service this project is " +
            "configured with, so it can take a moment."
    )
    Text(
        text = "RESEND CODE",
        style = MaterialTheme.typography.labelMedium,
        color = SovereignGlow,
        modifier = Modifier
            .padding(top = 8.dp)
            .testTag(SignInTags.RESEND)
            .clickableText(onResend)
    )
    Text(
        text = "USE A DIFFERENT ADDRESS",
        style = MaterialTheme.typography.labelMedium,
        color = TextMuted,
        modifier = Modifier
            .padding(top = 16.dp)
            .testTag(SignInTags.CHANGE_EMAIL)
            .clickableText(onChangeEmail)
    )
}

/**
 * A single-line field with no Material decoration.
 *
 * OutlinedTextField brings a floating label, a container colour, and a focus
 * ripple that all read as stock Android. This is a hairline box with a Sovereign
 * cursor -- the same geometry as every other control in the app.
 */
@Composable
private fun FieldBox(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    tag: String
) {
    Box(
        modifier = Modifier
            .padding(top = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Slate)
            .border(1.dp, SlateEdge, RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
            cursorBrush = SolidColor(Sovereign),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(tag)
        )
    }
}

private fun Modifier.clickableText(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)