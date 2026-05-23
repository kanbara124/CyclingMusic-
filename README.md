# 骑行音乐同步 (CyclingMusic)

一款基于 Android 的运动陪伴应用，利用手机内置的加速度计与陀螺仪实时检测骑行踏频 (RPM)，并根据踏频快慢自动切换匹配节奏的音乐，提供沉浸式的骑行体验。

> 北京邮电大学 (BUPT) 课程项目 · `com.bupt.cyclingmusic` · v1.0

---

## 功能特性

- **实时踏频检测**：融合加速度计与陀螺仪信号，使用低通滤波 + 峰值检测算法估算踏频，加权融合（accel 0.6 / gyro 0.4）后输出 RPM。
- **音乐节奏匹配**：将踏频映射为三档 BPM 区间（SLOW < 60、MEDIUM 60–90、FAST ≥ 90），自动从对应曲库挑选曲目播放，支持 5 秒切换防抖避免频繁换歌。
- **目标踏频训练模式**：用户可设定目标 RPM 与容差，实时给出"过快 / 过慢 / 完美"的彩色提示，辅助节奏训练。
- **后台骑行服务**：通过前台 Service + 通知栏显示实时距离，锁屏后仍可持续记录与播放。
- **骑行数据持久化**：使用 Room 数据库保存每次骑行的开始/结束时间、总距离、平均踏频、时长、卡路里及目标踏频。
- **历史记录与骑行总结**：结束骑行后弹出本次数据汇总，并支持在历史页查看全部记录。

## 技术栈

| 模块 | 选型 |
| --- | --- |
| 语言 | Kotlin 1.9.20 |
| 构建 | Android Gradle Plugin 8.2.1 |
| UI | AppCompat + Material Components + ConstraintLayout（XML 布局，未启用 Compose） |
| 数据库 | Room 2.5.2（通过 KSP 生成代码） |
| 异步 | Kotlin Coroutines |
| 传感器 | Android `SensorManager`（TYPE_ACCELEROMETER、TYPE_GYROSCOPE） |
| 媒体 | Android `MediaPlayer` |

- `minSdk = 24`，`targetSdk = 34`，`compileSdk = 34`
- Java/Kotlin 编译目标：1.8

## 项目结构

```
CyclingMusic/
├── build.gradle.kts              # 顶层构建脚本
├── settings.gradle.kts
├── gradle.properties
└── app/
    ├── build.gradle.kts          # 模块构建脚本与依赖
    └── src/main/
        ├── AndroidManifest.xml
        ├── kotlin/com/bupt/cyclingmusic/
        │   ├── MainActivity.kt           # 主界面：踏频/距离/时间显示、训练模式入口
        │   ├── HistoryActivity.kt        # 历史骑行记录列表
        │   ├── RidingSummaryActivity.kt  # 单次骑行结束总结页
        │   ├── RidingService.kt          # 前台 Service：计时、计距、计卡路里、写库
        │   ├── service/
        │   │   ├── CyclingSensorService.kt  # 传感器采样与踏频计算
        │   │   └── MusicSyncService.kt      # BPM 分档与防抖切歌
        │   └── data/
        │       ├── RidingDatabase.kt
        │       ├── database/DatabaseHelper.kt
        │       ├── dao/RidingRecordDao.kt
        │       ├── entity/RidingRecord.kt
        │       └── converter/DateConverter.kt
        └── res/                  # 布局、主题、资源
```

## 架构概览

```
 ┌─────────────┐  cadence  ┌──────────────────────┐  bpm   ┌──────────────────┐
 │ Sensors     │──────────▶│ CyclingSensorService │───────▶│ MusicSyncService │
 │ (accel/gyro)│           │  (滤波 + 峰值检测)    │        │ (BPM 分档 + 防抖)│
 └─────────────┘           └──────────────────────┘        └──────────────────┘
                                     │
                                     ▼
                         ┌──────────────────────┐  insert  ┌─────────────┐
                         │ RidingService (前台) │─────────▶│ Room (SQLite)│
                         │ 计时 / 距离 / 卡路里 │          │ riding_records│
                         └──────────────────────┘          └─────────────┘
                                     │
                                     ▼ Broadcast
                            RidingSummaryActivity
```

- `MainActivity` 通过 `bindService` 同时绑定三个 Service，UI 线程以 500ms 周期轮询当前踏频/距离/时间并刷新。
- 踏频由 `CyclingSensorService` 在独立 `HandlerThread` 上计算，避免主线程阻塞。
- 切歌策略：踏频跨档后启动 5 秒计时，期间档位回稳则取消切换，以避免红绿灯/暂歇造成的频繁切换。
- 距离按 `cadence × 2.1m × 时长(min)` 估算（轮周长 2.1m）；卡路里按 MET 法估算（默认体重 70kg）。

## 权限

应用在 `AndroidManifest.xml` 中声明以下权限：

- `BODY_SENSORS` — 加速度计 / 陀螺仪
- `POST_NOTIFICATIONS` — 前台服务通知（Android 13+）
- `READ_MEDIA_AUDIO` / `READ_EXTERNAL_STORAGE` — 读取本地音乐文件
- `FOREGROUND_SERVICE` 及 `FOREGROUND_SERVICE_DATA_SYNC` / `_HEALTH` / `_MEDIA_PLAYBACK` — Android 14 前台服务类型要求

应用首次启动时会动态申请所需的运行时权限。

## 构建与运行

### 环境要求
- Android Studio Hedgehog (2023.1.1) 或更新版本
- JDK 17（AGP 8.x 要求）
- Android SDK Platform 34

### 步骤
1. 克隆仓库后用 Android Studio 打开 `CyclingMusic/` 目录。
2. 首次同步 Gradle，等待依赖下载完成。
3. 连接 Android 设备（建议 Android 12+ 真机，模拟器无真实传感器信号）。
4. 运行 `app` 模块即可安装到设备。

也可使用命令行构建：

```bash
cd CyclingMusic
./gradlew :app:assembleDebug
```

> Windows 用户使用 `gradlew.bat`。

### 音乐文件准备

`MusicSyncService` 内置三档示例路径（未配置时的回退）：

```
/storage/emulated/0/Music/slow_track.mp3
/storage/emulated/0/Music/medium_track.mp3
/storage/emulated/0/Music/fast_track.mp3
```

如需使用，请将自备的慢/中/快节奏 MP3 文件按上述命名放入设备 `Music` 目录；也可通过 `MusicSyncService.LocalBinder.addTrack(tier, path)` 动态注册自定义曲库。

## 使用流程

1. 启动应用，授予传感器、通知、媒体权限。
2. （可选）点击"训练模式"，输入目标踏频与容差。
3. 点击"开始骑行"，将手机固定在车架/口袋/手臂上保持稳定姿态。
4. 实时观察踏频、距离、时长；训练模式下按界面提示调整节奏。
5. 点击"停止骑行"，跳转单次骑行总结页；记录自动写入数据库。
6. 在"历史记录"中浏览既往骑行数据。

## 后续规划

- 接入外置 BLE 踏频传感器以提高精度
- 接入 GPS 实测距离与轨迹
- 用户体重 / 轮径可配置
- 自定义 BPM 区间与曲库管理界面
- 导出/分享骑行记录

## 许可证

仅用于学习与课程演示，未指定开源许可证。
