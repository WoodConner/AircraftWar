package edu.hitsz.render;

import java.util.ArrayList;

/**
 * 程序化低多边形飞机网格生成器
 *
 * 坐标系（模型空间，归一化 -0.5 ~ 0.5）：
 *   -Z = 机头（朝屏幕上方）   +Z = 机尾
 *   +Y = 向上（法线）         +X = 右翼
 *
 * 顶点格式：[x, y, z, nx, ny, nz, r, g, b] = 9 floats/vertex（平面着色，每三角3顶点共享同一法线）
 *
 * 使用方式：
 *   float[] heroVerts = AircraftMesh.build(AircraftMesh.HERO);
 *   int vtxCount = heroVerts.length / 9;
 */
public final class AircraftMesh {

    public static final int HERO     = 0;
    public static final int MOB      = 1;
    public static final int ELITE    = 2;
    public static final int BOSS     = 3;
    public static final int BULLET_H = 4;
    public static final int BULLET_E = 5;
    public static final int PROP     = 6;

    public static float[] build(int type) {
        switch (type) {
            case MOB:      return mob();
            case ELITE:    return elite();
            case BOSS:     return boss();
            case BULLET_H: return bullet(new float[]{0.50f,0.95f,0.20f});
            case BULLET_E: return bullet(new float[]{0.95f,0.25f,0.25f});
            case PROP:     return prop();
            default:       return hero();
        }
    }

    // ════════════════════════════════════════════════════════════
    // 英雄飞机 — 现代隐形战机（蓝灰色）
    // ════════════════════════════════════════════════════════════
    private static float[] hero() {
        B b = new B();
        float[] F  = {0.45f,0.50f,0.62f};  // 机身主色（蓝灰）
        float[] G  = {0.22f,0.60f,0.92f};  // 驾驶舱玻璃（亮蓝）
        float[] W  = {0.37f,0.41f,0.54f};  // 机翼
        float[] E  = {0.92f,0.45f,0.10f};  // 发动机喷口（橙）
        float[] D  = {0.18f,0.20f,0.28f};  // 机腹深色

        // 机身截面 [z, topY, sideX, botY]
        float[][] s = {
            {-0.50f, 0f,    0f,    0f    },
            {-0.32f, 0.07f, 0.06f,-0.03f},
            {-0.10f, 0.09f, 0.09f,-0.04f},
            { 0.10f, 0.08f, 0.10f,-0.04f},
            { 0.28f, 0.07f, 0.09f,-0.03f},
            { 0.50f, 0f,    0f,    0f    },
        };
        b.loft(s, F, D);

        // 驾驶舱隆起
        float[] CF ={-0.05f,0.10f,-0.24f}, CF2={0.05f,0.10f,-0.24f};
        float[] CT ={-0.04f,0.17f,-0.10f}, CT2={0.04f,0.17f,-0.10f};
        float[] CR ={-0.03f,0.14f, 0.03f}, CR2={0.03f,0.14f, 0.03f};
        b.tri(CF, CT, CT2,G); b.tri(CF,CT2,CF2,G);
        b.tri(CT, CR, CR2,G); b.tri(CT,CR2,CT2,G);

        // 机翼（后掠式）
        float[] WLF={-0.10f,0.01f,-0.04f}, WLT={-0.50f,-0.01f,0.10f}, WLR={-0.10f,0.01f,0.22f};
        float[] WRF={ 0.10f,0.01f,-0.04f}, WRT={ 0.50f,-0.01f,0.10f}, WRR={ 0.10f,0.01f,0.22f};
        b.tri(WLF,WLR,WLT,W); b.tri(WLF,WLT,WLR,W); // 左翼上下
        b.tri(WRF,WRT,WRR,W); b.tri(WRF,WRR,WRT,W); // 右翼上下

        // 水平尾翼
        float[] TL0={-0.09f,0.04f,0.30f},TL1={-0.22f,0.01f,0.46f},TLb={-0.09f,-0.02f,0.30f};
        float[] TR0={ 0.09f,0.04f,0.30f},TR1={ 0.22f,0.01f,0.46f},TRb={ 0.09f,-0.02f,0.30f};
        b.tri(TL0,TL1,TLb,W); b.tri(TL0,TLb,TL1,W);
        b.tri(TR0,TRb,TR1,W); b.tri(TR0,TR1,TRb,W);

        // 双发动机喷口
        float[] EL0={-0.09f,0.04f,0.46f},EL1={-0.09f,-0.03f,0.46f},EL2={-0.04f,-0.03f,0.46f},EL3={-0.04f,0.04f,0.46f};
        float[] ER0={ 0.04f,0.04f,0.46f},ER1={ 0.04f,-0.03f,0.46f},ER2={ 0.09f,-0.03f,0.46f},ER3={ 0.09f,0.04f,0.46f};
        b.quad(EL0,EL1,EL2,EL3,E); b.quad(ER0,ER1,ER2,ER3,E);
        return b.build();
    }

