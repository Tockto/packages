// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.videoplayer.imagetexture;

import static org.mockito.Mockito.*;

import android.view.Surface;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlayer;
import io.flutter.plugins.videoplayer.VideoPlayerCallbacks;
import io.flutter.plugins.videoplayer.VideoPlayerOptions;
import io.flutter.view.TextureRegistry.ImageTextureEntry;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@Config(sdk = 29)
@RunWith(RobolectricTestRunner.class)
public final class ImageTextureVideoPlayerTest {
  private static final String FAKE_ASSET_URL = "https://flutter.dev/movie.mp4";

  @Mock private VideoPlayerCallbacks mockEvents;
  @Mock private ImageTextureEntry mockTextureEntry;
  @Mock private ImageTextureVideoPlayer.RenderTarget mockRenderTarget;
  @Mock private ExoPlayer mockExoPlayer;

  @Rule public MockitoRule initRule = MockitoJUnit.rule();

  private ImageTextureVideoPlayer createPlayer() {
    return new ImageTextureVideoPlayer(
        mockEvents,
        mockTextureEntry,
        mockRenderTarget,
        new MediaItem.Builder().setUri(FAKE_ASSET_URL).build(),
        new VideoPlayerOptions(),
        () -> mockExoPlayer);
  }

  @Test
  public void videoSizeChangeResizesTargetAndWiresItsSurfaceToExoPlayer() {
    ImageTextureVideoPlayer player = createPlayer();
    ArgumentCaptor<Player.Listener> listenerCaptor = ArgumentCaptor.forClass(Player.Listener.class);
    verify(mockExoPlayer, times(1)).addListener(listenerCaptor.capture());

    listenerCaptor.getValue().onVideoSizeChanged(new VideoSize(640, 360));

    ArgumentCaptor<ImageTextureVideoPlayer.SurfaceSetter> setterCaptor =
        ArgumentCaptor.forClass(ImageTextureVideoPlayer.SurfaceSetter.class);
    verify(mockRenderTarget).resize(eq(640), eq(360), setterCaptor.capture());
    Surface surface = mock(Surface.class);
    setterCaptor.getValue().setSurface(surface);
    verify(mockExoPlayer).setVideoSurface(surface);

    player.dispose();
  }

  @Test
  public void initializedEventPreservesRotationCorrection() {
    ImageTextureVideoPlayer player = createPlayer();
    ArgumentCaptor<Player.Listener> listenerCaptor = ArgumentCaptor.forClass(Player.Listener.class);
    verify(mockExoPlayer).addListener(listenerCaptor.capture());
    when(mockExoPlayer.getVideoSize()).thenReturn(new VideoSize(800, 400));
    when(mockExoPlayer.getVideoFormat())
        .thenReturn(new Format.Builder().setRotationDegrees(90).build());
    when(mockExoPlayer.getDuration()).thenReturn(10L);

    listenerCaptor.getValue().onPlaybackStateChanged(Player.STATE_READY);

    verify(mockEvents).onInitialized(800, 400, 10L, 90);
    verify(mockRenderTarget).resize(eq(800), eq(400), any());
    player.dispose();
  }

  @Test
  public void disposeReleasesProducerThenTargetThenTextureAndIsIdempotent() {
    ImageTextureVideoPlayer player = createPlayer();

    player.dispose();
    player.dispose();

    InOrder inOrder = inOrder(mockExoPlayer, mockRenderTarget, mockTextureEntry);
    inOrder.verify(mockExoPlayer).release();
    inOrder.verify(mockRenderTarget).release();
    inOrder.verify(mockTextureEntry).release();
    verify(mockExoPlayer, times(1)).release();
    verify(mockRenderTarget, times(1)).release();
    verify(mockTextureEntry, times(1)).release();
  }
}
