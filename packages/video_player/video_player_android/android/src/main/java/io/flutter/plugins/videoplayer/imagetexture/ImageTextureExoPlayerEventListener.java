// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.videoplayer.imagetexture;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.Format;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlayer;
import io.flutter.plugins.videoplayer.ExoPlayerEventListener;
import io.flutter.plugins.videoplayer.VideoPlayerCallbacks;
import java.util.Objects;

/** Translates ExoPlayer events and resizes the native image render target. */
final class ImageTextureExoPlayerEventListener extends ExoPlayerEventListener {
  /** Receives video dimensions reported by ExoPlayer. */
  interface VideoSizeListener {
    void onVideoSizeChanged(int width, int height);
  }

  @NonNull private final VideoSizeListener videoSizeListener;

  ImageTextureExoPlayerEventListener(
      @NonNull ExoPlayer exoPlayer,
      @NonNull VideoPlayerCallbacks events,
      @NonNull VideoSizeListener videoSizeListener) {
    super(exoPlayer, events);
    this.videoSizeListener = videoSizeListener;
  }

  @Override
  public void onVideoSizeChanged(@NonNull VideoSize videoSize) {
    videoSizeListener.onVideoSizeChanged(videoSize.width, videoSize.height);
  }

  @Override
  protected void sendInitialized() {
    VideoSize videoSize = exoPlayer.getVideoSize();
    int width = videoSize.width;
    int height = videoSize.height;
    int rotationCorrection = 0;

    if (width != 0 && height != 0) {
      videoSizeListener.onVideoSizeChanged(width, height);
      int rawVideoFormatRotation = getRotationCorrectionFromFormat(exoPlayer);
      try {
        rotationCorrection = RotationDegrees.fromDegrees(rawVideoFormatRotation).getDegrees();
      } catch (IllegalArgumentException exception) {
        // Ignore unexpected rotation metadata and render without a correction.
      }
    }
    events.onInitialized(width, height, exoPlayer.getDuration(), rotationCorrection);
  }

  @OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
  private static int getRotationCorrectionFromFormat(@NonNull ExoPlayer exoPlayer) {
    Format videoFormat = Objects.requireNonNull(exoPlayer.getVideoFormat());
    return videoFormat.rotationDegrees;
  }
}
