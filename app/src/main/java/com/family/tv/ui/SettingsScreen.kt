package com.family.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.family.tv.BuildConfig
import com.family.tv.data.AppLanguage
import com.family.tv.data.Channel
import com.family.tv.data.PlaylistRepository
import com.family.tv.data.Section
import com.family.tv.data.SubtitleSize
import java.io.File

private enum class PinAction { NONE, CREATE_PIN, CHANGE_PIN, REMOVE_PIN, MANAGE_CATEGORIES }

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(
    strings: AppStrings,
    channels: List<Channel>,
    language: AppLanguage,
    subtitleSize: SubtitleSize,
    autoLogin: Boolean,
    parentalPin: String?,
    lockedCategories: Set<String>,
    updateState: UpdateState,
    onCheckUpdate: () -> Unit,
    onDownloadUpdate: (String) -> Unit,
    onInstallApk: (File) -> Unit,
    onSetLanguage: (AppLanguage) -> Unit,
    onSetSubtitleSize: (SubtitleSize) -> Unit,
    onSetAutoLogin: (Boolean) -> Unit,
    onSetParentalPin: (String?) -> Unit,
    onToggleLockedCategory: (Section, String) -> Unit,
    onClearHistory: () -> Unit,
    onBack: () -> Unit,
) {
    var clearedBanner by remember { mutableStateOf(false) }
    var pinAction by remember { mutableStateOf(PinAction.NONE) }
    var managingLocks by remember { mutableStateOf(false) }

    val allCategories = remember(channels) {
        channels.map { it.section to it.group }.distinct()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E1015)),
    ) {
        if (managingLocks) {
            BackHandler { managingLocks = false }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 56.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(strings.manageLockedCats, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.height(4.dp))
                        Text(strings.lockedCount(lockedCategories.size), fontSize = 15.sp, color = Color(0xFF9AA1B2))
                    }
                    OutlinedButton(onClick = { managingLocks = false }) {
                        Text(strings.back)
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(allCategories) { (sec, grp) ->
                        val lockKey = PlaylistRepository.categoryLockKey(sec, grp)
                        val isLocked = lockKey in lockedCategories
                        Surface(
                            onClick = { onToggleLockedCategory(sec, grp) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (isLocked) Color(0xFF2B1D26) else Color(0xFF151821),
                                focusedContainerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White,
                                focusedContentColor = Color(0xFF0B0D12),
                            ),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 22.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "${strings.sectionTitle(sec)}  •  $grp",
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = if (isLocked) "🔒" else "🔓",
                                    fontSize = 18.sp,
                                )
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 56.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(strings.settings, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(Modifier.height(4.dp))
                            Text(strings.settingsSubtitle, fontSize = 15.sp, color = Color(0xFF9AA1B2))
                        }
                        OutlinedButton(onClick = onBack) {
                            Text(strings.back)
                        }
                    }
                }

                item {
                    val updateDesc = when (updateState) {
                        UpdateState.Idle -> strings.currentVersionDesc(BuildConfig.VERSION_NAME)
                        UpdateState.Checking -> strings.checkingUpdates
                        UpdateState.UpToDate -> "${strings.currentVersionDesc(BuildConfig.VERSION_NAME)} • ${strings.upToDateDesc}"
                        is UpdateState.Available -> strings.updateAvailableDesc(updateState.versionName)
                        is UpdateState.Downloading -> strings.downloadingProgress(updateState.percent)
                        is UpdateState.ReadyToInstall -> if (updateState.needsPermission) {
                            strings.allowInstallHint
                        } else {
                            strings.currentVersionDesc(BuildConfig.VERSION_NAME)
                        }
                        is UpdateState.Error -> strings.updateErrorDesc(updateState.message)
                    }

                    SettingCard(
                        title = strings.updateTitle,
                        description = updateDesc,
                    ) {
                        when (updateState) {
                            UpdateState.Checking -> {
                                OutlinedButton(onClick = {}) {
                                    Text(strings.checkingUpdates)
                                }
                            }
                            is UpdateState.Available -> {
                                Button(onClick = { onDownloadUpdate(updateState.apkUrl) }) {
                                    Text(strings.installUpdateButton(updateState.versionName))
                                }
                            }
                            is UpdateState.Downloading -> {
                                OutlinedButton(onClick = {}) {
                                    Text(strings.downloadingProgress(updateState.percent))
                                }
                            }
                            is UpdateState.ReadyToInstall -> {
                                Button(onClick = { onInstallApk(updateState.apkFile) }) {
                                    Text(strings.installReadyButton)
                                }
                            }
                            else -> {
                                Button(onClick = onCheckUpdate) {
                                    Text(strings.checkUpdatesButton)
                                }
                            }
                        }
                    }
                }

                item {
                    SettingCard(
                        title = strings.parentalLockTitle,
                        description = if (parentalPin != null) {
                            "${strings.parentalLockDesc} (${strings.lockedCount(lockedCategories.size)})"
                        } else {
                            strings.parentalLockDesc
                        },
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (parentalPin == null) {
                                Button(onClick = { pinAction = PinAction.CREATE_PIN }) {
                                    Text(strings.setPin)
                                }
                            } else {
                                Button(onClick = { pinAction = PinAction.MANAGE_CATEGORIES }) {
                                    Text(strings.manageLockedCats)
                                }
                                OutlinedButton(onClick = { pinAction = PinAction.CHANGE_PIN }) {
                                    Text(strings.changePin)
                                }
                                OutlinedButton(onClick = { pinAction = PinAction.REMOVE_PIN }) {
                                    Text(strings.removePin)
                                }
                            }
                        }
                    }
                }

                item {
                    SettingCard(
                        title = strings.languageTitle,
                        description = strings.languageDesc,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AppLanguage.entries.forEach { lang ->
                                if (lang == language) {
                                    Button(onClick = { onSetLanguage(lang) }) {
                                        Text("✓ ${lang.displayName}")
                                    }
                                } else {
                                    OutlinedButton(onClick = { onSetLanguage(lang) }) {
                                        Text(lang.displayName)
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    SettingCard(
                        title = strings.subtitleSizeTitle,
                        description = strings.subtitleSizeDesc,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SubtitleSize.entries.forEach { size ->
                                if (size == subtitleSize) {
                                    Button(onClick = { onSetSubtitleSize(size) }) {
                                        Text("✓ ${strings.subtitleSizeLabel(size)}")
                                    }
                                } else {
                                    OutlinedButton(onClick = { onSetSubtitleSize(size) }) {
                                        Text(strings.subtitleSizeLabel(size))
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    SettingCard(
                        title = strings.autoLoginTitle,
                        description = strings.autoLoginDesc,
                    ) {
                        Button(onClick = { onSetAutoLogin(!autoLogin) }) {
                            Text(if (autoLogin) "✓ ${strings.enabled}" else strings.disabled)
                        }
                    }
                }

                item {
                    SettingCard(
                        title = strings.clearHistoryTitle,
                        description = if (clearedBanner) strings.clearedNotice else strings.clearHistoryDesc,
                    ) {
                        OutlinedButton(
                            onClick = {
                                onClearHistory()
                                clearedBanner = true
                            }
                        ) {
                            Text(strings.clearHistoryButton)
                        }
                    }
                }
            }
        }

        if (pinAction != PinAction.NONE) {
            val creating = pinAction == PinAction.CREATE_PIN
            PinDialog(
                title = if (creating) strings.createPinTitle else strings.enterPinTitle,
                strings = strings,
                expectedPin = if (creating) null else parentalPin,
                onSuccess = { enteredPin ->
                    when (pinAction) {
                        PinAction.CREATE_PIN -> onSetParentalPin(enteredPin)
                        PinAction.CHANGE_PIN -> {
                            pinAction = PinAction.CREATE_PIN
                            return@PinDialog
                        }
                        PinAction.REMOVE_PIN -> onSetParentalPin(null)
                        PinAction.MANAGE_CATEGORIES -> managingLocks = true
                        PinAction.NONE -> {}
                    }
                    pinAction = PinAction.NONE
                },
                onDismiss = { pinAction = PinAction.NONE },
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingCard(
    title: String,
    description: String,
    control: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF151821), RoundedCornerShape(18.dp))
            .padding(horizontal = 28.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 24.dp)) {
            Text(title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text(description, fontSize = 14.sp, color = Color(0xFF9AA1B2))
        }
        control()
    }
}