    // ════════════════════════════════════════════════════════════
    // 杂兵飞机 — 简单小型战机（暗红）
    // ════════════════════════════════════════════════════════════
    private static float[] mob() {
        B b = new B();
        float[] F = {0.62f,0.22f,0.22f}; // 暗红机身
        float[] W = {0.50f,0.18f,0.18f};
        float[] E = {0.85f,0.42f,0.05f};
        float[] D = {0.28f,0.10f,0.10f};

        float[][] s = {
            {-0.45f, 0f,    0f,    0f    },
            {-0.20f, 0.06f, 0.07f,-0.04f},
            { 0.10f, 0.07f, 0.08f,-0.04f},
            { 0.45f, 0f,    0f,    0f    },
        };
        b.loft(s, F, D);

        // 直机翼
        float[] WLF={-0.08f,0.01f,-0.05f}, WLT={-0.40f,-0.01f,0.02f}, WLR={-0.08f,0.01f,0.15f};
        float[] WRF={ 0.08f,0.01f,-0.05f}, WRT={ 0.40f,-0.01f,0.02f}, WRR={ 0.08f,0.01f,0.15f};
        b.tri(WLF,WLR,WLT,W); b.tri(WLF,WLT,WLR,W);
        b.tri(WRF,WRT,WRR,W); b.tri(WRF,WRR,WRT,W);

        // 单喷口
        float[] E0={-0.05f,0.04f,0.42f},E1={-0.05f,-0.03f,0.42f},E2={0.05f,-0.03f,0.42f},E3={0.05f,0.04f,0.42f};
        b.quad(E0,E1,E2,E3,E);
        return b.build();
    }

    // ════════════════════════════════════════════════════════════
    // 精英飞机 — 角形战机（金色）
    // ════════════════════════════════════════════════════════════
    private static float[] elite() {
        B b = new B();
        float[] F = {0.70f,0.55f,0.15f}; // 金色机身
        float[] W = {0.60f,0.46f,0.12f};
        float[] A = {0.90f,0.70f,0.20f}; // 亮金高光
        float[] D = {0.35f,0.28f,0.08f};

        float[][] s = {
            {-0.48f, 0f,    0f,    0f    },
            {-0.25f, 0.07f, 0.07f,-0.03f},
            { 0.02f, 0.09f, 0.10f,-0.04f},
            { 0.22f, 0.08f, 0.08f,-0.03f},
            { 0.48f, 0f,    0f,    0f    },
        };
        b.loft(s, F, D);

        // 三角形后掠翼（更大）
        float[] WLF={-0.10f,0.01f,-0.15f}, WLT={-0.48f,-0.01f,0.20f}, WLR={-0.10f,0.01f,0.28f};
        float[] WRF={ 0.10f,0.01f,-0.15f}, WRT={ 0.48f,-0.01f,0.20f}, WRR={ 0.10f,0.01f,0.28f};
        b.tri(WLF,WLR,WLT,W); b.tri(WLF,WLT,WLR,W);
        b.tri(WRF,WRT,WRR,W); b.tri(WRF,WRR,WRT,W);

        // 金色高光条纹（顶面细条）
        float[] S0={-0.04f,0.10f,-0.30f},S1={-0.04f,0.10f,0.20f},S2={0.04f,0.10f,0.20f},S3={0.04f,0.10f,-0.30f};
        b.quad(S0,S1,S2,S3,A);

        float[] E0={-0.06f,0.04f,0.44f},E1={-0.06f,-0.03f,0.44f},E2={0.06f,-0.03f,0.44f},E3={0.06f,0.04f,0.44f};
        b.quad(E0,E1,E2,E3,A);
        return b.build();
    }

