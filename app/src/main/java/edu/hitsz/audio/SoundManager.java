package edu.hitsz.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

/**
 * 音频管理器 —— Android多线程音频处理
 *
 * 多线程设计：
 *   - 专用 HandlerThread（"AudioThread"）负责所有音频初始化和操作，
 *     避免阻塞主线程或游戏渲染线程
 *   - SoundPool：用于低延迟音效（射击、爆炸、拾取道具）
 *   - MediaPlayer：用于背景音乐（BGM），支持循环播放
 *
 * 音效文件需放置于 res/raw/ 目录，文件名如：
 *   bgm.mp3, sound_shoot.wav, sound_enemy_die.wav,
 *   sound_hero_die.wav, sound_prop.wav, sound_bomb.wav
 */
public class SoundManager {
    private static final String TAG = "SoundManager";

    // 专用音频线程（HandlerThread），保证所有音频操作在同一非UI线程执行
    private final HandlerThread audioThread;
    private final Handler audioHandler;

    // 背景音乐（MediaPlayer 适合长音频，支持暂停/恢复）
    private MediaPlayer bgmPlayer;
    private volatile boolean bgmEnabled = true;
    private volatile boolean sfxEnabled = true;

    // 音效（SoundPool 适合短音频，低延迟）
    private SoundPool soundPool;
    private int soundShoot = -1;
    private int soundEnemyDie = -1;
    private int soundHeroDie = -1;
    private int soundPropGet = -1;
    private int soundBomb = -1;

    private final Context context;

    public SoundManager(Context context) {
        this.context = context.getApplicationContext();

        // 启动音频专用线程
        audioThread = new HandlerThread("AudioThread");
        audioThread.start();
        audioHandler = new Handler(audioThread.getLooper());

        // 在音频线程中异步初始化（不阻塞UI线程）
        audioHandler.post(this::initAudio);
    }

    /** 在音频线程中初始化 SoundPool 和 MediaPlayer */
    private void initAudio() {
        // 初始化 SoundPool（游戏音效）
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        soundPool = new SoundPool.Builder()
                .setMaxStreams(8)
                .setAudioAttributes(attrs)
                .build();

        // 加载音效资源（文件不存在时优雅降级，不崩溃）
        soundShoot = loadSound("sound_shoot");
        soundEnemyDie = loadSound("sound_enemy_die");
        soundHeroDie = loadSound("sound_hero_die");
        soundPropGet = loadSound("sound_prop");
        soundBomb = loadSound("sound_bomb");

        // 初始化背景音乐
        initBgm("bgm");
    }

    /** 通过资源名加载音效（动态查找 R.raw.name） */
    private int loadSound(String name) {
        try {
            int resId = context.getResources().getIdentifier(name, "raw", context.getPackageName());
            if (resId != 0 && soundPool != null) return soundPool.load(context, resId, 1);
        } catch (Exception e) {
            Log.w(TAG, "音效加载失败: " + name);
        }
        return -1;
    }

    /** 初始化背景音乐 MediaPlayer */
    private void initBgm(String name) {
        try {
            int resId = context.getResources().getIdentifier(name, "raw", context.getPackageName());
            if (resId != 0) {
                bgmPlayer = MediaPlayer.create(context, resId);
                if (bgmPlayer != null) {
                    bgmPlayer.setLooping(true);
                    bgmPlayer.setVolume(0.5f, 0.5f);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "背景音乐加载失败");
        }
    }

    // ===== 公开音效播放接口（可从任意线程安全调用） =====

    public void startBgm() {
        audioHandler.post(() -> {
            if (bgmEnabled && bgmPlayer != null && !bgmPlayer.isPlaying()) bgmPlayer.start();
        });
    }

    public void pauseBgm() {
        audioHandler.post(() -> {
            if (bgmPlayer != null && bgmPlayer.isPlaying()) bgmPlayer.pause();
        });
    }

    public void resumeBgm() {
        audioHandler.post(() -> {
            if (bgmEnabled && bgmPlayer != null) bgmPlayer.start();
        });
    }

    public void playShoot() { playSfx(soundShoot, 0.6f); }
    public void playEnemyDie() { playSfx(soundEnemyDie, 1.0f); }
    public void playHeroDie() { playSfx(soundHeroDie, 1.0f); }
    public void playPropGet() { playSfx(soundPropGet, 0.9f); }
    public void playBomb() { playSfx(soundBomb, 1.0f); }

    private void playSfx(int soundId, float volume) {
        if (!sfxEnabled || soundId < 0) return;
        audioHandler.post(() -> {
            if (soundPool != null && soundId >= 0) {
                soundPool.play(soundId, volume, volume, 1, 0, 1.0f);
            }
        });
    }

    public void setBgmEnabled(boolean enabled) {
        bgmEnabled = enabled;
        if (!enabled) pauseBgm(); else startBgm();
    }

    public void setSfxEnabled(boolean enabled) { sfxEnabled = enabled; }

    /**
     * 释放音频资源（在 onDestroy 中调用）
     * 先释放资源，再退出音频线程，确保无内存泄漏
     */
    public void release() {
        audioHandler.post(() -> {
            if (bgmPlayer != null) { bgmPlayer.stop(); bgmPlayer.release(); bgmPlayer = null; }
            if (soundPool != null) { soundPool.release(); soundPool = null; }
        });
        // 等待音频线程完成清理后退出
        audioThread.quitSafely();
    }
}
