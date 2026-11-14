# FakeScreen V2

FakeScreen 是一个在真实 Android 设备上运行的“模拟息屏”工具：用全屏纯黑悬浮窗覆盖屏幕，配合 PIN 密码退出，并按需把亮度降到最低。它不是系统锁屏，不会真正关机，也不会拦截电源键、来电或紧急呼叫。

- 语言：Kotlin
- UI：Jetpack Compose + Material 3（深色）
- 架构：MVVM + Repository，服务端会话状态机
- 最低系统：Android 8.0（API 26），targetSdk 35
- 版本：2.1.1 (versionCode 4)

## 功能

| 能力 | 状态 |
| --- | --- |
| 模式 A：纯黑覆盖（OLED），不修改系统亮度 | 已实现 |
| 模式 B：黑屏 + 低亮度（窗口级优先，系统级可选） | 已实现 |
| 模式 C：Shizuku 真熄屏（调用系统电源键） | 已实现（需要 Shizuku 授权） |
| 黑屏优先：默认只有纯黑，轻触才显示 PIN 键盘 | 已实现 |
| 低亮度模式下显示 PIN 时临时提高窗口亮度 | 已实现 |
| PIN 设置、二次确认、修改、错误锁定 | 已实现 |
| PBKDF2-HMAC-SHA256 + Android Keystore 加密存储 | 已实现 |
| 音量加 + 音量减：开启/关闭两个独立开关 | 已实现 |
| 前台服务 + 常驻通知（不再提供免 PIN 的紧急结束） | 已实现 |
| 悬浮窗丢失、进程被杀、重启后的恢复 | 已实现 |
| 覆盖安装升级保留 PIN、设置与会话文件 | 已实现 |
| 平板横竖屏自适应 PIN 键盘 | 已实现 |
| MagicOS 11 / 荣耀平板真机验证 | **部分完成**：安装、UI、Shizuku 授权已验证；会话/亮度待手动静默授权后测试 |

## 三种模式

**A. 纯黑覆盖（OLED）**
全屏 `TYPE_APPLICATION_OVERLAY` 黑色窗口，尽可能隐藏状态栏和导航栏，`FLAG_KEEP_SCREEN_ON` 保持显示。不读取也不写入系统亮度，退出时只需移除窗口。

**B. 黑屏 + 低亮度（LCD）**
在 A 的基础上把悬浮窗的 `screenBrightness` 设为 `0.01f`（1%，绝不为 0）。这是窗口级亮度，窗口移除即失效，不需要任何权限，也不改系统设置。轻触黑屏显示 PIN 时，窗口亮度临时提高到 35%，键盘自动隐藏后回到 1%。只有用户在设置中显式开启“系统级亮度备用”并授予 `WRITE_SETTINGS` 后，才会把系统亮度临时调到 `4/255` 并切到手动模式；退出时恢复原值和原来的自动/手动模式。

**C. Shizuku 真熄屏**
通过 Shizuku 13 的 UserService + AIDL，在 shell（uid 2000）进程中执行 `input keyevent 26`，让屏幕真正熄灭，而不是覆盖一层黑色。Android 没有普通应用可用的“直接切断屏幕供电”公开 API，因此在应用内明确说明：这里使用的是系统电源键的官方效果。需要 Shizuku 已安装、运行并授权；不可用时自动降级为纯黑覆盖，并在通知中说明。

所有模式下，正常状态都是**只有纯黑**：
- 不显示 PIN 键盘、不显示提示文字；
- 轻触屏幕任意位置后，PIN 键盘淡入；
- 20 秒无操作自动隐藏，回到纯黑；
- 模式 B 在显示键盘时自动提亮，隐藏后恢复低亮度。

## 亮度恢复策略

1. 进入前读取 `SCREEN_BRIGHTNESS` / `SCREEN_BRIGHTNESS_MODE`，连同会话 UUID、时间戳、是否修改全局亮度写入 `SharedPreferences`（`commit()` 同步落盘），**先持久化再改亮度**。
2. 正常退出：先恢复亮度，再移除黑色窗口，最后清理会话记录。
3. 异常退出：下次启动 App、服务被系统重启、或设备重启后，`RecoveryManager` 检查记录：
   - 超过 12 小时（无最大会话时长时）或超过配置时长 + 5 分钟 → 视为过期，只清理记录，不写亮度；
   - 当前亮度 ≠ 本应用写入的值 → 说明用户后来改过，**不覆盖**；
   - 亮度模式也被改过 → 同样不覆盖；
   - 恢复失败（例如 `WRITE_SETTINGS` 被撤销）→ 保留记录，授权后下次启动重试；
   - 否则恢复原亮度与原模式。
4. 所有写入都不会是 0：窗口级最低 0.01，系统级最低 4/255。

## 密码与安全

- 4–8 位纯数字 PIN，PBKDF2-HMAC-SHA256（210,000 次迭代、16 字节随机盐、256 位输出）。
- 只保存派生哈希，不保存明文；整个记录用 Android Keystore 中不可导出的 AES-256-GCM 密钥加密，复制应用私有数据到别的设备无法通过校验。
- 恒定时间比较；失败次数和锁定时间加密后持久化，清设置不会重置计数。
- 第 5 次起逐步延迟（5s/15s/30s/60s/120s），第 10 次锁定 5 分钟。
- 第 10 次错误且 Shizuku 可用时，通过 Shizuku 执行 `input keyevent 26` 真正关闭屏幕；Shizuku 不可用时只锁定输入并给出明确提示，不会谎称已经熄屏。
- **通知栏不再提供“紧急结束”**。之前那会绕过 PIN，任何人拉开通知栏都能结束会话，等同于没有密码。
- PIN 只保护 FakeScreen 的退出流程，不伪装系统锁屏，不拦截电源键与紧急功能。

