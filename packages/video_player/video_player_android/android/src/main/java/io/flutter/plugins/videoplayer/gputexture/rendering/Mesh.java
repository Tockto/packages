/*
 * Copyright 2017 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.flutter.plugins.videoplayer.gputexture.rendering;

import android.opengl.GLES20;

import java.nio.FloatBuffer;

/**
 * Utility class to generate & render spherical meshes for video or images. Use the static creation
 * methods to construct the Mesh's data. Then call the Mesh constructor on the GL thread when ready.
 * Use glDraw method to render it.
 */
public abstract class Mesh {

    // Vertices for the mesh with 3D position + left 2D texture UV + right 2D texture UV.
    public final float[] vertices;
    protected final FloatBuffer vertexBuffer;

    // Program related GL items. These are only valid if program != 0.
    protected int program;
    protected int mvpMatrixHandle;
    protected int positionHandle;
    protected int texCoordsHandle;
    protected int textureHandle;
    protected int textureId;


    /**
     * Used by static constructors.
     */
    protected Mesh(float[] vertexData) {
        vertices = vertexData;
        vertexBuffer = Utils.createBuffer(vertices);
    }

    /**
     * Finishes initialization of the GL components.
     *
     * @param textureId GL_TEXTURE_EXTERNAL_OES used for this mesh.
     */
    public abstract void glInit(int textureId);

    /**
     * Renders the mesh. This must be called on the GL thread.
     *
     * @param mvpMatrix The Model View Projection matrix.
     */
    public abstract void glDraw(float[] mvpMatrix);

    /**
     * Cleans up the GL resources.
     */
    public void glShutdown() {
        if (program != 0) {
            GLES20.glDeleteProgram(program);
            GLES20.glDeleteTextures(1, new int[]{textureId}, 0);
        }
    }

}
