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

import static io.flutter.plugins.videoplayer.gputexture.rendering.Utils.checkGlError;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;

public class CanvasQuad extends Mesh {

    private static final String[] VERTEX_SHADER_CODE =
            new String[]{
                    "attribute vec4 aPosition;",
                    "attribute vec2 aTexCoords;",
                    "varying vec2 vTexCoords;",

                    // Standard transformation.
                    "void main() {",
                    "  gl_Position = aPosition;",
                    "  vTexCoords = aTexCoords;",
                    "}"
            };
    private static final String[] FRAGMENT_SHADER_CODE =
            new String[]{
                    // This is required since the texture data is GL_TEXTURE_EXTERNAL_OES.
                    "#extension GL_OES_EGL_image_external : require",
                    "precision mediump float;",

                    // Standard texture rendering shader.
                    "uniform samplerExternalOES uTexture;",
                    "varying vec2 vTexCoords;",
                    "void main() {",
                    "  gl_FragColor = texture2D(uTexture, vTexCoords);",
                    "}"
            };


    private static final int POSITION_COORDS_PER_VERTEX = 2;
    private static final int TEXTURE_COORDS_PER_VERTEX = 2;
    private static final int CPV = POSITION_COORDS_PER_VERTEX + TEXTURE_COORDS_PER_VERTEX;
    private static final int VERTEX_STRIDE_BYTES = CPV * Utils.BYTES_PER_FLOAT;

    public static CanvasQuad createCanvasQuad() {
        float width = 1.0f;
        float height = 1.0f;
        float[] vertexData = {
                -width, -height, 0, 1,
                width, -height, 1, 1,
                -width, height, 0, 0,
                width, height, 1, 0,
        };
        return new CanvasQuad(vertexData);
    }

    /**
     * Used by static constructors.
     */
    private CanvasQuad(float[] vertexData) {
        super(vertexData);
    }


    @Override
    public void glInit(int textureId) {
        this.textureId = textureId;

        program = Utils.compileProgram(VERTEX_SHADER_CODE, FRAGMENT_SHADER_CODE);
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition");
        texCoordsHandle = GLES20.glGetAttribLocation(program, "aTexCoords");
        textureHandle = GLES20.glGetUniformLocation(program, "uTexture");
    }

    @Override
    public void glDraw(float[] mvpMatrix) {
        // Configure shader.
        GLES20.glUseProgram(program);
        checkGlError();

        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glEnableVertexAttribArray(texCoordsHandle);
        checkGlError();

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
        GLES20.glUniform1i(textureHandle, 0);
        checkGlError();

        // Load position data.
        vertexBuffer.position(0);
        GLES20.glVertexAttribPointer(
                positionHandle,
                POSITION_COORDS_PER_VERTEX,
                GLES20.GL_FLOAT,
                false,
                VERTEX_STRIDE_BYTES,
                vertexBuffer);
        checkGlError();

        // Load texture data. Eye.Type.RIGHT uses the left eye's data.
        vertexBuffer.position(POSITION_COORDS_PER_VERTEX);
        GLES20.glVertexAttribPointer(
                texCoordsHandle,
                TEXTURE_COORDS_PER_VERTEX,
                GLES20.GL_FLOAT,
                false,
                VERTEX_STRIDE_BYTES,
                vertexBuffer);
        checkGlError();

        // Render.
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, vertices.length / CPV);
        checkGlError();

        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glDisableVertexAttribArray(texCoordsHandle);
    }
}
