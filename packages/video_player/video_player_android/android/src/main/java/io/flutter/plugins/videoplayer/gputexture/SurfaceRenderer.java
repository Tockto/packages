package io.flutter.plugins.videoplayer.gputexture;

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;

import io.flutter.plugins.videoplayer.gputexture.rendering.CanvasQuad;
import io.flutter.plugins.videoplayer.gputexture.rendering.Utils;

public class SurfaceRenderer implements SurfaceTexture.OnFrameAvailableListener {
  private static final String TAG = "SurfaceRenderer";

  private static final int[] EGL_CONFIG_ATTRIBS = {
    EGL14.EGL_RED_SIZE, 8,
    EGL14.EGL_GREEN_SIZE, 8,
    EGL14.EGL_BLUE_SIZE, 8,
    EGL14.EGL_ALPHA_SIZE, 8,
    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
    EGL14.EGL_NONE
  };

  private static final int[] EGL_CONTEXT_ATTRIBS = {
    EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
    EGL14.EGL_NONE
  };

  private static final float[] IDENTITY_MATRIX = {
    1.0f, 0.0f, 0.0f, 0.0f,
    0.0f, 1.0f, 0.0f, 0.0f,
    0.0f, 0.0f, 1.0f, 0.0f,
    0.0f, 0.0f, 0.0f, 1.0f
  };

  private Surface targetSurface;
  private SurfaceTexture sourceSurfaceTexture;
  private Surface inputSurface;
  private HandlerThread renderThread;
  private Handler renderHandler;
  private CanvasQuad canvasQuad;

  // EGL objects
  private EGLDisplay eglDisplay = EGL14.EGL_NO_DISPLAY;
  private EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
  private EGLSurface eglSurface = EGL14.EGL_NO_SURFACE;
  private EGLContext renderContext = EGL14.EGL_NO_CONTEXT;

  private volatile boolean isReleased = false;
  private boolean isInitialized = false;
  private final Object surfaceLock = new Object(); // Add synchronization for surface updates

  public void initialize(Surface surface) {
    if (isInitialized) return;

    targetSurface = surface;

    try {
      setupEGL();
      setupTexture();
      setupRenderThread();
      isInitialized = true;
    } catch (Exception e) {
      Log.e(TAG, "Initialization failed", e);
      cleanup();
      throw new RuntimeException("SurfaceRenderer initialization failed", e);
    }
  }

  private void setupEGL() {
    eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
    if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
      throw new RuntimeException("Unable to get EGL display");
    }

