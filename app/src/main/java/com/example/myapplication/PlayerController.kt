package com.example.myapplication

import android.content.Context
import android.util.Log
import android.view.SurfaceView
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.TrackGroup
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionOverrides
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.util.EventLogger
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

private const val TAG = "PlayerController"

private const val stream1 =
    "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_ts/master.m3u8"
private const val stream2 =
    //"https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_4x3/bipbop_4x3_variant.m3u8"
    stream1

private const val nextStreamDelayMs = 1000L

class PlayerController(private val context: Context) {
    private var player: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null
    private var mediaSource: ConcatenatingMediaSource? = null
    private var coroutineScope: CoroutineScope? = null

    private var seekedNearEnd = false
    private var addedNextStream = false

    fun start(surfaceView: SurfaceView) {
        Log.d(TAG, "start() called with: surfaceView = $surfaceView")

        stop()
        coroutineScope = MainScope()

        val trackSelector = DefaultTrackSelector(context)
        this.trackSelector = trackSelector
        val player = ExoPlayer.Builder(context).setTrackSelector(trackSelector).build()
        this.player = player

        player.addAnalyticsListener(EventLogger(trackSelector))
        player.addListener(Listener())

        val mediaSource = ConcatenatingMediaSource(createHlsMediaSource(stream1))
        this.mediaSource = mediaSource
        player.setMediaSource(mediaSource)

        player.setVideoSurfaceView(surfaceView)

        player.playWhenReady = true
        player.prepare()
    }

    private fun createHlsMediaSource(url: String): HlsMediaSource {
        Log.d(TAG, "createHlsMediaSource() called with: url = $url")
        return HlsMediaSource.Factory(DefaultDataSource.Factory(context))
            .createMediaSource(MediaItem.fromUri(url))
    }

    fun stop() {
        Log.d(TAG, "stop() called")
        player?.apply {
            stop()
            clearMediaItems()
            release()
        }
        player = null
        trackSelector = null
        mediaSource = null
        coroutineScope?.cancel()
        coroutineScope = null
        addedNextStream = false
        seekedNearEnd = false
    }

    private fun selectSubtitleTrack(tracksInfo: TracksInfo) {
        val trackSelector = this.trackSelector ?: return

        val textGroupInfos = tracksInfo.trackGroupInfos
            .asSequence()
            .filter { it.trackType == C.TRACK_TYPE_TEXT && it.isSupported }
            .toList()
        Log.d(TAG, "selectSubtitleTrack: textGroupInfos = $textGroupInfos")
        if (textGroupInfos.isEmpty()) {
            Log.d(TAG, "selectSubtitleTrack: no text tracks, return")
            return
        }

        var overriddenTrack = false
        for (trackGroupInfo in textGroupInfos) {
            val track = trackGroupInfo.trackGroup
                .asSequence()
                .withIndex()
                .find { (trackIndex, format) ->
                    trackGroupInfo.isTrackSupported(trackIndex) && format.language == "en"
                }
            if (track != null) {
                val (trackIndex, format) = track
                Log.d(TAG, "selectSubtitleTrack: overriding track ${Format.toLogString(format)}")

                trackSelector.setParameters(trackSelector.buildUponParameters()
                    .setTrackSelectionOverrides(
                        trackSelector.parameters.trackSelectionOverrides.buildUpon()
                            .apply {
                                clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                addOverride(
                                    TrackSelectionOverrides.TrackSelectionOverride(
                                        trackGroupInfo.trackGroup,
                                        listOf(trackIndex)
                                    )
                                )
                            }
                            .build()
                    ))
                overriddenTrack = true

                break
            }
        }

        if (!overriddenTrack) {
            Log.d(TAG, "selectSubtitleTrack: disable all text track groups")
            trackSelector.setParameters(
                trackSelector.buildUponParameters().setTrackSelectionOverrides(
                    trackSelector.parameters.trackSelectionOverrides.buildUpon()
                        .apply {
                            clearOverridesOfType(C.TRACK_TYPE_TEXT)
                            textGroupInfos.forEach {
                                addOverride(
                                    TrackSelectionOverrides.TrackSelectionOverride(
                                        it.trackGroup,
                                        emptyList()
                                    )
                                )
                            }
                        }
                        .build()
                )
            )
        }

        seekNearEnd()
    }

    private fun seekNearEnd() {
        if (seekedNearEnd) return
        seekedNearEnd = true

        val player = checkNotNull(player)
        val duration = player.contentDuration
        if (duration != C.TIME_UNSET) {
            Log.d(TAG, "seekNearEnd: duration = $duration")
            val position = duration - TimeUnit.SECONDS.toMillis(30)
            if (position > 0) {
                Log.d(TAG, "seekNearEnd: seeking to $position")
                player.seekTo(position)
            }
        }
    }

    private fun addNextStream() {
        if (addedNextStream) return
        addedNextStream = true
        checkNotNull(coroutineScope).launch {
            delay(nextStreamDelayMs)
            Log.d(TAG, "addNextStream: adding next stream")
            checkNotNull(mediaSource).addMediaSource(createHlsMediaSource(stream2))
        }
    }

    private inner class Listener : Player.Listener {
        private var lastTracksInfo: TracksInfo? = null

        override fun onTracksInfoChanged(tracksInfo: TracksInfo) {
            super.onTracksInfoChanged(tracksInfo)
            if (tracksInfo != lastTracksInfo) {
                lastTracksInfo = tracksInfo
                selectSubtitleTrack(tracksInfo)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            super.onPlayerError(error)
            stop()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                addNextStream()
            }
        }

    }
}

private fun TrackGroup.asSequence(): Sequence<Format> =
    (0 until length).asSequence().map { getFormat(it) }
