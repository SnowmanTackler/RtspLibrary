package com.potterhsu.rtsplibrary.demo;

import android.app.Activity;
import android.graphics.Rect;
import android.opengl.GLES31;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;

import com.potterhsu.rtsplibrary.NativeCallback;
import com.potterhsu.rtsplibrary.RtspClient;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Date;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MainActivity extends Activity {

    public static final String TAG = MainActivity.class.getSimpleName();

    GLSurfaceView surfaceView;
    private BackgroundReceiver backgroundListener;
    private final RendererGL rendererGL = new RendererGL();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.surfaceView = findViewById(R.id.surface_view);
        this.surfaceView.setEGLContextClientVersion(3);
        this.surfaceView.setRenderer(this.rendererGL);
        this.surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        this.surfaceView.requestRender();
    }


    @Override
    protected void onResume() {
        super.onResume();
        this.backgroundListener = new BackgroundReceiver(this.rendererGL, this.surfaceView);
        new Thread(this.backgroundListener).start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        this.backgroundListener.stop();
    }


    private static Rect centerIn(Size inner, Size outer) {
        float aspectInner = inner.getWidth() / inner.getHeight();
        float aspectOuter = outer.getWidth() / outer.getHeight();

        if (aspectInner > aspectOuter) {
            // inside is "wider" than outside
            int newInnerHeight = ((outer.getWidth() * inner.getHeight()) / inner.getWidth());
            int newInnerY = (outer.getHeight() - newInnerHeight) / 2;
            return new Rect(0, newInnerY, outer.getWidth(), newInnerY + newInnerHeight);
        } else if (aspectOuter > aspectInner) {
            // inside is "taller" than outside
            int newInnerWidth = ((outer.getHeight() * inner.getWidth()) / inner.getHeight());
            int newInnerX = (outer.getWidth() - newInnerWidth) / 2;
            return new Rect(newInnerX, 0, newInnerX + newInnerWidth, outer.getHeight());
        } else { // Equal aspect ratios
            return new Rect(0, 0, outer.getWidth(), outer.getHeight());
        }
    }

    private static Buffer floatArrayToBuffer(float[] floats) {
        ByteBuffer bb = ByteBuffer.allocateDirect(floats.length * 4);
        bb.order(ByteOrder.nativeOrder()); // use the device hardware's native byte order
        FloatBuffer fb = bb.asFloatBuffer(); // create a floating point buffer from the ByteBuffer
        fb.put(floats); // add the coordinates to the FloatBuffer
        fb.position(0); // set the buffer to read the first coordinate
        return bb;
    }

    private static int loadShader(String source, int iType) {

        if (source == null) {
            return 0;
        }

        int[] compiled = new int[1];
        int location = GLES31.glCreateShader(iType);
        GLES31.glShaderSource(location, source);
        GLES31.glCompileShader(location);
        GLES31.glGetShaderiv(location, GLES31.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.e("Shader", "Load failed: \n" + GLES31.glGetShaderInfoLog(location));
            return 0;
        }
        return location;
    }


    static class BackgroundListener implements NativeCallback {

        float period = 0;
        Date lastDate = new Date();

        private final RendererGL rendererGL;
        private final GLSurfaceView surfaceGL;

        BackgroundListener(RendererGL rendererGL, GLSurfaceView surfaceGL) {
            this.rendererGL = rendererGL;
            this.surfaceGL = surfaceGL;
        }

        @Override
        public void onFrame(byte[] frame, int nChannel, int width, int height) {
            Date nowDate = new Date();
            float alpha = 0.0f;
            period = period * alpha + (1- alpha) * (nowDate.getTime() - lastDate.getTime());
            lastDate = nowDate;
            int fps = (int) (1000 / period);

            Log.d(TAG, String.format("onFrame: nChannel = %d, width = %d, height = %d, fps = %d", nChannel, width, height, fps));

            this.rendererGL.update(frame, width, height);
            this.surfaceGL.requestRender();

                /*
                int area = width * height;
                int pixels[] = new int[area];
                for (int i = 0; i < area; i++) {
                    int r = frame[3 * i];
                    int g = frame[3 * i + 1];
                    int b = frame[3 * i + 2];
                    if (r < 0) r += 255;
                    if (g < 0) g += 255;
                    if (b < 0) b += 255;
                    pixels[i] = Color.rgb(r, g, b);
                }

                final Bitmap bmp = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);


                ivPreview.post(new Runnable() {
                    @Override
                    public void run() {
                        ivPreview.setImageBitmap(bmp);
                    }
                });
                */
        }
    }

    static class BackgroundReceiver implements Runnable {

        private final RtspClient rtspClient;
        private volatile boolean exit = false;

        BackgroundReceiver(RendererGL rendererGL, GLSurfaceView surfaceGL) {
            this.rtspClient = new RtspClient(new BackgroundListener(rendererGL, surfaceGL));
        }

        public void run() {
            while (!exit) {
                if (rtspClient.play("rtsp://192.168.80.3:8554/h264.sdp", 42630, 42632) == 0) {
                    break;
                }
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        void stop(){
            exit = true;
            this.rtspClient.stop();
            this.rtspClient.dispose();

        }
    }

    private static class Texture2D {

        private int imageWidth = 0;
        private int imageHeight = 0;
        private int imageFormat = 0;
        private int imageType = 0;

        int location = -1;

        /**
         * Will create a location if one doesn't exist
         */
        private int getLocation() {

            if (this.location < 0) {
                int[] textureHandleArray = new int[1];
                GLES31.glGenTextures(1, textureHandleArray, 0);
                this.location = textureHandleArray[0];
            }
            return this.location;
        }

        private boolean shouldRemakeBuffer(int width, int height, int glFormat, int glType) {
            if ((this.imageHeight != height) ||
                (this.imageWidth != width) ||
                (this.imageFormat != glFormat) ||
                (this.imageType != glType)) {

                this.imageHeight = height;
                this.imageWidth = width;
                this.imageFormat = glFormat;
                this.imageType = glType;
                return true;

            } else {

                return false;

            }
        }

        private void setTextureParameters() {
            GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_MIN_FILTER, GLES31.GL_NEAREST);
            GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_MAG_FILTER, GLES31.GL_NEAREST);
            GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_WRAP_S, GLES31.GL_CLAMP_TO_EDGE);
            GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_WRAP_T, GLES31.GL_CLAMP_TO_EDGE);
        }

        /**
         * OpenGL docs say this won't work for GL_LUMINANCE internal format, there is some question about
         * what to do if we every get there.
         */
        private void update(byte[] data, int width, int height, int glFormat) {

            if (glFormat == GLES31.GL_LUMINANCE) {
                Log.w("Image Format", "Potentially unsupported");
            }

            int loc = this.getLocation();
            int glType = GLES31.GL_UNSIGNED_BYTE;

            GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, loc);

            if (this.shouldRemakeBuffer(width, height, glFormat, glType)) {
                this.setTextureParameters();
                GLES31.glTexImage2D(
                        GLES31.GL_TEXTURE_2D,
                        0,
                        glFormat,
                        width,
                        height,
                        0,
                        glFormat,
                        glType,
                        ByteBuffer.wrap(data));
            } else {
                // Data of correct size was sent to graphics card already, so we just update the
                // current image rather than sending a new one.
                GLES31.glTexSubImage2D(
                        GLES31.GL_TEXTURE_2D,
                        0,
                        0, // x offset
                        0, // y offset
                        width,
                        height,
                        glFormat,
                        glType,
                        ByteBuffer.wrap(data));
            }

            GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, 0);
        }
    }

    private static class RendererGL implements GLSurfaceView.Renderer {

        private boolean setupComplete = false;
        private Size windowSize = new Size(0, 0);

        private final Object synchronizeOnThis = new Object();

        int glProgram;
        private int glAttributePosition;
        private int glAttributeTexturePosition;
        private int glUniformImage;
        private int glUniformMatrixProjectionAndView;
        private final float[] mvp = new float[16];

        private Texture2D texture2D = new Texture2D();

        private byte[] newData = null;
        private int newHeight = 0;
        private int newWidth = 0;
        private boolean newValid = false;

        RendererGL() {

        }

        private void update(byte[] data, int width, int height) {
            synchronized (this.synchronizeOnThis) {
                if (this.newData == null) {
                    this.newData = new byte[data.length];
                } else if (this.newData.length != data.length) {
                    this.newData = new byte[data.length];
                }
                this.newHeight = height;
                this.newWidth = width;
                System.arraycopy(data, 0, this.newData, 0, data.length);
                this.newValid = true;
            }
        }

        @Override
        public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {

            GLES31.glClearColor(1, 0, 0, 0); // Red, will show if we don't setup correctly
            GLES31.glDisable(GLES31.GL_DEPTH_TEST);

            String fragString =
                    "#version 300 es                                                    \n" +
                    "                                                                   \n" +
                    "/**                                                                \n" +
                    " * Fragment shader for drawing single images!                      \n" +
                    " */                                                                \n" +
                    "                                                                   \n" +
                    "// Textures for images to be drawn                                 \n" +
                    "uniform sampler2D image;                                           \n" +
                    "                                                                   \n" +
                    "// texturePosition is the XYZ position we'll sample our texture at \n" +
                    "in mediump vec2 texturePositionO;                                  \n" +
                    "                                                                   \n" +
                    "// Output of the frag shader is frag color.                        \n" +
                    "out mediump vec4 fragColor;                                        \n" +
                    "                                                                   \n" +
                    "void main() {                                                      \n" +
                    "    mediump vec4 temp = texture(image, texturePositionO);          \n" +
                    "                                                                   \n" +
                    "    fragColor = vec4(temp.r, temp.g, temp.b, temp.a);              \n" +
                    "}                                                                  \n" +
                    "                                                                   \n" +
                    "                                                                   \n";

            String vertString =
                    "#version 300 es                                                    \n" +
                    "                                                                   \n" +
                    "/**                                                                \n" +
                    " * Vertex shader for drawing single images!                        \n" +
                    " */                                                                \n" +
                    "                                                                   \n" +
                    "// camera_projection_matrix * camera_T_view                        \n" +
                    "uniform mat4 matrixProjectionAndView;                              \n" +
                    "                                                                   \n" +
                    "// Position is the XYZ position of the vertex with respect to the  \n" +
                    "// OpenGL camera                                                   \n" +
                    "in vec3 position;                                                  \n" +
                    "                                                                   \n" +
                    "// texturePosition is the XYZ position we'll sample our texture at \n" +
                    "in vec2 texturePosition;                                           \n" +
                    "                                                                   \n" +
                    "// texturePosition is the XYZ position we'll sample our texture at,\n" +
                    "// passed to fragment shader                                       \n" +
                    "out vec2 texturePositionO;                                         \n" +
                    "                                                                   \n" +
                    "void main() {                                                      \n" +
                    "    gl_Position = matrixProjectionAndView * vec4(position, 1);     \n" +
                    "    texturePositionO = texturePosition;                            \n" +
                    "}                                                                  \n" +
                    "                                                                   \n" +
                    "                                                                   \n";

            int vert = loadShader(vertString, GLES31.GL_VERTEX_SHADER);
            if (vert == 0) {
                return;
            }
            int frag = loadShader(fragString, GLES31.GL_FRAGMENT_SHADER);
            if (frag == 0) {
                return;
            }

            int location = GLES31.glCreateProgram();
            int[] link = new int[1];

            GLES31.glAttachShader(location, vert);
            GLES31.glAttachShader(location, frag);
            GLES31.glLinkProgram(location);
            GLES31.glGetProgramiv(location, GLES31.GL_LINK_STATUS, link, 0);
            GLES31.glDeleteShader(vert);
            GLES31.glDeleteShader(frag);

            if (link[0] <= 0) {
                Log.e("Program", "Linking failed");
                GLES31.glDeleteProgram(location);
                return;
            }

            this.glProgram = location;
            this.glAttributePosition = GLES31.glGetAttribLocation(location, "position");
            this.glAttributeTexturePosition = GLES31.glGetAttribLocation(location, "texturePosition");
            this.glUniformImage = GLES31.glGetUniformLocation(location, "image");
            this.glUniformMatrixProjectionAndView = GLES31.glGetUniformLocation(location, "matrixProjectionAndView");

            for (int i : new int[] {
                    this.glAttributePosition,
                    this.glAttributeTexturePosition,
                    this.glUniformImage,
                    this.glUniformMatrixProjectionAndView}) {
                if (i == -1) {
                    // GLSL Error Value For UniformLocation
                    return;
                }
            }

            setupComplete = true;
            GLES31.glClearColor(0f, 0f, 0f, 0f); // Black
        }

        @Override
        public void onSurfaceChanged(GL10 gl10, int width, int height) {
            GLES31.glViewport(0, 0, width, height);
            synchronized(this.synchronizeOnThis) {
                this.windowSize = new Size(width, height);
                Matrix.orthoM(this.mvp, 0, 0, width, height, 0, -1, 1);
            }
        }

        @Override
        public void onDrawFrame(GL10 gl10) {

            GLES31.glClear(GLES31.GL_COLOR_BUFFER_BIT);

            if (!setupComplete) {
                return;
            }

            // We don't want to update any data during the draw routine, so we lock on our self.
            // This will delay any synchronized methods from running until this block finishes
            synchronized(this.synchronizeOnThis) {

                if (this.newValid) {
                    this.texture2D.update(this.newData, this.newWidth, this.newHeight, GLES31.GL_RGB);
                    this.newValid = false;
                }

                if (this.windowSize.getWidth() == 0) return;
                if (this.windowSize.getHeight() == 0) return;
                if (this.texture2D.imageWidth == 0) return;
                if (this.texture2D.imageHeight == 0) return;

                Rect rect = centerIn(new Size(this.texture2D.imageWidth, this.texture2D.imageHeight), windowSize);

                GLES31.glUseProgram(this.glProgram);

                GLES31.glActiveTexture(GLES31.GL_TEXTURE0);
                GLES31.glUniform1i(this.glUniformImage, 0);
                GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, this.texture2D.getLocation());

                GLES31.glUniformMatrix4fv(this.glUniformMatrixProjectionAndView, 1, false, FloatBuffer.wrap(this.mvp));

                GLES31.glEnableVertexAttribArray(this.glAttributeTexturePosition);
                GLES31.glVertexAttribPointer(
                        this.glAttributeTexturePosition,
                        2, // coords_per_vertex,
                        GLES31.GL_FLOAT,
                        false,
                        8, // coords_per_vertex * 4 bytes per vertex (GL_FLOAT)
                        floatArrayToBuffer(new float[]{
                                0f, 0f,
                                0f, 1f,
                                1f, 0f,
                                1f, 1f
                        }));

                GLES31.glEnableVertexAttribArray(this.glAttributePosition);
                GLES31.glVertexAttribPointer(
                        this.glAttributePosition,
                        2, // coords_per_vertex,
                        GLES31.GL_FLOAT,
                        false,
                        8, // coords_per_vertex * 4 bytes per vertex (GL_FLOAT)
                        floatArrayToBuffer(new float[]{
                                rect.left , rect.top   ,
                                rect.left , rect.bottom,
                                rect.right, rect.top   ,
                                rect.right, rect.bottom
                        }));

                GLES31.glDrawArrays(GLES31.GL_TRIANGLE_STRIP, 0, 4);

                GLES31.glActiveTexture(GLES31.GL_TEXTURE0);
                GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, 0);

                GLES31.glUseProgram(0);
            }
        }
    }
}
