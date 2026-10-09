package com.metrolist.music.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.music.R
import com.metrolist.music.ui.menu.YouTubeSongMenu

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PodcastEpisodeItem(
    episode: EpisodeItem,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    isNew: Boolean = false,
    progress: Float? = null,
) {
    val menuState = LocalMenuState.current

    Box(modifier = modifier) {
        YouTubeListItem(
            item = episode,
            isActive = isActive,
            isPlaying = isPlaying,
            modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
            trailingContent = {
                if (isNew) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
                IconButton(onClick = {
                    menuState.show {
                        YouTubeSongMenu(episode.asSongItem(), menuState::dismiss)
                    }
                }) {
                    Icon(painterResource(R.drawable.more_vert), null)
                }
            },
        )
        progress?.let {
            LinearProgressIndicator(
                progress = { it },
                gapSize = 0.dp,
                drawStopIndicator = {},
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(2.dp),
            )
        }
    }
}
