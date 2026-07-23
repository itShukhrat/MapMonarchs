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
import androidx.compose.ui.res.stringResource
import com.mapmonarchs.R
import com.mapmonarchs.feature.common.localizedMessage
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mapmonarchs.ui.components.ActionState
import com.mapmonarchs.ui.components.BodyNote
import com.mapmonarchs.ui.components.CommandAction
import com.mapmonarchs.ui.components.CommandScreen
import com.mapmonarchs.ui.components.ErrorStrip
import com.mapmonarchs.ui.components.SectionHeader
import com.mapmonarchs.ui.components.TextCommand
import androidx.compose.foundation.layout.offset
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
    CommandScreen(
        title = stringResource(R.string.signin_title),
        onBack = onBack,
        modifier = modifier
    ) {
        when (state) {
            is SignInState.EnteringEmail -> EmailStep(state, onEmailChanged, onRequestCode)
            is SignInState.EnteringCode -> CodeStep(state, onCodeChanged, onVerifyCode, onResend, onChangeEmail)
            is SignInState.Complete -> BodyNote(stringResource(R.string.signin_complete))
        }
    }
}

@Composable
private fun EmailStep(
    state: SignInState.EnteringEmail,
    onEmailChanged: (String) -> Unit,
    onRequestCode: () -> Unit
) {
    SectionHeader(stringResource(R.string.signin_your_address))
    BodyNote(stringResource(R.string.signin_address_body))

    FieldBox(
        value = state.email,
        onValueChange = onEmailChanged,
        placeholder = stringResource(R.string.signin_email_placeholder),
        keyboardType = KeyboardType.Email,
        tag = SignInTags.EMAIL_FIELD
    )

    state.error?.let {
        ErrorStrip(
            message = it.localizedMessage(),
            onRetry = onRequestCode,
            retryLabel = stringResource(R.string.signin_try_again),
            modifier = Modifier
                .padding(top = 12.dp)
                .testTag(SignInTags.ERROR)
        )
    }

    CommandAction(
        label = stringResource(
            if (state.sending) R.string.signin_sending else R.string.signin_send_code
        ),
        onClick = onRequestCode,
        // The reason is stated rather than left to the dimming. A player using a
        // screen reader hears why the control will not respond; previously they
        // heard nothing at all and had no way to learn the field was the problem.
        state = when {
            state.sending -> ActionState.Unavailable("a code is already being sent")
            !state.email.looksLikeEmail() -> ActionState.Unavailable("enter an email address first")
            else -> ActionState.Ready
        },
        modifier = Modifier
            .padding(top = 20.dp)
            .testTag(SignInTags.PRIMARY)
    )

    SectionHeader(stringResource(R.string.signin_what_for))
    BodyNote(
        stringResource(R.string.signin_what_for_body),
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
    SectionHeader(stringResource(R.string.signin_enter_code))
    BodyNote(stringResource(R.string.signin_sent_to, state.email))

    FieldBox(
        value = state.code,
        onValueChange = onCodeChanged,
        placeholder = stringResource(R.string.signin_code_placeholder),
        keyboardType = KeyboardType.NumberPassword,
        tag = SignInTags.CODE_FIELD
    )

    state.error?.let {
        ErrorStrip(
            message = it.localizedMessage(),
            onRetry = onResend,
            retryLabel = stringResource(R.string.signin_resend),
            modifier = Modifier
                .padding(top = 12.dp)
                .testTag(SignInTags.ERROR)
        )
    }

    if (state.resent) {
        BodyNote(stringResource(R.string.signin_resent), modifier = Modifier.padding(top = 10.dp))
    }

    CommandAction(
        label = stringResource(
            if (state.verifying) R.string.signin_verifying else R.string.signin_confirm
        ),
        onClick = onVerifyCode,
        state = when {
            state.verifying -> ActionState.Unavailable("checking the code")
            !state.code.looksLikeCode() -> ActionState.Unavailable("enter all six digits")
            else -> ActionState.Ready
        },
        modifier = Modifier
            .padding(top = 20.dp)
            .testTag(SignInTags.PRIMARY)
    )

    SectionHeader(stringResource(R.string.signin_no_code))
    BodyNote(stringResource(R.string.signin_no_code_body))
    TextCommand(
        label = stringResource(R.string.signin_resend_code),
        onClick = onResend,
        modifier = Modifier
            .padding(top = 4.dp)
            .offset(x = (-10).dp)
            .testTag(SignInTags.RESEND)
    )
    TextCommand(
        label = stringResource(R.string.signin_different_address),
        onClick = onChangeEmail,
        color = TextMuted,
        modifier = Modifier
            .padding(top = 4.dp)
            .offset(x = (-10).dp)
            .testTag(SignInTags.CHANGE_EMAIL)
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

