package edu.hitsz.render;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLUtils;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/** OpenGL ES 2.0 工具类 — 着色器编译、纹理加载、缓冲区创建 */
public final class GlUtil {

    private GlUtil() {}

    /** 编译单个着色器 */
    public static int compileShader(int type, String src) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, src);
        GLES20.glCompileShader(shader);
        int[] ok = {0};
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            String log = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new RuntimeException("Shader compile error:\n" + log);
        }
        return shader;
    }

    /** 链接顶点+片段着色器，返回程序句柄 */
    public static int linkProgram(String vertSrc, String fragSrc) {
        int v = compileShader(GLES20.GL_VERTEX_SHADER,   vertSrc);
        int f = compileShader(GLES20.GL_FRAGMENT_SHADER, fragSrc);
        int prog = GLES20.glCreateProgram();
        GLES20.glAttachShader(prog, v);
        GLES20.glAttachShader(prog, f);
        GLES20.glLinkProgram(prog);
        GLES20.glDeleteShader(v);
        GLES20.glDeleteShader(f);
        int[] ok = {0};
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0)
            throw new RuntimeException("Program link error:\n" + GLES20.glGetProgramInfoLog(prog));
        return prog;
    }

    /** 从 res/raw/ 读取文本资源 */
    public static String loadRaw(Context ctx, int resId) {
        try (InputStream is = ctx.getResources().openRawResource(resId)) {
            byte[] buf = new byte[is.available()];
            //noinspection ResultOfMethodCallIgnored
            is.read(buf);
            return new String(buf);
        } catch (Exception e) {
            throw new RuntimeException("Cannot read raw resource " + resId, e);
        }
    }

    /** float[] → FloatBuffer (NativeOrder) */
    public static FloatBuffer floatBuf(float[] arr) {
        ByteBuffer bb = ByteBuffer.allocateDirect(arr.length * 4);
        bb.order(ByteOrder.nativeOrder());
        FloatBuffer fb = bb.asFloatBuffer();
        fb.put(arr).position(0);
        return fb;
    }

    /** short[] → ShortBuffer (NativeOrder) */
    public static ShortBuffer shortBuf(short[] arr) {
        ByteBuffer bb = ByteBuffer.allocateDirect(arr.length * 2);
        bb.order(ByteOrder.nativeOrder());
        ShortBuffer sb = bb.asShortBuffer();
        sb.put(arr).position(0);
        return sb;
    }

    /**
     * 从资源 ID 加载 Bitmap 并上传为 GL 纹理，返回纹理 ID。
     * WRAP_S/T = GL_REPEAT，用于背景滚动。
     */
    public static int loadTexture(Context ctx, int resId) {
        int[] ids = new int[1];
        GLES20.glGenTextures(1, ids, 0);
        int tid = ids[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tid);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);
        Bitmap bmp = BitmapFactory.decodeResource(ctx.getResources(), resId);
        if (bmp != null) {
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0);
            bmp.recycle();
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        return tid;
    }
}
