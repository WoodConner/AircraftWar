package edu.hitsz.render;

import android.opengl.GLES20;

/**
 * 3D 网格 VBO/IBO 封装。
 *
 * 顶点布局（每顶点 10 个 float，40 字节）:
 *   [0-2]  position  (x, y, z)
 *   [3-5]  normal    (nx, ny, nz)
 *   [6-9]  color     (r, g, b, a)
 */
public class Mesh {

    public static final int FLOATS_PER_VERTEX = 10;
    public static final int STRIDE            = FLOATS_PER_VERTEX * 4; // bytes
    public static final int POS_OFFSET        = 0;
    public static final int NORM_OFFSET       = 3 * 4;
    public static final int COLOR_OFFSET      = 6 * 4;

    private final int vbo, ibo;
    private final int indexCount;

    public Mesh(float[] vertices, short[] indices) {
        int[] bufs = new int[2];
        GLES20.glGenBuffers(2, bufs, 0);
        vbo = bufs[0];
        ibo = bufs[1];

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER,
                vertices.length * 4, GlUtil.floatBuf(vertices), GLES20.GL_STATIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo);
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER,
                indices.length * 2, GlUtil.shortBuf(indices), GLES20.GL_STATIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
        indexCount = indices.length;
    }

    /** 绑定 VBO + 启用属性指针 */
    public void bind(int posAttr, int normAttr, int colorAttr) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo);
        if (posAttr   >= 0) { GLES20.glEnableVertexAttribArray(posAttr);
            GLES20.glVertexAttribPointer(posAttr,   3, GLES20.GL_FLOAT, false, STRIDE, POS_OFFSET);   }
        if (normAttr  >= 0) { GLES20.glEnableVertexAttribArray(normAttr);
            GLES20.glVertexAttribPointer(normAttr,  3, GLES20.GL_FLOAT, false, STRIDE, NORM_OFFSET);  }
        if (colorAttr >= 0) { GLES20.glEnableVertexAttribArray(colorAttr);
            GLES20.glVertexAttribPointer(colorAttr, 4, GLES20.GL_FLOAT, false, STRIDE, COLOR_OFFSET); }
    }

    public void draw() {
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, 0);
    }

    public void unbind(int posAttr, int normAttr, int colorAttr) {
        if (posAttr   >= 0) GLES20.glDisableVertexAttribArray(posAttr);
        if (normAttr  >= 0) GLES20.glDisableVertexAttribArray(normAttr);
        if (colorAttr >= 0) GLES20.glDisableVertexAttribArray(colorAttr);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    public void delete() {
        GLES20.glDeleteBuffers(2, new int[]{vbo, ibo}, 0);
    }
}
