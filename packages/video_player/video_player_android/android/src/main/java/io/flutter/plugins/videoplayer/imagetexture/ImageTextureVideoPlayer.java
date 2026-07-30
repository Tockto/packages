// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.videoplayer.imagetexture;

import android.content.Context;
import android.view.Surface;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import io.flutter.plugins.videoplayer.ExoPlayerEventListener;
import io.flutter.plugins.videoplayer.VideoAsset;
import io.flutter.plugins.videoplayer.VideoPlayer;
import io.flutter.plugins.videoplayer.VideoPlayerCallbacks;
import io.flutter.plugins.videoplayer.VideoPlayerOptions;
import io.flutter.view.TextureRegistry.ImageTextureEntry;
import io.flutter.view.TextureRegistry.SurfaceProducer;

/**
 * A video player that decodes into a private {@code ImageReader} surface and forwards the images to
 * a Flutter image texture.
 */
@RequiresApi(29)
public final class ImageTextureVideoPlayer extends VideoPlayer {
  /** Sets ExoPlayer's output surface during a render-target replacement. */
  interface SurfaceSetter {
    void setSurface(@NonNull Surface surface);
  }

  /** The native render target used by this player. */
  interface RenderTarget {
    void resize(int width, int height, @NonNull SurfaceSetter surfaceSetter);

    void release();
  }

  @NonNull private final ImageTextureEntry textureEntry;
  @NonNull private final RenderTarget renderTarget;
  private boolean disposed;

  /** Creates an image texture video player. */
  @UnstableApi
  @NonNull
  public static ImageTextureVideoPlayer create(
      @NonNull Context context,
      @NonNull VideoPlayerCallbacks events,
      @NonNull VideoAsset asset,
      @NonNull VideoPlayerOptions options,
      @NonNull ImageTextureEntry textureEntry) {
    RenderTarget renderTarget = new ImageReaderRenderTarget(textureEntry);
    try {
      return new ImageTextureVideoPlayer(
          events,
          textureEntry,
          renderTarget,
          asset.getMediaItem(),
          options,
          () -> {
            ExoPlayer.Builder builder = new ExoPlayer.Builder(context);
            if (options.backBufferDurationMs != null) {
              if (options.backBufferDurationMs < 0) {
                throw new IllegalArgumentException("backBufferDurationMs must be at least 0");
              }
              if (options.backBufferDurationMs > 0) {
                int backBufferInt =
                    (int) Math.min(options.backBufferDurationMs.longValue(), Integer.MAX_VALUE);
                DefaultLoadControl loadControl =
                    new DefaultLoadControl.Builder()
                        .setBackBuffer(backBufferInt, /* retainBackBufferFromKeyframe= */ true)
                        .build();
                builder.setLoadControl(loadControl);
              }
            }
            DefaultTrackSelector trackSelector = new DefaultTrackSelector(context);
            builder
                .setTrackSelector(trackSelector)
                .setMediaSourceFactory(asset.getMediaSourceFactory(context));
            return builder.build();
          });
    } catch (RuntimeException exception) {
      renderTarget.release();
      throw exception;
    }
  }

  @UnstableApi
  @VisibleForTesting
  ImageTextureVideoPlayer(
      @NonNull VideoPlayerCallbacks events,
      @NonNull ImageTextureEntry textureEntry,
      @NonNull RenderTarget renderTarget,
      @NonNull MediaItem mediaItem,
      @NonNull VideoPlayerOptions options,
      @NonNull ExoPlayerProvider exoPlayerProvider) {
    super(events, mediaItem, options, /* surfaceProducer= */ null, exoPlayerProvider);
    this.textureEntry = textureEntry;
    this.renderTarget = renderTarget;
    initializeEventListener();
  }

  @NonNull
  @Override
  protected ExoPlayerEventListener createExoPlayerEventListener(
      @NonNull ExoPlayer exoPlayer, @Nullable SurfaceProducer surfaceProducer) {
    return new ImageTextureExoPlayerEventListener(
        exoPlayer,
        videoPlayerEvents,
        (width, height) -> renderTarget.resize(width, height, exoPlayer::setVideoSurface));
  }

  @Override
  public void dispose() {
    if (disposed) {
      return;
    }
    disposed = true;
    try {
      // Release the producer before its private decoding surface or consumer.
      super.dispose();
    } finally {
      try {
        renderTarget.release();
      } finally {
        textureEntry.release();
      }
    }
  }
}
