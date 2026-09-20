package dev.kamlendu.preptracker.ui.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler

private enum class AuthStep { LANDING, SIGN_UP, SIGN_IN }

/**
 * The signed-out half of the app: what it does, then a way in.
 *
 * Both forms share one view model so an error from the server survives the frame it arrives on,
 * and the system back button returns to the landing page rather than leaving the app.
 */
@Composable
fun AuthFlow() {
    var step by rememberSaveable { mutableStateOf(AuthStep.LANDING) }

    BackHandler(enabled = step != AuthStep.LANDING) { step = AuthStep.LANDING }

    AnimatedContent(
        targetState = step,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "auth",
    ) { current ->
        when (current) {
            AuthStep.LANDING -> LandingScreen(
                onCreateAccount = { step = AuthStep.SIGN_UP },
                onSignIn = { step = AuthStep.SIGN_IN },
            )
            AuthStep.SIGN_UP -> SignUpScreen(
                onBack = { step = AuthStep.LANDING },
                onSignInInstead = { step = AuthStep.SIGN_IN },
            )
            AuthStep.SIGN_IN -> LoginScreen(
                onBack = { step = AuthStep.LANDING },
                onCreateInstead = { step = AuthStep.SIGN_UP },
            )
        }
    }
}
