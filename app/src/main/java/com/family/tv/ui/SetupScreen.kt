package com.family.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.family.tv.data.Source

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SetupScreen(state: UiState, strings: AppStrings, onLoad: (Source) -> Unit) {
    var url by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E1015))
            .padding(48.dp),
        horizontalArrangement = Arrangement.spacedBy(36.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color(0xFF151821), RoundedCornerShape(20.dp))
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(strings.appTitle, fontSize = 38.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(strings.setupSubtitle, fontSize = 15.sp, color = Color(0xFF9AA1B2))

            if (state.loading) {
                Text(
                    text = strings.loadingPlaylist,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E2638), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                )
            }

            state.error?.let {
                Text(
                    text = "Error: $it",
                    fontSize = 15.sp,
                    color = Color(0xFFFF8A80),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF2D191E), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                )
            }

            Spacer(Modifier.height(4.dp))
            Text(strings.savedPlaylists, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White)

            state.saved.forEach { s ->
                Button(onClick = { onLoad(s) }, modifier = Modifier.fillMaxWidth()) {
                    Text(s.label(), maxLines = 1)
                }
            }

            OutlinedButton(onClick = { onLoad(Source.Demo) }, modifier = Modifier.fillMaxWidth()) {
                Text(strings.demoPlaylist)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color(0xFF151821), RoundedCornerShape(20.dp))
                .verticalScroll(rememberScrollState())
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(strings.xtreamLogin, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Field(strings.serverLabel, host) { host = it }
            Field(strings.usernameLabel, user) { user = it }
            Field(strings.passwordLabel, pass, password = true) { pass = it }
            Button(
                onClick = {
                    if (host.isNotBlank() && user.isNotBlank()) {
                        onLoad(Source.Xtream(host.trim(), user.trim(), pass.trim()))
                    }
                }
            ) {
                Text(strings.connect)
            }

            Spacer(Modifier.height(12.dp))
            Text(strings.orM3u, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Field("M3U URL", url) { url = it }
            Button(onClick = { if (url.isNotBlank()) onLoad(Source.M3u(url.trim())) }) {
                Text(strings.loadUrl)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Field(label: String, value: String, password: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        label = { androidx.compose.material3.Text(label, color = Color(0xFF9AA1B2)) },
        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = Color(0xFF2D3343),
            focusedContainerColor = Color(0xFF10131B),
            unfocusedContainerColor = Color(0xFF10131B),
        ),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
    )
}