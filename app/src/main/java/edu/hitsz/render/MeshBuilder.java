package edu.hitsz.render;

import java.util.ArrayList;

/**
 * 流式 3D 网格构建器。
 *
 * 用法：
 *   MeshBuilder b = new MeshBuilder();
 *   int v0 = b.v(x,y,z, nx,ny,nz, r,g,b,a);
 *   b.tri(v0, v1, v2);
 *   Mesh m = b.build();
 */
public class MeshBuilder {

    private final ArrayList<Float> vtx = new ArrayList<>();
    private final ArrayList<Short> idx = new ArrayList<>();

    /** 添加一个顶点，返回顶点索引 */
    public int v(float x,  float y,  float z,
                 float nx, float ny, float nz,
                 float r,  float g,  float b, float a) {
        int i = vtx.size() / Mesh.FLOATS_PER_VERTEX;
        vtx.add(x);  vtx.add(y);  vtx.add(z);
        vtx.add(nx); vtx.add(ny); vtx.add(nz);
        vtx.add(r);  vtx.add(g);  vtx.add(b);  vtx.add(a);
        return i;
    }

    /** 添加一个三角面 */
    public void tri(int a, int b, int c) {
        idx.add((short) a);
        idx.add((short) b);
        idx.add((short) c);
    }

    /** 两个三角形构成的四边形 (a,b,c,d 逆时针) */
    public void quad(int a, int b, int c, int d) {
        tri(a, b, c);
        tri(a, c, d);
    }

    /**
     * 将 2D 多边形轮廓挤出为 3D 平板 (slab)，添加顶面/底面/侧面。
     *
     * @param outline2D 顶点 [x0,y0, x1,y1, ...] 逆时针（屏幕 Y 向下时，从正面看是 CCW）
     * @param zTop      顶面 Z（靠近相机，= +depth/2）
     * @param zBot      底面 Z（远离相机，= -depth/2）
     * @param topC      顶面 RGBA
     * @param botC      底面 RGBA
     * @param sideC     侧面 RGBA
     */
    public void extrude(float[] outline2D,
                        float zTop, float zBot,
                        float[] topC, float[] botC, float[] sideC) {
        int n = outline2D.length / 2;

        // ----- 顶面 (法线 0,0,+1) -----
        int[] topIdx = new int[n];
        for (int i = 0; i < n; i++)
            topIdx[i] = v(outline2D[i*2], outline2D[i*2+1], zTop,
                          0, 0, 1,
                          topC[0], topC[1], topC[2], topC[3]);
        // Fan 三角化（凸多边形）
        for (int i = 1; i < n - 1; i++)
            tri(topIdx[0], topIdx[i], topIdx[i+1]);

        // ----- 底面 (法线 0,0,-1) -----
        int[] botIdx = new int[n];
        for (int i = 0; i < n; i++)
            botIdx[i] = v(outline2D[i*2], outline2D[i*2+1], zBot,
                          0, 0, -1,
                          botC[0], botC[1], botC[2], botC[3]);
        // 反转绕序
        for (int i = 1; i < n - 1; i++)
            tri(botIdx[0], botIdx[i+1], botIdx[i]);

        // ----- 侧面 -----
        for (int i = 0; i < n; i++) {
            int next = (i + 1) % n;
            float dx = outline2D[next*2]   - outline2D[i*2];
            float dy = outline2D[next*2+1] - outline2D[i*2+1];
            float len = (float) Math.sqrt(dx*dx + dy*dy);
            if (len < 1e-4f) continue;
            // 外向法线 (2D 边的左手法线在 Y-down 坐标系 = (dy, -dx)/len)
            float nx = dy / len, ny = -dx / len;

            int t0 = v(outline2D[i*2],    outline2D[i*2+1],    zTop, nx, ny, 0, sideC[0],sideC[1],sideC[2],sideC[3]);
            int t1 = v(outline2D[next*2], outline2D[next*2+1], zTop, nx, ny, 0, sideC[0],sideC[1],sideC[2],sideC[3]);
            int b0 = v(outline2D[i*2],    outline2D[i*2+1],    zBot, nx, ny, 0, sideC[0],sideC[1],sideC[2],sideC[3]);
            int b1 = v(outline2D[next*2], outline2D[next*2+1], zBot, nx, ny, 0, sideC[0],sideC[1],sideC[2],sideC[3]);
            quad(t0, t1, b1, b0);
        }
    }

    public Mesh build() {
        float[] va = new float[vtx.size()];
        for (int i = 0; i < va.length; i++) va[i] = vtx.get(i);
        short[] ia = new short[idx.size()];
        for (int i = 0; i < ia.length; i++) ia[i] = idx.get(i);
        return new Mesh(va, ia);
    }
}
