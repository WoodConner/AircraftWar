Audio Files Guide / 音频文件说明
====================

This game requires the following audio files to play sound effects properly.
本游戏需要以下音频文件才能正常播放音效。

Required Audio Files (Place in app/src/main/res/raw/):
必需的音频文件（放置在 app/src/main/res/raw/ 目录）：
------------------------------------------------------

1. bgm.mp3 or bgm.wav
   - Background music (loop playback)
   - 背景音乐（循环播放）
   - Recommended duration: 2-3 minutes / 建议时长：2-3分钟
   - Format: MP3 or WAV

2. sound_shoot.wav
   - Shooting sound effect
   - 射击音效
   - Recommended duration: 0.1-0.3 seconds / 建议时长：0.1-0.3秒
   - Format: WAV (low latency)

3. sound_enemy_die.wav
   - Enemy death sound effect
   - 敌机死亡音效
   - Recommended duration: 0.3-0.5 seconds / 建议时长：0.3-0.5秒
   - Format: WAV

4. sound_hero_die.wav
   - Hero death sound effect
   - 英雄死亡音效
   - Recommended duration: 0.5-1 second / 建议时长：0.5-1秒
   - Format: WAV

5. sound_prop.wav
   - Prop pickup sound effect
   - 拾取道具音效
   - Recommended duration: 0.2-0.4 seconds / 建议时长：0.2-0.4秒
   - Format: WAV

6. sound_bomb.wav
   - Bomb explosion sound effect
   - 炸弹爆炸音效
   - Recommended duration: 0.5-1 second / 建议时长：0.5-1秒
   - Format: WAV


How to Add Audio Files:
如何添加音频文件：
------------------------------------------------------

Method 1: Using Android Studio
方法1：使用Android Studio
1. In Android Studio, right-click on app/src/main/res/raw directory
   在Android Studio中，右键点击 app/src/main/res/raw 目录
2. Select New > File
   选择 New > File
3. Enter filename (e.g., bgm.mp3)
   输入文件名（如 bgm.mp3）
4. Copy audio file content to the new file
   将音频文件内容复制到新建的文件中

Method 2: Direct File Copy
方法2：直接复制文件
1. Open File Explorer and navigate to project directory
   打开文件管理器，导航到项目目录
2. Go to app/src/main/res/raw/ directory
   进入 app/src/main/res/raw/ 目录
3. Copy audio files directly to this directory
   将音频文件直接复制到该目录

Method 3: Use Free Sound Resources
方法3：使用免费音效资源
Recommended websites / 推荐网站：
- Freesound.org (https://freesound.org/)
- OpenGameArt.org (https://opengameart.org/)
- Zapsplat.com (https://www.zapsplat.com/)


Important Notes:
注意事项：
------------------------------------------------------
1. Filenames must be lowercase, containing only letters, numbers, and underscores
   文件名必须全部小写，只能包含字母、数字和下划线
2. Do not use Chinese characters in filenames
   不能使用中文文件名
3. If audio files are missing, the game will still run but without sound effects
   如果缺少音频文件，游戏仍可运行，但不会播放对应音效
4. SoundManager will automatically handle missing files without crashing
   SoundManager 会自动处理文件缺失的情况，不会导致崩溃


Current Status:
当前状态：
------------------------------------------------------
✗ Audio files missing / 音频文件缺失
  Game can run but without sound effects
  游戏可以运行，但没有音效

Recommendation:
建议：
  Please add audio files according to the above instructions for complete game experience
  请按照上述说明添加音频文件以获得完整的游戏体验