    int[] version = new int[2];
    if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
      throw new RuntimeException("Unable to initialize EGL");
    }

    EGLConfig config = chooseEGLConfig();
    eglContext = createEGLContext(config, EGL14.EGL_NO_CONTEXT);
    renderContext = createEGLContext(config, eglContext);
    eglSurface = createEGLSurface(config);

    makeCurrent(eglContext);
    setupViewport();
  }

  private EGLConfig chooseEGLConfig() {
    EGLConfig[] configs = new EGLConfig[1];
    int[] numConfigs = new int[1];
    if (!EGL14.eglChooseConfig(eglDisplay, EGL_CONFIG_ATTRIBS, 0, configs, 0, 1, numConfigs, 0)) {
      throw new RuntimeException("Unable to find suitable EGL config");
    }
    return configs[0];
  }

  private EGLContext createEGLContext(EGLConfig config, EGLContext sharedContext) {
    EGLContext context = EGL14.eglCreateContext(eglDisplay, config, sharedContext, EGL_CONTEXT_ATTRIBS, 0);
    if (context == EGL14.EGL_NO_CONTEXT) {
      throw new RuntimeException("Failed to create EGL context");
    }
    return context;
  }

  private EGLSurface createEGLSurface(EGLConfig config) {
    EGLSurface surface = EGL14.eglCreateWindowSurface(eglDisplay, config, targetSurface, new int[]{EGL14.EGL_NONE}, 0);
    if (surface == EGL14.EGL_NO_SURFACE) {
      throw new RuntimeException("Failed to create EGL surface");
    }
    return surface;
  }

  private void makeCurrent(EGLContext context) {
    if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, context)) {
      throw new RuntimeException("Failed to make EGL context current");
    }
  }

  private int[] getSurfaceSize() {
    int[] size = new int[2];
    EGL14.eglQuerySurface(eglDisplay, eglSurface, EGL14.EGL_WIDTH, size, 0);
    EGL14.eglQuerySurface(eglDisplay, eglSurface, EGL14.EGL_HEIGHT, size, 1);
    return size;
  }

  private void setupViewport() {
    int[] size = getSurfaceSize();
    GLES20.glViewport(0, 0, size[0], size[1]);
  }

  private void setupTexture() {
    int textureId = Utils.glCreateExternalTexture();
    sourceSurfaceTexture = new SurfaceTexture(textureId);

    int[] size = getSurfaceSize();
    sourceSurfaceTexture.setDefaultBufferSize(size[0], size[1]);

    canvasQuad = CanvasQuad.createCanvasQuad();
    canvasQuad.glInit(textureId);

    inputSurface = new Surface(sourceSurfaceTexture);
  }

  private void setupRenderThread() {
    EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);

    renderThread = new HandlerThread("SurfaceRenderer");
    renderThread.start();
    renderHandler = new Handler(renderThread.getLooper());

    sourceSurfaceTexture.setOnFrameAvailableListener(this, renderHandler);
  }

  public Surface getInputSurface() {
    return inputSurface;
  }

  /**
   * Update the target surface, typically called when the surface is resized
   */
  public void updateSurface(Surface newSurface) {
    if (isReleased || !isInitialized) return;

    synchronized (surfaceLock) {
      if (newSurface == null || newSurface.equals(targetSurface)) return;

      targetSurface = newSurface;
      recreateEGLSurface();
    }
  }

  private void recreateEGLSurface() {
    if (eglDisplay == EGL14.EGL_NO_DISPLAY) return;

    // Get the current EGL config
    EGLConfig config = getCurrentEGLConfig();

    // Destroy old surface
    if (eglSurface != EGL14.EGL_NO_SURFACE) {
      EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
      EGL14.eglDestroySurface(eglDisplay, eglSurface);
    }

    // Create new surface with updated target
    eglSurface = createEGLSurface(config);

    // Update surface texture buffer size to match new surface
    updateSurfaceTextureSize();

    Log.d(TAG, "EGL surface recreated for new target surface");
  }

  private EGLConfig getCurrentEGLConfig() {
    // Query current config from the context
    EGLConfig[] configs = new EGLConfig[1];
    int[] numConfigs = new int[1];
    if (!EGL14.eglChooseConfig(eglDisplay, EGL_CONFIG_ATTRIBS, 0, configs, 0, 1, numConfigs, 0)) {
      throw new RuntimeException("Unable to find EGL config during surface update");
    }
    return configs[0];
  }

  private void updateSurfaceTextureSize() {
    if (sourceSurfaceTexture != null) {
      int[] size = getSurfaceSize();
      sourceSurfaceTexture.setDefaultBufferSize(size[0], size[1]);
    }
  }

  @Override
  public void onFrameAvailable(SurfaceTexture surfaceTexture) {
    if (!isReleased && renderHandler != null) {
      renderHandler.post(this::renderFrame);
    }
  }

  private void renderFrame() {
    if (isReleased || sourceSurfaceTexture == null || canvasQuad == null) return;

    synchronized (surfaceLock) {
      try {
        makeCurrent(renderContext);
        setupViewport();

        sourceSurfaceTexture.updateTexImage();

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        canvasQuad.glDraw(IDENTITY_MATRIX);
        Utils.checkGlError();

        EGL14.eglSwapBuffers(eglDisplay, eglSurface);
      } catch (Exception e) {
        Log.e(TAG, "Error rendering frame", e);
      }
    }
  }

  public void release() {
    if (isReleased) return;
    isReleased = true;

    if (renderThread != null) {
      renderThread.quitSafely();
      try {
        renderThread.join(1000); // Add timeout
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    cleanup();
  }

  private void cleanup() {
    // Clean up surfaces first
    if (inputSurface != null) {
      inputSurface.release();
      inputSurface = null;
    }

    if (sourceSurfaceTexture != null) {
      sourceSurfaceTexture.release();
      sourceSurfaceTexture = null;
    }

    // Clean up GL resources
    if (canvasQuad != null) {
      canvasQuad.glShutdown();
      canvasQuad = null;
    }

    // Clean up EGL resources in proper order
    if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
      EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);

      if (eglSurface != EGL14.EGL_NO_SURFACE) {
        EGL14.eglDestroySurface(eglDisplay, eglSurface);
        eglSurface = EGL14.EGL_NO_SURFACE;
      }

      if (renderContext != EGL14.EGL_NO_CONTEXT) {
        EGL14.eglDestroyContext(eglDisplay, renderContext);
        renderContext = EGL14.EGL_NO_CONTEXT;
      }

      if (eglContext != EGL14.EGL_NO_CONTEXT) {
        EGL14.eglDestroyContext(eglDisplay, eglContext);
        eglContext = EGL14.EGL_NO_CONTEXT;
      }

      EGL14.eglTerminate(eglDisplay);
      eglDisplay = EGL14.EGL_NO_DISPLAY;
    }

    // Clean up thread references
    renderHandler = null;
    renderThread = null;
    targetSurface = null;
    isInitialized = false;
  }
}

