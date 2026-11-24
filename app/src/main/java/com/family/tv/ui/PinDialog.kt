package com.family.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PinDialog(
    title: String,
    strings: AppStrings,
    expectedPin: String?, // null = creating a new 4-digit PIN
    onSuccess: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val firstDigitFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { firstDigitFocus.requestFocus() }
    }

    BackHandler { onDismiss() }

    fun pressDigit(d: String) {
        if (entered.length >= 4) return
        error = false
        val next = entered + d
        entered = next
        if (next.length == 4) {
            if (expectedPin == null || next == expectedPin) {
                onSuccess(next)
            } else {
                error = true
                entered = ""
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(360.dp)
                .background(Color(0xFF171B26), RoundedCornerShape(22.dp))
                .border(1.dp, Color(0xFF2B3245), RoundedCornerShape(22.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "🔒  $title",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )

            if (error) {
                Text(
                    text = strings.wrongPin,
                    fontSize = 14.sp,
                    color = Color(0xFFFF8A80),
                )
            }

            // 4 PIN Dots
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(4) { idx ->
                    val filled = idx < entered.length
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(
                                color = if (filled) MaterialTheme.colorScheme.primary else Color(0xFF282F42),
                                shape = CircleShape,
                            )
                    )
                }
            }

            val rows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("⌫", "0", "✕"),
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEachIndexed { rIdx, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEachIndexed { cIdx, key ->
                            val mod = if (rIdx == 0 && cIdx == 0) {
                                Modifier.size(84.dp, 54.dp).focusRequester(firstDigitFocus)
                            } else {
                                Modifier.size(84.dp, 54.dp)
                            }
                            Surface(
                                onClick = {
                                    when (key) {
                                        "⌫" -> if (entered.isNotEmpty()) entered = entered.dropLast(1)
                                        "✕" -> onDismiss()
                                        else -> pressDigit(key)
                                    }
                                },
                                modifier = mod,
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = Color(0xFF222838),
                                    focusedContainerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = Color.White,
                                    focusedContentColor = Color(0xFF0B0D12),
                                ),
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = key,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}