    // ════════════════════════════════════════════════════════════
    // BOSS — 重型轰炸机（暗紫）
    // ════════════════════════════════════════════════════════════
    private static float[] boss() {
        B b = new B();
        float[] F = {0.35f,0.20f,0.45f}; // 暗紫机身
        float[] W = {0.28f,0.16f,0.38f};
        float[] E = {0.92f,0.20f,0.92f}; // 紫色引擎
        float[] A = {0.55f,0.10f,0.70f}; // 亮紫高光
        float[] D = {0.18f,0.10f,0.25f};

        float[][] s = {
            {-0.50f, 0f,    0f,    0f    },
            {-0.30f, 0.12f, 0.12f,-0.06f},
            {-0.05f, 0.15f, 0.18f,-0.08f},
            { 0.15f, 0.14f, 0.18f,-0.08f},
            { 0.35f, 0.10f, 0.14f,-0.05f},
            { 0.50f, 0f,    0f,    0f    },
        };
        b.loft(s, F, D);

        // 大型机翼（前掠+后掠混合）
        float[] WLF={-0.18f,0.01f,-0.25f}, WLT={-0.50f,-0.02f,0.05f}, WLM={-0.48f,-0.02f,0.30f}, WLR={-0.18f,0.01f,0.35f};
        float[] WRF={ 0.18f,0.01f,-0.25f}, WRT={ 0.50f,-0.02f,0.05f}, WRM={ 0.48f,-0.02f,0.30f}, WRR={ 0.18f,0.01f,0.35f};
        b.tri(WLF,WLR,WLT,W); b.tri(WLR,WLM,WLT,W); b.tri(WLF,WLT,WLR,W); b.tri(WLR,WLT,WLM,W);
        b.tri(WRF,WRT,WRR,W); b.tri(WRR,WRT,WRM,W); b.tri(WRF,WRR,WRT,W); b.tri(WRR,WRM,WRT,W);

        // 4个发动机喷口
        float dx = 0.11f;
        for (int i = -1; i <= 1; i += 2) {
            for (int j = -1; j <= 1; j += 2) {
                float ox = i * dx, oy = j * 0.04f;
                float[] en0={ox-0.04f,0.05f+oy,0.46f},en1={ox-0.04f,-0.02f+oy,0.46f};
                float[] en2={ox+0.04f,-0.02f+oy,0.46f},en3={ox+0.04f,0.05f+oy,0.46f};
                b.quad(en0,en1,en2,en3,E);
            }
        }

        // 中央高光脊线
        float[] R0={-0.05f,0.16f,-0.25f},R1={-0.05f,0.16f,0.20f},R2={0.05f,0.16f,0.20f},R3={0.05f,0.16f,-0.25f};
        b.quad(R0,R1,R2,R3,A);
        return b.build();
    }

    // ════════════════════════════════════════════════════════════
    // 子弹 — 细长菱形
    // ════════════════════════════════════════════════════════════
    private static float[] bullet(float[] color) {
        B b = new B();
        float[] tip1={0,0.02f,-0.48f}, tip2={0,0.02f,0.48f};
        float[] mL={-0.10f,0.02f,0f}, mR={0.10f,0.02f,0f}, mT={0,0.08f,0f}, mB={0,-0.04f,0f};
        b.tri(tip1,mL,mT,color); b.tri(tip1,mT,mR,color);
        b.tri(tip2,mT,mL,color); b.tri(tip2,mR,mT,color);
        b.tri(tip1,mB,mL,color); b.tri(tip1,mR,mB,color);
        b.tri(tip2,mL,mB,color); b.tri(tip2,mB,mR,color);
        return b.build();
    }

