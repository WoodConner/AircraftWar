package edu.hitsz.sufaceview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;

import edu.hitsz.R;

/**
 * EasyGame - 简单模式游戏主类
 * 将Windows版BaseGame重构为Android SurfaceView子类
 * 替代原Windows版的JFrame/JPanel Swing组件
 */
public class EasyGame extends MySurfaceView {

    // 游戏图片资源（替代原Windows版ImageManager/BufferedImage）
    private Bitmap bgBitmap;       // 背景图片
    private Bitmap heroBitmap;     // 英雄飞机图片
    private Bitmap mobBitmap;      // 普通敌机
    private Bitmap eliteBitmap;    // 精英敌机
    private Bitmap bossBitmap;     // Boss飞机
    private Bitmap bulletHeroBitmap;   // 英雄子弹
    private Bitmap bulletEnemyBitmap;  // 敌机子弹

    // 英雄飞机当前位置（触屏控制，替代原Windows版HeroController鼠标输入）
    private float heroX = 240;
    private float heroY = 600;

    // 背景滚动偏移量（实现背景纵向滚动效果）
    private float bgOffset = 0;

    public EasyGame(Context context) {
        super(context);

        // 注册触屏事件监听器（替代Windows版HeroController鼠标输入）
        setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                // 跟踪手指位置，控制英雄飞机移动
                heroX = motionEvent.getX();
                heroY = motionEvent.getY();
                return true;
            }
        });
    }

    /**
     * surfaceCreated: 加载图片资源，设置绘制标志位，启动游戏线程
     * 使用Android BitmapFactory.decodeResource() 替代原Windows版 ImageIO.read()
     */
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // 加载图片资源（替代原Windows版ImageManager.java中的BufferedImage）
        bgBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.bg);
        heroBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.hero);
        mobBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.mob);
        eliteBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.elite);
        bossBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.boss);
        bulletHeroBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.bullet_hero);
        bulletEnemyBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.bullet_enemy);

        // 设置绘制标志位为true，启动游戏绘制线程
        mbLoop = true;
        new Thread(this).start();
    }

    /**
     * 游戏绘制逻辑：绘制背景、飞机等UI元素
     * 使用Android Canvas API 替代原Windows版 Graphics2D
     */
    @Override
    public void draw() {
        if (mSurfaceHolder == null) {
            return;
        }

        // 锁定画布，获取Canvas对象
        canvas = mSurfaceHolder.lockCanvas();
        if (canvas == null) {
            return;
        }

        try {
            // 绘制滚动背景
            drawBackground(canvas);

            // 绘制英雄飞机（以触屏位置为中心）
            if (heroBitmap != null) {
                float drawX = heroX - heroBitmap.getWidth() / 2f;
                float drawY = heroY - heroBitmap.getHeight() / 2f;
                canvas.drawBitmap(heroBitmap, drawX, drawY, null);
            }

        } finally {
            // 释放Canvas对象，解锁并提交绘制内容
            mSurfaceHolder.unlockCanvasAndPost(canvas);
        }

        // 背景滚动
        bgOffset += 2;
        if (bgBitmap != null && bgOffset >= bgBitmap.getHeight()) {
            bgOffset = 0;
        }
    }

    /**
     * 绘制纵向滚动背景
     */
    private void drawBackground(Canvas canvas) {
        if (bgBitmap == null) {
            return;
        }

        // 将背景图片缩放至屏幕宽度
        float scaleX = (float) screenWidth / bgBitmap.getWidth();
        float scaleY = (float) screenHeight / bgBitmap.getHeight();
        float scale = Math.max(scaleX, scaleY);

        Matrix matrix1 = new Matrix();
        matrix1.setScale(scale, scale);
        matrix1.postTranslate(0, bgOffset - bgBitmap.getHeight() * scale);
        canvas.drawBitmap(bgBitmap, matrix1, null);

        Matrix matrix2 = new Matrix();
        matrix2.setScale(scale, scale);
        matrix2.postTranslate(0, bgOffset);
        canvas.drawBitmap(bgBitmap, matrix2, null);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mbLoop = false;
    }
}