## 紧急退出（重要）

忘记 PIN 时：

1. **同时按音量加和音量减**：空闲时开始模拟息屏，会话中结束会话。设置里“音量键开启”和“音量键关闭”是两个独立开关；关闭动作不验证 PIN，是物理按键层面的紧急出口。要在应用外（后台）使用，需要开启无障碍服务；会话中覆盖层自己也能捕获组合键。
2. 长按电源键关机/重启（系统始终有效）。
3. `adb shell am force-stop com.fakescreen.app`，下次打开 App 会自动恢复亮度。
4. 等待“最长会话时长”自动退出；可填任意分钟数（最小 0，无上限），也可以打开“无最大会话时长”。0 与“无最大”都表示不自动结束。
5. PIN 连续错误 10 次时，若 Shizuku 可用，应用会真正熄屏。

音量组合键的“关闭”不需要 PIN 是有意的取舍：它比通知栏按钮更难被旁观者触发，并且必须同时按住两个键。如果你希望完全只靠 PIN，请在设置 → 快捷操作中关闭“音量键关闭”开关。

## Shizuku（可选）

- 两个用途：①“Shizuku 真熄屏”模式启动时关闭屏幕；② PIN 错误过多时真正熄屏。
- 使用 Shizuku 13 官方推荐的 **UserService + AIDL** 方式：在 Shizuku 拉起的 shell 进程（uid 2000）中运行一个只有 `turnScreenOff()` 的极小服务，内部执行 `input keyevent 26`。不提供通用 shell 桥，不接受任意命令。
- 不参与密码验证、不参与悬浮窗创建，未安装/未授权时全部核心功能正常；真熄屏模式会自动降级为纯黑覆盖。
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
- `VolumeComboDetectorTest`：5 项通过。
- **合计 25 项，0 失败，0 跳过**；`assembleDebug` 与 `assembleRelease` 均构建成功（2.1.0 / 2.1.1）。

真机（2026-11-13，HONOR ROD2-W69 / MagicOS_11.0.0 / Android 17，**针对 2.0.0**）：

- 已安装并启动 release 包，首页、设置、PIN 页面、兼容性报告正常渲染；
- Shizuku 从“未授权”到“已就绪”的授权流程在 MagicOS 11 上验证通过；
- 未授予悬浮窗权限时，开始按钮正确跳转系统授权页；
- 悬浮窗、亮度、PIN 退出、通知、旋转、异常恢复仍需悬浮窗授权后的人工测试。

2.1.0 / 2.1.1（黑屏优先、触摸显示 PIN、音量键开启/关闭、Shizuku 真熄屏、数字时长输入、无障碍音量键修复）已完成构建与单元测试，但**按用户要求停止真机测试**，未在设备上安装验证。

详见 [docs/DEVICE_TEST_2026-11-13.md](docs/DEVICE_TEST_2026-11-13.md) 与 [docs/TESTING.md](docs/TESTING.md)。

未执行：

- 全屏覆盖、亮度、PIN 退出等会话级真机测试（系统权限页需手动操作）。
- Compose UI 自动化测试（`app/src/androidTest` 已提供骨架，但需要设备/模拟器）。

## 荣耀 MagicOS 11 适配

已在设置页和报告中给出可操作的引导（应用启动管理、忽略电池优化、悬浮窗权限、通知权限），并针对以下系统行为做了防御：

- 前台服务被回收：`START_STICKY` + 持久化记录 + 重新展示覆盖层；
- 悬浮窗被移除：`OnAttachStateChangeListener` 检测并结束会话、恢复亮度；
- 系统重启：`BOOT_COMPLETED` 恢复检查（不自动弹窗）；
- 屏幕旋转：`DisplayManager` 监听 + Compose 自适应；
- 状态栏/导航栏：`WindowInsetsController` 隐藏，可下滑临时呼出。

2.0.0 已在荣耀平板（HONOR ROD2-W69 / MagicOS_11.0.0 / Android 17）上完成安装、UI 与 Shizuku 授权的部分验证；2.1.0 / 2.1.1 尚未真机验证。具体未验证项与预期风险见 [docs/MAGICOS11.md](docs/MAGICOS11.md)。

## 已知问题

见 [docs/KNOWN_ISSUES.md](docs/KNOWN_ISSUES.md)。主要包括：

- 窗口级亮度并非所有 OEM 都生效；系统级备用需要 `WRITE_SETTINGS` 且会短暂修改全局亮度。
- 无法保证进程永不被系统终止；应用不声明“永不掉线”。
- 音量加+减组合键结束会话不需要 PIN；这是用户要求的物理紧急出口，可在设置中关闭。
- Shizuku 真熄屏通过系统电源键实现，Android 没有普通应用可用的直接切断面板供电 API。
- 若 Keystore 记录损坏（如清除锁屏密码导致密钥失效），PIN 会被视为未设置，需要重新设置。

## 隐私

不联网、不收集、不上传任何数据。所有 PIN 哈希、会话记录、设置都只保存在本机应用私有目录。兼容性报告只包含设备型号、系统版本和权限状态。