    // ════════════════════════════════════════════════════════════
    // 道具 — 旋转宝石多面体
    // ════════════════════════════════════════════════════════════
    private static float[] prop() {
        B b = new B();
        float[] C = {0.30f,0.90f,0.50f};
        float[] top={0,0.40f,0}, bot={0,-0.40f,0};
        float[][] ring = {{0.35f,0,0},{0,0,0.35f},{-0.35f,0,0},{0,0,-0.35f}};
        for (int i = 0; i < 4; i++) {
            float[] a = ring[i], nxt = ring[(i+1)%4];
            b.tri(top,a,nxt,C); b.tri(bot,nxt,a,C);
        }
        return b.build();
    }

    // ════════════════════════════════════════════════════════════
    // Builder
    // ════════════════════════════════════════════════════════════
    private static final class B {
        private final ArrayList<Float> v = new ArrayList<>(1024);

        /** 添加三角形（自动计算面法线） */
        void tri(float[] v0, float[] v1, float[] v2, float[] c) {
            // 面法线 = (v1-v0) × (v2-v0)
            float e1x=v1[0]-v0[0], e1y=v1[1]-v0[1], e1z=v1[2]-v0[2];
            float e2x=v2[0]-v0[0], e2y=v2[1]-v0[1], e2z=v2[2]-v0[2];
            float nx=e1y*e2z-e1z*e2y, ny=e1z*e2x-e1x*e2z, nz=e1x*e2y-e1y*e2x;
            float len=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
            if(len>1e-6f){nx/=len;ny/=len;nz/=len;}
            addV(v0,nx,ny,nz,c); addV(v1,nx,ny,nz,c); addV(v2,nx,ny,nz,c);
        }

        /** 四边形 → 2个三角形（v0,v1,v2,v3 按CCW顺序） */
        void quad(float[] v0,float[] v1,float[] v2,float[] v3,float[] c) {
            tri(v0,v1,v2,c); tri(v0,v2,v3,c);
        }

        /**
         * 纵向放样机身（Loft）
         * 每个截面：[z, topY, sideX, botY]  ← 4点钻石形截面
         */
        void loft(float[][] secs, float[] topColor, float[] botColor) {
            for (int i = 0; i < secs.length - 1; i++) {
                float[] a = secs[i], bSec = secs[i+1];
                float az=a[0], aty=a[1], asx=a[2], aby=a[3];
                float bz=bSec[0], bty=bSec[1], bsx=bSec[2], bby=bSec[3];

                float[] aTop={0,aty,az}, aRt={ asx,0,az}, aBot={0,aby,az}, aLt={-asx,0,az};
                float[] bTop={0,bty,bz}, bRt={ bsx,0,bz}, bBot={0,bby,bz}, bLt={-bsx,0,bz};

                // 4个侧面（每侧一个quad）
                quad(aTop,aLt,bLt,bTop, topColor); // 左上
                quad(aTop,bTop,bRt,aRt, topColor); // 右上
                quad(aLt,aBot,bBot,bLt, botColor); // 左下
                quad(aRt,bRt,bBot,aBot, botColor); // 右下
            }
        }

        private void addV(float[] p, float nx,float ny,float nz,float[] c) {
            v.add(p[0]); v.add(p[1]); v.add(p[2]);
            v.add(nx);   v.add(ny);   v.add(nz);
            v.add(c[0]); v.add(c[1]); v.add(c[2]);
        }

        float[] build() {
            float[] a = new float[v.size()];
            for (int i = 0; i < v.size(); i++) a[i] = v.get(i);
            return a;
        }
    }
}
