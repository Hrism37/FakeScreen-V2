# FakeScreen V2

FakeScreen 是一个在真实 Android 设备上运行的“模拟息屏”工具：用全屏纯黑悬浮窗覆盖屏幕，配合 PIN 密码退出，并按需把亮度降到最低。它不是系统锁屏，不会真正关机，也不会拦截电源键、来电或紧急呼叫。

- 语言：Kotlin
- UI：Jetpack Compose + Material 3（深色）
- 架构：MVVM + Repository，服务端会话状态机
- 最低系统：Android 8.0（API 26），targetSdk 35
- 版本：2.0.0 (versionCode 2)

## 功能

| 能力 | 状态 |
| --- | --- |
| 模式 A：纯黑覆盖（OLED），不修改系统亮度 | 已实现 |
| 模式 B：黑屏 + 低亮度（窗口级优先，系统级可选） | 已实现 |
| 亮度快照 / 恢复 / 异常恢复 | 已实现 |
| PIN 设置、二次确认、修改、错误锁定 | 已实现 |
| PBKDF2-HMAC-SHA256 + Android Keystore 加密存储 | 已实现 |
| 前台服务 + 常驻通知 + 紧急结束 | 已实现 |
| 悬浮窗丢失、进程被杀、重启后的恢复 | 已实现 |
| Shizuku 安装/服务/授权检测，错误过多真正熄屏 | 已实现（可选） |
| 平板横竖屏自适应 PIN 键盘 | 已实现 |
| MagicOS 11 / 荣耀平板 GT Pro 真机验证 | **未完成**（见下文） |

## 两种模式

**A. 纯黑覆盖（OLED）**
全屏 `TYPE_APPLICATION_OVERLAY` 黑色窗口，尽可能隐藏状态栏和导航栏，`FLAG_KEEP_SCREEN_ON` 保持显示。不读取也不写入系统亮度，退出时只需移除窗口。

**B. 黑屏 + 低亮度（LCD）**
在 A 的基础上把悬浮窗的 `screenBrightness` 设为 `0.01f`（1%，绝不为 0）。这是窗口级亮度，窗口移除即失效，不需要任何权限，也不改系统设置。只有用户在设置中显式开启“系统级亮度备用”并授予 `WRITE_SETTINGS` 后，才会把系统亮度临时调到 `4/255` 并切到手动模式；退出时恢复原值和原来的自动/手动模式。

## 亮度恢复策略

1. 进入前读取 `SCREEN_BRIGHTNESS` / `SCREEN_BRIGHTNESS_MODE`，连同会话 UUID、时间戳、是否修改全局亮度写入 `SharedPreferences`（`commit()` 同步落盘），**先持久化再改亮度**。
2. 正常退出：先恢复亮度，再移除黑色窗口，最后清理会话记录。
3. 异常退出：下次启动 App、服务被系统重启、或设备重启后，`RecoveryManager` 检查记录：
   - 超过 12 小时 → 视为过期，只清理记录，不写亮度；
   - 当前亮度 ≠ 本应用写入的值 → 说明用户后来改过，**不覆盖**；
   - 亮度模式也被改过 → 同样不覆盖；
   - 否则恢复原亮度与原模式。
4. 所有写入都不会是 0：窗口级最低 0.01，系统级最低 4/255。

## 密码与安全

- 4–8 位纯数字 PIN，PBKDF2-HMAC-SHA256（210,000 次迭代、16 字节随机盐、256 位输出）。
- 只保存派生哈希，不保存明文；整个记录用 Android Keystore 中不可导出的 AES-256-GCM 密钥加密，复制应用私有数据到别的设备无法通过校验。
- 恒定时间比较；失败次数和锁定时间加密后持久化，清设置不会重置计数。
- 第 5 次起逐步延迟（5s/15s/30s/60s/120s），第 10 次锁定 5 分钟。
- 第 10 次错误且 Shizuku 可用时，通过 Shizuku 执行 `input keyevent 26` 真正关闭屏幕；Shizuku 不可用时只锁定输入并给出明确提示，不会谎称已经熄屏。
- PIN 只保护 FakeScreen 的退出流程，不伪装系统锁屏，不拦截电源键与紧急功能。

## 紧急退出（重要）

忘记 PIN 时：

1. 下拉通知栏 → “紧急结束”（不需要 PIN，这是有意的安全出口）。
2. 长按电源键关机/重启（系统始终有效）。
3. `adb shell am force-stop com.fakescreen.app`，下次打开 App 会自动恢复亮度。
4. 等待“最长会话时长”（默认 30 分钟，可设 5–120 分钟）自动退出。

