package edu.hitsz.render;

/**
 * 用程序化方式生成各飞机/子弹/道具的三维网格。
 *
 * 坐标系 (与游戏像素空间一致):
 *   X 向右 / Y 向下 / Z 朝向相机（正 Z = 靠近玩家）
 *
 * 每个网格以 (0,0) 为中心，通过模型矩阵平移到游戏世界位置。
 * zTop = +depth/2（最亮，面向相机）
 * zBot = -depth/2（最暗）
 */
public final class AircraftMeshFactory {

    private AircraftMeshFactory() {}

    // ───────────── 英雄飞机 (蓝色箭头形) ─────────────
    public static Mesh heroAircraft(float hw, float hh) {
        float d = hw * 0.35f;          // 厚度
        // 轮廓：机头朝上（负 Y），逆时针绕序
        float[] outline = {
             0,        -hh,          // 机头
            -hw*0.45f, -hh*0.25f,    // 左肩
            -hw,        hh*0.05f,    // 左翼尖
            -hw*0.35f,  hh*0.5f,     // 左翼后缘
             0,         hh*0.35f,    // 尾部中心凹
             hw*0.35f,  hh*0.5f,     // 右翼后缘
             hw,        hh*0.05f,    // 右翼尖
             hw*0.45f, -hh*0.25f,    // 右肩
        };
        float[] top  = {0.40f, 0.65f, 1.00f, 1.0f}; // 亮蓝
        float[] bot  = {0.10f, 0.25f, 0.55f, 1.0f}; // 暗蓝
        float[] side = {0.22f, 0.45f, 0.80f, 1.0f}; // 中蓝
        MeshBuilder b = new MeshBuilder();
        b.extrude(outline, d, -d, top, bot, side);
        // 驾驶舱凸起（小菱形，Z 更高）
        float[] cockpit = {
             0,        -hh*0.35f,
            -hw*0.18f, -hh*0.10f,
             0,         hh*0.05f,
             hw*0.18f, -hh*0.10f,
        };
        float[] cTop  = {0.75f, 0.90f, 1.00f, 1.0f};
        float[] cBot  = {0.30f, 0.55f, 0.80f, 1.0f};
        float[] cSide = {0.50f, 0.70f, 0.95f, 1.0f};
        b.extrude(cockpit, d + hw*0.12f, d - 1, cTop, cBot, cSide);
        return b.build();
    }

    // ───────────── 杂兵敌机 (红色三角形) ─────────────
    public static Mesh mobEnemy(float hw, float hh) {
        float d = hw * 0.30f;
        float[] outline = {
             0,        -hh*0.55f,   // 尾部尖
            -hw,        hh*0.55f,   // 左前
             0,         hh*0.25f,   // 前中凹
             hw,        hh*0.55f,   // 右前
        };
        float[] top  = {1.00f, 0.25f, 0.18f, 1.0f};
        float[] bot  = {0.45f, 0.05f, 0.05f, 1.0f};
        float[] side = {0.75f, 0.12f, 0.10f, 1.0f};
        MeshBuilder b = new MeshBuilder();
        b.extrude(outline, d, -d, top, bot, side);
        return b.build();
    }

    // ───────────── 精英敌机 (橙色菱形+展翼) ─────────────
    public static Mesh eliteEnemy(float hw, float hh) {
        float d = hw * 0.32f;
        float[] body = {
             0,        -hh*0.6f,    // 前锋
            -hw*0.55f,  0,          // 左翼
             0,         hh*0.6f,    // 后部
             hw*0.55f,  0,          // 右翼
        };
        float[] sideWings = {
            -hw,        0,
            -hw*0.55f, -hh*0.25f,
            -hw*0.55f,  hh*0.25f,
        };
        float[] top  = {1.00f, 0.55f, 0.10f, 1.0f};
        float[] bot  = {0.50f, 0.20f, 0.03f, 1.0f};
        float[] side = {0.80f, 0.35f, 0.06f, 1.0f};
        MeshBuilder b = new MeshBuilder();
        b.extrude(body, d, -d, top, bot, side);
        // 左侧延伸翼
        b.extrude(sideWings, d*0.4f, -d*0.4f, top, bot, side);
        // 右侧延伸翼 (镜像)
        float[] sideWingsR = {hw, 0, hw*0.55f, -hh*0.25f, hw*0.55f, hh*0.25f};
        b.extrude(sideWingsR, d*0.4f, -d*0.4f, top, bot, side);
        return b.build();
    }

