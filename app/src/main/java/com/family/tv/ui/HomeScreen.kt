package com.family.tv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.family.tv.data.Channel
import com.family.tv.data.Section

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    channels: List<Channel>,
    lastVisitedSection: Section = Section.LIVE,
    strings: AppStrings,
    onSelectSection: (Section) -> Unit,
    onOpenSettings: () -> Unit,
    onSwitchSource: () -> Unit,
) {
    val liveCount = remember(channels) { channels.count { it.section == Section.LIVE } }
    val movieCount = remember(channels) { channels.count { it.section == Section.MOVIES } }
    val seriesCount = remember(channels) { channels.count { it.section == Section.SERIES } }

    val liveFocus = remember { FocusRequester() }
    val moviesFocus = remember { FocusRequester() }
    val seriesFocus = remember { FocusRequester() }

    LaunchedEffect(lastVisitedSection) {
        val target = when (lastVisitedSection) {
            Section.LIVE -> liveFocus
            Section.MOVIES -> moviesFocus
            Section.SERIES -> seriesFocus
        }
        runCatching { target.requestFocus() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E1015))
            .padding(horizontal = 56.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(strings.appTitle, fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Text(strings.chooseWhatToWatch, fontSize = 16.sp, color = Color(0xFF9AA1B2))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onOpenSettings) {
                    Text(strings.settings)
                }
                OutlinedButton(onClick = onSwitchSource) {
                    Text(strings.switchPlaylist)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(290.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            HubCard(
                title = strings.sectionTitle(Section.LIVE),
                subtitle = strings.channelsCount(liveCount),
                tag = strings.sectionTag(Section.LIVE),
                accent = Color(0xFFEF4444),
                onClick = { onSelectSection(Section.LIVE) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .focusRequester(liveFocus),
            )
            HubCard(
                title = strings.sectionTitle(Section.MOVIES),
                subtitle = strings.titlesCount(movieCount),
                tag = strings.sectionTag(Section.MOVIES),
                accent = Color(0xFF3B82F6),
                onClick = { onSelectSection(Section.MOVIES) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .focusRequester(moviesFocus),
            )
            HubCard(
                title = strings.sectionTitle(Section.SERIES),
                subtitle = strings.showsCount(seriesCount),
                tag = strings.sectionTag(Section.SERIES),
                accent = Color(0xFF10B981),
                onClick = { onSelectSection(Section.SERIES) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .focusRequester(seriesFocus),
            )
        }

        Text(strings.homeTip, fontSize = 14.sp, color = Color(0xFF7B8294))
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HubCard(
    title: String,
    subtitle: String,
    tag: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        scale = CardDefaults.scale(focusedScale = 1.05f),
        colors = CardDefaults.colors(containerColor = Color(0xFF181C26)),
        border = CardDefaults.border(
            focusedBorder = Border(
                BorderStroke(3.dp, MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(20.dp),
            )
        ),
        shape = CardDefaults.shape(RoundedCornerShape(20.dp)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(accent.copy(alpha = 0.22f), Color(0xFF171B26))
                    )
                )
                .padding(28.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(accent),
                )
                Text(tag, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent)
            }

            Column {
                Text(title, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(6.dp))
                Text(subtitle, fontSize = 17.sp, color = Color(0xFFB3BAC8))
            }
        }
    }
}