第 1 条意味着能拿到通知栏的人可以结束会话。这是“可靠恢复”和“密码保护”之间的取舍，已在设置页明确说明。

## Shizuku（可选）

- 用于状态检测，以及“错误过多时真正熄屏”。
- 使用 Shizuku 13 官方推荐的 **UserService + AIDL** 方式：在 Shizuku 拉起的 shell 进程（uid 2000）中运行一个只有 `turnScreenOff()` 的极小服务，内部执行 `input keyevent 26`。不提供通用 shell 桥，不接受任意命令。
- 不参与密码验证、不参与悬浮窗创建，未安装/未授权时全部核心功能正常。
- Shizuku 13 已移除旧的 `newProcess` API，因此没有使用它，也没有调用未经验证的系统接口。

## 构建

需要 JDK 17、Android SDK Platform 35、Build-Tools 35.0.0。

```bash
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk

# 已签名的 release（需要 keystore.properties，见下）
./gradlew :app:assembleRelease
```

`keystore.properties` 格式（不进版本库）：

```properties
storeFile=release.keystore
storePassword=******
keyAlias=fakescreen
keyPassword=******
```

更详细的编译说明见 [docs/BUILD.md](docs/BUILD.md)。

## 项目结构

```
app/src/main/java/com/fakescreen/app/
├── FakeScreenApp.kt / AppContainer.kt     应用与手动依赖容器
├── core/                                  常量、模式、设置模型、日志、时间格式
├── data/                                  设置 DataStore、会话记录、崩溃安全存储
├── security/                              PBKDF2、Keystore 加密、锁定策略、PIN 管理
├── brightness/                            亮度读写/恢复、纯逻辑恢复决策
├── overlay/                               WindowManager 悬浮窗、Compose 宿主、PIN 界面
├── session/                               会话状态机、UI 状态
├── service/                               前台服务、开机恢复、紧急退出接收器、通知
├── keepawake/                             FLAG_KEEP_SCREEN_ON 配套与实验性静音音频
├── shizuku/                               Shizuku 状态与受限 shell
├── recovery/                              启动/重启时的一致性恢复
├── device/                                设备信息与兼容性报告
└── ui/                                    Compose 主题、主页、设置、模式、PIN、报告
```

## 测试

本次构建已执行（2026-11-13，JVM 单元测试）：

- `PinHasherTest`：5 项通过。
- `LockoutPolicyTest`：4 项通过。
- `RecoveryPlannerTest`：7 项通过。
- `SessionRecordTest`：4 项通过。
- **合计 20 项，0 失败，0 跳过**；`assembleDebug` 与 `assembleRelease` 均构建成功。

未执行：

- 真机安装与交互测试。
- Compose UI 自动化测试（`app/src/androidTest` 已提供骨架，但需要设备/模拟器）。
- 荣耀平板 GT Pro / MagicOS 11 专项验证。

详见 [docs/TESTING.md](docs/TESTING.md)。

## 荣耀 MagicOS 11 适配

已在设置页和报告中给出可操作的引导（应用启动管理、忽略电池优化、悬浮窗权限、通知权限），并针对以下系统行为做了防御：

- 前台服务被回收：`START_STICKY` + 持久化记录 + 重新展示覆盖层；
- 悬浮窗被移除：`OnAttachStateChangeListener` 检测并结束会话、恢复亮度；
- 系统重启：`BOOT_COMPLETED` 恢复检查（不自动弹窗）；
- 屏幕旋转：`DisplayManager` 监听 + Compose 自适应；
- 状态栏/导航栏：`WindowInsetsController` 隐藏，可下滑临时呼出。

**未在荣耀平板 GT Pro（MagicOS 11）真机上验证。** 具体未验证项与预期风险见 [docs/MAGICOS11.md](docs/MAGICOS11.md)。

## 已知问题

见 [docs/KNOWN_ISSUES.md](docs/KNOWN_ISSUES.md)。主要包括：

- 窗口级亮度并非所有 OEM 都生效；系统级备用需要 `WRITE_SETTINGS` 且会短暂修改全局亮度。
- 无法保证进程永不被系统终止；应用不声明“永不掉线”。
- 通知栏“紧急结束”是无密码出口，属于有意设计。
- 若 Keystore 记录损坏（如清除锁屏密码导致密钥失效），PIN 会被视为未设置，需要重新设置。

## 隐私

不联网、不收集、不上传任何数据。所有 PIN 哈希、会话记录、设置都只保存在本机应用私有目录。兼容性报告只包含设备型号、系统版本和权限状态。
