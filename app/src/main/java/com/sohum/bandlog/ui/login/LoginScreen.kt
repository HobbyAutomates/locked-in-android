package com.sohum.bandlog.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.SupabaseAuth
import com.sohum.bandlog.ui.components.ErrorNote
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onSignedIn: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var creating by rememberSaveable { mutableStateOf(false) }
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var info by mutableStateOf<String?>(null)

    fun submit() {
        if (busy) return
        scope.launch {
            busy = true; error = null; info = null
            try {
                if (creating) {
                    val active = SupabaseAuth.signUp(email, password)
                    if (active) onSignedIn() else info = "Account created. Confirm the email we sent, then sign in."
                } else {
                    SupabaseAuth.signIn(email, password); onSignedIn()
                }
            } catch (e: Exception) { error = e.message } finally { busy = false }
        }
    }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("LOCKED IN", fontSize = 11.sp, fontWeight = FontWeight(700), letterSpacing = 1.5.sp, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Text(if (creating) "Create account" else "Sign in", fontSize = 30.sp, fontWeight = FontWeight(800), letterSpacing = (-1).sp)
        Text("One sign-in on this phone. You'll stay logged in.", color = cs.onSurfaceVariant, fontSize = 13.sp)
        Spacer(Modifier.height(22.dp))
        OutlinedTextField(
            email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = androidx.compose.ui.text.input.ImeAction.Next),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
        )
        Spacer(Modifier.height(12.dp))
        ErrorNote(error)
        if (info != null) Text(info!!, color = cs.tertiary, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        Button(onClick = { submit() }, Modifier.fillMaxWidth().height(50.dp), enabled = !busy && email.isNotBlank() && password.length >= 6) {
            if (busy) CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp, color = cs.onPrimary)
            else Text(if (creating) "Create account" else "Sign in", fontWeight = FontWeight(700))
        }
        TextButton(onClick = { creating = !creating; error = null; info = null }, Modifier.align(Alignment.CenterHorizontally)) {
            Text(if (creating) "Have an account? Sign in" else "New here? Create an account", color = cs.onSurfaceVariant)
        }
    }
}
