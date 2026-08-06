package com.mapmonarchs.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.mapmonarchs.R
import com.mapmonarchs.feature.common.localizedMessage
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
            // A live spinner ON the button while the request is out, not a greyed
            // control -- the round-trip is acknowledged by the control itself. The
            // label still reads SENDING, so the reason survives for a screen reader.
            state.sending -> ActionState.Loading
            !state.email.looksLikeEmail() -> ActionState.Unavailable(stringResource(R.string.signin_reason_need_email))
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

    CodeInput(
        value = state.code,
        onValueChange = onCodeChanged,
        onDone = onVerifyCode,
        modifier = Modifier.padding(top = 16.dp)
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
            state.verifying -> ActionState.Loading
            !state.code.looksLikeCode() -> ActionState.Unavailable(stringResource(R.string.signin_reason_need_code))
            else -> ActionState.Ready
        },
        modifier = Modifier
            .padding(top = 20.dp)
            .testTag(SignInTags.PRIMARY)
    )

    SectionHeader(stringResource(R.string.signin_no_code))
    BodyNote(stringResource(R.string.signin_no_code_body))
    if (state.resendInSeconds > 0) {
        // A calm timer, not a dead button: the control becomes a quiet countdown
        // that says exactly when it returns, so the player waits instead of jabbing
        // RESEND into the backend's rate limit.
        BodyNote(
            stringResource(R.string.signin_resend_in, state.resendInSeconds),
            modifier = Modifier
                .padding(top = 8.dp)
                .testTag(SignInTags.RESEND)
        )
    } else {
        TextCommand(
            label = stringResource(R.string.signin_resend_code),
            onClick = onResend,
            modifier = Modifier
                .padding(top = 4.dp)
                .offset(x = (-10).dp)
                .testTag(SignInTags.RESEND)
        )
    }
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

private const val CODE_LEN = 6

/**
 * A six-cell code entry.
 *
 * One hidden BasicTextField backs six visible cells, so the platform's own paste,
 * one-time-code autofill, and keyboard all work while the app draws its own
 * hairline cells in the product's geometry. Typing auto-advances -- the lit cell
 * is wherever the next digit lands -- and pasting a code fills every cell at once.
 * Input is digit-only and capped at six; the keyboard's Done verifies when the
 * six are complete, so a player never has to reach for the CONFIRM button.
 */
@Composable
private fun CodeInput(
    value: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    BasicTextField(
        value = value,
        onValueChange = { raw -> onValueChange(raw.filter(Char::isDigit).take(CODE_LEN)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { if (value.length == CODE_LEN) onDone() }),
        // The caret is the lit cell, not a blinking bar drawn across the row.
        cursorBrush = SolidColor(Color.Transparent),
        textStyle = MaterialTheme.typography.titleLarge.copy(color = Color.Transparent),
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focus)
            .testTag(SignInTags.CODE_FIELD),
        decorationBox = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                repeat(CODE_LEN) { i ->
                    CodeCell(
                        char = value.getOrNull(i)?.toString() ?: "",
                        active = i == value.length,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    )
    // Open the keyboard straight onto the code, so a player pasting from their
    // mail app does not have to tap the field first.
    LaunchedEffect(Unit) { focus.requestFocus() }
}

@Composable
private fun CodeCell(char: String, active: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Slate)
            .border(
                width = if (active) 1.5.dp else 1.dp,
                color = if (active) Sovereign else SlateEdge,
                shape = RoundedCornerShape(10.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = char,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
    }
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

