// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.videoplayer.imagetexture;

import static org.mockito.Mockito.*;

import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.view.Surface;
import io.flutter.view.TextureRegistry.ImageTextureEntry;
import org.junit.Before;
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
public final class ImageReaderRenderTargetTest {
  @Mock private ImageTextureEntry mockTextureEntry;
  @Mock private ImageReaderRenderTarget.ImageReaderFactory mockReaderFactory;
  @Mock private Handler mockHandler;
  @Mock private Runnable mockStopCallbackThread;
  @Mock private ImageReader mockFirstReader;
  @Mock private ImageReader mockSecondReader;
  @Mock private Surface mockFirstSurface;
  @Mock private Surface mockSecondSurface;
  @Mock private ImageTextureVideoPlayer.SurfaceSetter mockSurfaceSetter;

  @Rule public MockitoRule initRule = MockitoJUnit.rule();

  @Before
  public void setUp() {
    when(mockReaderFactory.create(anyInt(), anyInt(), any(), same(mockHandler)))
        .thenReturn(mockFirstReader, mockSecondReader);
    when(mockFirstReader.getSurface()).thenReturn(mockFirstSurface);
    when(mockSecondReader.getSurface()).thenReturn(mockSecondSurface);
  }

  private ImageReaderRenderTarget createRenderTarget() {
    return new ImageReaderRenderTarget(
        mockTextureEntry, mockReaderFactory, mockHandler, mockStopCallbackThread);
  }

  @Test
  public void resizeSwitchesSurfaceBeforeClosingPreviousReader() {
    ImageReaderRenderTarget target = createRenderTarget();

    target.resize(640, 360, mockSurfaceSetter);
    target.resize(1280, 720, mockSurfaceSetter);

    InOrder inOrder = inOrder(mockSurfaceSetter, mockFirstReader);
    inOrder.verify(mockSurfaceSetter).setSurface(mockFirstSurface);
    inOrder.verify(mockSurfaceSetter).setSurface(mockSecondSurface);
    inOrder.verify(mockFirstReader).close();
    verify(mockReaderFactory).create(eq(640), eq(360), any(), same(mockHandler));
    verify(mockReaderFactory).create(eq(1280), eq(720), any(), same(mockHandler));
    verify(mockTextureEntry, times(2)).pushImage(null);
  }

  @Test
  public void resizeWithUnchangedDimensionsKeepsReaderAndSurface() {
    ImageReaderRenderTarget target = createRenderTarget();

    target.resize(640, 360, mockSurfaceSetter);
    target.resize(640, 360, mockSurfaceSetter);

    verify(mockReaderFactory, times(1)).create(anyInt(), anyInt(), any(), any());
    verify(mockSurfaceSetter, times(1)).setSurface(mockFirstSurface);
    verify(mockFirstReader, never()).close();
  }

  @Test
  public void callbackTransfersLatestImageWithoutClosingIt() {
    ImageReaderRenderTarget target = createRenderTarget();
    target.resize(640, 360, mockSurfaceSetter);
    Image image = mock(Image.class);
    when(mockFirstReader.acquireLatestImage()).thenReturn(image);
    ImageReader.OnImageAvailableListener listener = captureListener();

    listener.onImageAvailable(mockFirstReader);

    verify(mockTextureEntry).pushImage(image);
    verify(image, never()).close();
  }

  @Test
  public void callbackFromRetiredReaderClosesImageInsteadOfTransferringIt() {
    ImageReaderRenderTarget target = createRenderTarget();
    target.resize(640, 360, mockSurfaceSetter);
    ImageReader.OnImageAvailableListener listener = captureListener();
    target.resize(1280, 720, mockSurfaceSetter);
    Image retiredImage = mock(Image.class);
    when(mockFirstReader.acquireLatestImage()).thenReturn(retiredImage);
    clearInvocations(mockTextureEntry);

    listener.onImageAvailable(mockFirstReader);

    verify(retiredImage).close();
    verify(mockTextureEntry, never()).pushImage(retiredImage);
  }

  @Test
  public void releaseClearsImageAndPreventsLatePushes() {
    ImageReaderRenderTarget target = createRenderTarget();
    target.resize(640, 360, mockSurfaceSetter);
    ImageReader.OnImageAvailableListener listener = captureListener();
    Image lateImage = mock(Image.class);
    when(mockFirstReader.acquireLatestImage()).thenReturn(lateImage);
    clearInvocations(mockTextureEntry);

    target.release();
    target.release();
    listener.onImageAvailable(mockFirstReader);

    verify(mockTextureEntry, times(1)).pushImage(null);
    verify(mockTextureEntry, never()).pushImage(lateImage);
    verify(lateImage).close();
    verify(mockFirstReader, times(1)).close();
    verify(mockStopCallbackThread, times(1)).run();
  }

  private ImageReader.OnImageAvailableListener captureListener() {
    ArgumentCaptor<ImageReader.OnImageAvailableListener> listenerCaptor =
        ArgumentCaptor.forClass(ImageReader.OnImageAvailableListener.class);
    verify(mockReaderFactory, atLeastOnce())
        .create(anyInt(), anyInt(), listenerCaptor.capture(), same(mockHandler));
    return listenerCaptor.getValue();
  }
}
