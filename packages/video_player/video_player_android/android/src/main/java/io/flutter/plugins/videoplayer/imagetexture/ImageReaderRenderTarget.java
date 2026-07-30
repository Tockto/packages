// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.videoplayer.imagetexture;

import android.graphics.ImageFormat;
import android.hardware.HardwareBuffer;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import io.flutter.Log;
import io.flutter.view.TextureRegistry.ImageTextureEntry;

/**
 * Relays the latest image from an {@link ImageReader} to a Flutter {@link ImageTextureEntry}.
 *
 * <p>The image texture takes ownership of every image passed to {@link
 * ImageTextureEntry#pushImage(Image)}. Images that cannot be transferred are closed here.
 */
@RequiresApi(29)
public final class ImageReaderRenderTarget implements ImageTextureVideoPlayer.RenderTarget {
  private static final String TAG = "ImageReaderRenderTarget";
  private static final int MAX_IMAGES = 4;

  /** Creates an ImageReader and attaches its image listener. */
  @VisibleForTesting
  interface ImageReaderFactory {
    @NonNull
    ImageReader create(
        int width,
        int height,
        @NonNull ImageReader.OnImageAvailableListener listener,
        @NonNull Handler handler);
  }

  private final Object lock = new Object();
  @NonNull private final ImageTextureEntry textureEntry;
  @NonNull private final ImageReaderFactory imageReaderFactory;
  @NonNull private final Handler callbackHandler;
  @NonNull private final Runnable stopCallbackThread;
  private final ImageReader.OnImageAvailableListener imageAvailableListener =
      this::onImageAvailable;

  private ImageReader imageReader;
  private int width;
  private int height;
  private boolean released;

  /** Creates a render target whose image callbacks run on a dedicated thread. */
  public ImageReaderRenderTarget(@NonNull ImageTextureEntry textureEntry) {
    this(textureEntry, createCallbackThread());
  }

  private ImageReaderRenderTarget(
      @NonNull ImageTextureEntry textureEntry, @NonNull CallbackThread callbackThread) {
    this(
        textureEntry,
        ImageReaderRenderTarget::createImageReader,
        callbackThread.handler,
        callbackThread.thread::quitSafely);
  }

  @VisibleForTesting
  ImageReaderRenderTarget(
      @NonNull ImageTextureEntry textureEntry,
      @NonNull ImageReaderFactory imageReaderFactory,
      @NonNull Handler callbackHandler,
      @NonNull Runnable stopCallbackThread) {
    this.textureEntry = textureEntry;
    this.imageReaderFactory = imageReaderFactory;
    this.callbackHandler = callbackHandler;
    this.stopCallbackThread = stopCallbackThread;
  }

  @NonNull
  private static CallbackThread createCallbackThread() {
    HandlerThread thread = new HandlerThread("video_player_image_texture");
    thread.start();
    return new CallbackThread(thread, new Handler(thread.getLooper()));
  }

  @NonNull
  private static ImageReader createImageReader(
      int width,
      int height,
      @NonNull ImageReader.OnImageAvailableListener listener,
      @NonNull Handler handler) {
    ImageReader reader =
        ImageReader.newInstance(
            width, height, ImageFormat.PRIVATE, MAX_IMAGES, HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE);
    reader.setOnImageAvailableListener(listener, handler);
    return reader;
  }

  @Override
  public void resize(
      int newWidth, int newHeight, @NonNull ImageTextureVideoPlayer.SurfaceSetter surfaceSetter) {
    if (newWidth <= 0 || newHeight <= 0) {
      return;
    }

    final ImageReader replacement;
    synchronized (lock) {
      if (released || (imageReader != null && width == newWidth && height == newHeight)) {
        return;
      }
    }

    replacement =
        imageReaderFactory.create(newWidth, newHeight, imageAvailableListener, callbackHandler);

    final ImageReader previous;
    try {
      synchronized (lock) {
        if (released) {
          closeReader(replacement);
          return;
        }
        if (imageReader != null && width == newWidth && height == newHeight) {
          closeReader(replacement);
          return;
        }

        // Switch ExoPlayer before retiring its old target. Keeping this operation under the same
        // lock as image delivery prevents a new-size image from being cleared below.
        surfaceSetter.setSurface(replacement.getSurface());
        textureEntry.pushImage(null);
        previous = imageReader;
        imageReader = replacement;
        width = newWidth;
        height = newHeight;
      }
    } catch (RuntimeException exception) {
      closeReader(replacement);
      throw exception;
    }
    closeReader(previous);
  }

  private void onImageAvailable(@NonNull ImageReader reader) {
    Image image = null;
    boolean transferred = false;
    try {
      image = reader.acquireLatestImage();
      if (image == null) {
        return;
      }
      synchronized (lock) {
        if (!released && reader == imageReader) {
          textureEntry.pushImage(image);
          transferred = true;
        }
      }
    } catch (IllegalStateException exception) {
      Log.e(TAG, "Unable to acquire the latest video image.", exception);
    } finally {
      if (!transferred && image != null) {
        image.close();
      }
    }
  }

  @Override
  public void release() {
    final ImageReader readerToClose;
    synchronized (lock) {
      if (released) {
        return;
      }
      released = true;
      readerToClose = imageReader;
      imageReader = null;
      width = 0;
      height = 0;
      // This makes the texture entry close the last image whose ownership was transferred.
      textureEntry.pushImage(null);
    }
    closeReader(readerToClose);
    stopCallbackThread.run();
  }

  private static void closeReader(ImageReader reader) {
    if (reader == null) {
      return;
    }
    reader.setOnImageAvailableListener(null, null);
    reader.close();
  }

  private static final class CallbackThread {
    @NonNull final HandlerThread thread;
    @NonNull final Handler handler;

    CallbackThread(@NonNull HandlerThread thread, @NonNull Handler handler) {
      this.thread = thread;
      this.handler = handler;
    }
  }
}