    // ───────────── Boss 敌机 (紫色六边形大型) ─────────────
    public static Mesh bossEnemy(float hw, float hh) {
        float d = hw * 0.28f;
        // 六边形主体
        float[] body = {
             0,        -hh,
            -hw*0.65f, -hh*0.5f,
            -hw*0.65f,  hh*0.5f,
             0,         hh,
             hw*0.65f,  hh*0.5f,
             hw*0.65f, -hh*0.5f,
        };
        float[] top  = {0.65f, 0.10f, 0.90f, 1.0f};
        float[] bot  = {0.28f, 0.03f, 0.40f, 1.0f};
        float[] side = {0.48f, 0.06f, 0.65f, 1.0f};
        MeshBuilder b = new MeshBuilder();
        b.extrude(body, d, -d, top, bot, side);
        // 前方炮塔
        float[] turret = {
             0,        -hh*0.55f,
            -hw*0.22f, -hh*0.25f,
             0,        -hh*0.10f,
             hw*0.22f, -hh*0.25f,
        };
        float[] tTop  = {0.80f, 0.30f, 1.00f, 1.0f};
        float[] tBot  = {0.35f, 0.08f, 0.50f, 1.0f};
        float[] tSide = {0.60f, 0.18f, 0.80f, 1.0f};
        b.extrude(turret, d + hw*0.10f, d - 1, tTop, tBot, tSide);
        return b.build();
    }

    // ───────────── 英雄子弹 (青色细条) ─────────────
    public static Mesh heroBullet(float bw, float bh) {
        float d = bw * 0.8f;
        float[] outline = {
             0,       -bh,
            -bw*0.5f,  0,
             0,        bh*0.3f,
             bw*0.5f,  0,
        };
        float[] top  = {0.20f, 1.00f, 0.90f, 1.0f};
        float[] bot  = {0.05f, 0.40f, 0.35f, 1.0f};
        float[] side = {0.12f, 0.70f, 0.65f, 1.0f};
        MeshBuilder b = new MeshBuilder();
        b.extrude(outline, d, -d, top, bot, side);
        return b.build();
    }

    // ───────────── 敌机子弹 (橙红色菱形) ─────────────
    public static Mesh enemyBullet(float bw, float bh) {
        float d = bw;
        float[] outline = {
             0,   -bh,
            -bw,   0,
             0,    bh,
             bw,   0,
        };
        float[] top  = {1.00f, 0.42f, 0.05f, 1.0f};
        float[] bot  = {0.50f, 0.15f, 0.02f, 1.0f};
        float[] side = {0.80f, 0.28f, 0.03f, 1.0f};
        MeshBuilder b = new MeshBuilder();
        b.extrude(outline, d, -d, top, bot, side);
        return b.build();
    }

    // ───────────── 道具：血包 (绿色十字) ─────────────
    public static Mesh propBlood(float s) {
        float t = s * 0.35f; // 十字臂宽
        float d = s * 0.25f;
        float[] outline = {
            -t, -s,  t, -s,  t, -t,  s, -t,
             s,  t,  t,  t,  t,  s, -t,  s,
            -t,  t, -s,  t, -s, -t, -t, -t,
        };
        float[] top  = {0.20f, 0.90f, 0.30f, 1.0f};
        float[] bot  = {0.05f, 0.40f, 0.10f, 1.0f};
        float[] side = {0.12f, 0.65f, 0.20f, 1.0f};
        MeshBuilder b = new MeshBuilder();
        b.extrude(outline, d, -d, top, bot, side);
        return b.build();
    }

    // ───────────── 道具：炸弹 (黄色菱形) ─────────────
    public static Mesh propBomb(float s) {
        float d = s * 0.35f;
        float[] outline = {0, -s, -s, 0, 0, s, s, 0};
        float[] top  = {1.00f, 0.88f, 0.10f, 1.0f};
        float[] bot  = {0.55f, 0.45f, 0.03f, 1.0f};
        float[] side = {0.80f, 0.68f, 0.06f, 1.0f};
        MeshBuilder b = new MeshBuilder();
        b.extrude(outline, d, -d, top, bot, side);
        return b.build();
    }

    // ───────────── 道具：子弹强化 (紫色六边形) ─────────────
    public static Mesh propBullet(float s) {
        float d = s * 0.30f;
        int  N  = 6;
        float[] outline = new float[N * 2];
        for (int i = 0; i < N; i++) {
            double angle = Math.PI / 2 + i * 2 * Math.PI / N;
            outline[i*2]   = (float)(s * Math.cos(angle));
            outline[i*2+1] = (float)(s * Math.sin(angle));
        }
        float[] top  = {0.75f, 0.30f, 1.00f, 1.0f};
        float[] bot  = {0.35f, 0.10f, 0.55f, 1.0f};
        float[] side = {0.55f, 0.18f, 0.80f, 1.0f};
        MeshBuilder b = new MeshBuilder();
        b.extrude(outline, d, -d, top, bot, side);
        return b.build();
    }
}
