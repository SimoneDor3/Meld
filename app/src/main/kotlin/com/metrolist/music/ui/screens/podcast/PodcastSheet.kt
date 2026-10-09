package com.metrolist.music.ui.screens.podcast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.models.toHomeEpisode
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.PodcastEpisodeItem
import com.metrolist.music.ui.menu.YouTubeSongMenu
import com.metrolist.music.viewmodels.OnlinePodcastViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PodcastSheet(
    podcastId: String,
    onDismiss: () -> Unit,
    onOpenPage: () -> Unit,
) {
    val viewModel = hiltViewModel<OnlinePodcastViewModel>(key = "podcast_sheet_$podcastId")
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current

    val podcast by viewModel.podcast.collectAsStateWithLifecycle()
    val episodes by viewModel.episodes.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val libraryPodcast by viewModel.libraryPodcast.collectAsStateWithLifecycle()
    val isPlaying = playerConnection?.isEffectivelyPlaying?.collectAsStateWithLifecycle()?.value == true
    val playingId = playerConnection?.mediaMetadata?.collectAsStateWithLifecycle()?.value?.id

    LaunchedEffect(podcastId) { viewModel.load(podcastId) }

    val localSongs by remember(episodes) {
        viewModel.database.songEntitiesByIds(episodes.map { it.id })
    }.collectAsStateWithLifecycle(emptyList())
    val listeningState = remember(episodes, localSongs) {
        val localById = localSongs.associateBy { it.id }
        episodes.associate { it.id to it.asSongItem().toHomeEpisode(localById[it.id]) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(modifier = Modifier.navigationBarsPadding()) {
            val podcastItem = podcast
            if (podcastItem != null) {
                item(key = "header") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                model = podcastItem.thumbnail,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(MaterialTheme.shapes.medium),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = podcastItem.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                podcastItem.author?.name?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val subscribed = libraryPodcast?.inLibrary == true
                            FilledTonalButton(onClick = viewModel::toggleSubscription) {
                                Text(stringResource(if (subscribed) R.string.subscribed else R.string.subscribe))
                            }
                            TextButton(onClick = onOpenPage) {
                                Text(stringResource(R.string.view_podcast))
                            }
                        }
                    }
                }
            }

            when {
                podcast == null && isLoading -> item(key = "loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        ContainedLoadingIndicator()
                    }
                }

                error != null -> item(key = "error") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = error ?: stringResource(R.string.error_unknown),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                        )
                        Button(onClick = viewModel::retry) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }

                else -> itemsIndexed(
                    items = episodes,
                    key = { _, episode -> episode.id },
                ) { index, episode ->
                    val state = listeningState[episode.id]
                    PodcastEpisodeItem(
                        episode = episode,
                        isActive = playingId == episode.id,
                        isPlaying = isPlaying,
                        isNew = state?.isNew == true,
                        progress = state?.progress,
                        onClick = {
                            if (episode.id == playingId) {
                                playerConnection?.togglePlayPause()
                            } else {
                                playerConnection?.playQueue(
                                    ListQueue(
                                        title = podcast?.title,
                                        items = episodes.map { it.toMediaMetadata().toMediaItem() },
                                        startIndex = index,
                                    ),
                                )
                            }
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show {
                                YouTubeSongMenu(episode.asSongItem(), menuState::dismiss)
                            }
                        },
                    )
                }
            }
        }
    }
}
