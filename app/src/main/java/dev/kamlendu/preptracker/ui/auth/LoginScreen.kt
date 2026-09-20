package dev.kamlendu.preptracker.ui.auth

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.kamlendu.preptracker.appContainer
import dev.kamlendu.preptracker.sync.SyncApi
import dev.kamlendu.preptracker.sync.SyncEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginUi(val busy: Boolean = false, val error: String? = null)

class LoginViewModel(app: Application) : AndroidViewModel(app) {
    private val container = app.appContainer
    private val _ui = MutableStateFlow(LoginUi())
    val ui = _ui.asStateFlow()

    fun signIn(email: String, password: String) =
        authenticate { SyncApi.login(SyncApi.DEFAULT_BASE_URL, email.trim(), password) }

    fun signUp(name: String, email: String, password: String) =
        authenticate {
            SyncApi.register(SyncApi.DEFAULT_BASE_URL, name.trim(), email.trim(), password)
        }

    /**
     * Signing in and signing up differ only in which request is sent; what follows — storing the
     * account and pulling whatever is already on the server — is the same, and on a reinstall that
     * pull is the moment the history comes back.
     */
    private fun authenticate(request: suspend () -> SyncApi.Result<SyncApi.LoginResponse>) {
        if (_ui.value.busy) return
        _ui.value = LoginUi(busy = true)
        viewModelScope.launch {
            when (val result = request()) {
                is SyncApi.Result.Failed -> _ui.value = LoginUi(error = result.message)
                is SyncApi.Result.Ok -> {
                    val value = result.value
                    container.auth.signIn(value.token, value.userId, value.email, value.name)
                    SyncEngine.syncNow(getApplication())
                    _ui.value = LoginUi()
                }
            }
        }
    }
}

@Composable
fun LoginScreen(
    onBack: () -> Unit = {},
    onCreateInstead: () -> Unit = {},
    viewModel: LoginViewModel = viewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.padding(bottom = 8.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Text("Welcome back", style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            "The same account you use on the GATE platform. Your hours and spending are kept " +
                "there too, so they survive reinstalling the app.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            enabled = !ui.busy,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            enabled = !ui.busy,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        if (ui.error != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                ui.error!!,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { viewModel.signIn(email, password) },
            enabled = !ui.busy && email.isNotBlank() && password.isNotBlank(),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
            if (ui.busy) {
                // The button stays busy through the first sync, not just the sign-in request —
                // otherwise the app opens on an empty dashboard while the history is still landing.
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("Sign in")
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "New here?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onCreateInstead) { Text("Create an account") }
        }
    }
}
