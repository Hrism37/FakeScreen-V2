# 测试说明与结果

## 已执行的自动化测试

JVM 单元测试（`./gradlew :app:testDebugUnitTest`）：

| 测试类 | 覆盖内容 |
| --- | --- |
| `PinHasherTest` | PBKDF2 派生确定性、不同 PIN/盐产生不同结果、输出 256 位、调用方数组不被清空 |
| `LockoutPolicyTest` | 前 4 次免费、5–10 次延迟递增、第 10 次触发隐私锁、剩余次数不为负 |
| `RecoveryPlannerTest` | 正常恢复、用户改亮度/模式时不覆盖、超过 12 小时不恢复、仅窗口亮度无需写系统、关闭自动恢复、原始亮度未知时不写 |
| `SessionRecordTest` | JSON 往返、窗口亮度 null 往返、损坏 JSON 拒绝、年龄不为负 |
| `VolumeComboDetectorTest` | 音量加+减组合键的窗口判定、重复按键不重复触发、松开后重新武装 |

执行命令：

```bash
./gradlew :app:testDebugUnitTest
# 报告：app/build/reports/tests/testDebugUnitTest/index.html
```

本次实际执行结果（2026-11-13）：

| 测试类 | 用例数 | 失败 | 跳过 |
| --- | ---: | ---: | ---: |
| `PinHasherTest` | 5 | 0 | 0 |
| `LockoutPolicyTest` | 4 | 0 | 0 |
| `RecoveryPlannerTest` | 7 | 0 | 0 |
| `SessionRecordTest` | 4 | 0 | 0 |
| `VolumeComboDetectorTest` | 5 | 0 | 0 |
| **合计** | **25** | **0** | **0** |

构建结果：`:app:assembleDebug`、`:app:assembleRelease` 均成功；测试报告位于 `app/build/reports/tests/testDebugUnitTest/`。

## 真机验证（2026-11-13，2.0.0）

> 本节针对 2.0.0。2.1.0 的黑屏优先、触摸显示 PIN、音量键快捷开关、Shizuku 真熄屏按用户要求停止真机测试，未安装验证。

已在 HONOR ROD2-W69（Android 17 / API 37 / MagicOS_11.0.0.110）上完成：

- release APK 安装、启动；
- 首页、设置、PIN 设置界面、兼容性报告的渲染；
- 设备型号与 MagicOS 版本识别；
- Shizuku 未授权检测 → 授权申请 → “已就绪”；
- 缺少悬浮窗权限时点击“开始模拟息屏”正确跳转系统授权页。

未完成：悬浮窗授权后的会话、亮度、PIN 退出、通知、旋转、异常恢复。系统设置页由手机操控安全策略限制，只能由用户手动操作。

完整记录见 [DEVICE_TEST_2026-11-13.md](DEVICE_TEST_2026-11-13.md)。

## 编译验证

- `./gradlew :app:assembleDebug`：用于生成本次交付的 APK。
- `./gradlew :app:assembleRelease`：使用项目根目录的 `keystore.properties` 签名。

## Compose UI 测试（需要设备/模拟器）

`app/src/androidTest` 提供了 `OverlayScreenTest` 骨架，验证：

- 输入满 PIN 长度后触发一次 `onSubmit`；
- 锁定状态下按键不产生输入。

运行：

```bash
./gradlew :app:connectedDebugAndroidTest
```

2.0.0 曾安装到真机，但自动化环境无法对系统安装/权限页执行操作；2.1.0 按用户要求停止测试。因此 connected 测试未执行。

## 真机手工测试清单

以下项目必须在荣耀平板 GT Pro（MagicOS 11）与其他 Android 手机上执行。当前状态一律为“未执行”，不得视为通过。

| # | 项目 | 步骤 | 预期 | 状态 |
| --- | --- | --- | --- | --- |
| 1 | 模式 A 启动 | 设置 PIN → 开始 | 全屏纯黑，状态栏/导航栏尽量隐藏 | 未执行 |
| 2 | 模式 B 启动 | 选择低亮度模式 → 开始 | 黑屏且亮度明显降低 | 未执行 |
| 3 | PIN 退出 | 输入正确 PIN | 恢复亮度、移除覆盖层、通知消失 | 未执行 |
| 4 | PIN 错误 | 输入错误 PIN | 不退出、提示错误、按键抖动/清空 | 未执行 |
| 5 | 错误延迟 | 连续输错 5/6/7 次 | 分别锁定 5/15/30 秒 | 未执行 |
| 6 | 错误熄屏 | 连续输错 10 次（Shizuku 已授权） | 屏幕真正熄灭 | 未执行 |
| 7 | 错误熄屏降级 | 同上但无 Shizuku | 仅锁定输入，提示无法熄屏 | 未执行 |
| 8 | 亮度恢复 | 进入前记录亮度 → 退出 | 恢复原值与自动/手动模式 | 未执行 |
| 9 | 用户改亮度 | 会话中从通知栏/设置改亮度 → 退出 | 不被旧值覆盖（异常恢复路径） | 未执行 |
| 10 | 进程被杀 | `adb shell am kill com.fakescreen.app` → 重开 | 提示已恢复亮度或恢复会话 | 未执行 |
| 11 | 服务被杀 | 系统设置中强制停止 → 重开 | 恢复亮度、清理记录 | 未执行 |
| 12 | 悬浮窗撤销 | 会话中撤销悬浮窗权限 | 会话结束、亮度恢复、有提示 | 未执行 |
| 13 | 重启恢复 | 会话中重启设备 | 开机后检查记录并按规则恢复 | 未执行 |
| 14 | 通知紧急结束 | 下拉通知栏 → 紧急结束 | 不输 PIN 直接退出并恢复亮度 | 未执行 |
| 15 | 最长会话 | 设为 5 分钟并等待 | 自动退出、通知提示 | 未执行 |
| 16 | 横屏 | 旋转到横屏 | PIN 键盘完整可见、可操作 | 未执行 |
| 17 | 竖屏 | 旋转到竖屏 | 同上 | 未执行 |
| 18 | 平板大屏 | 12 英寸平板横屏 | 内容居中、不拉伸 | 未执行 |
| 19 | 应用切换 | 会话中切到其他应用 | 覆盖层仍在最上层 | 未执行 |
| 20 | 紧急功能 | 会话中拨 110/120、触发闹钟 | 系统紧急功能不被拦截 | 未执行 |
| 21 | 电源键 | 会话中按电源键 | 正常熄屏/亮屏，覆盖层仍在 | 未执行 |
| 22 | Shizuku 撤销 | 会话前撤销 Shizuku 授权 | 状态变为“未授权”，核心功能正常 | 未执行 |
| 23 | 无 Shizuku | 未安装 Shizuku | 全部核心功能可用 | 未执行 |
| 24 | 通知权限拒绝 | 拒绝通知权限后开始 | 会话可运行，通知不可见 | 未执行 |
| 25 | 黑屏优先 / 触摸显示 PIN | 开始会话 → 观察 | 纯黑无 UI；轻触后显示 PIN；20 秒隐藏 | 未执行（2.1.0） |
| 26 | 音量键会话内退出 | 会话中同时按音量加+减 | 直接退出并恢复亮度 | 未执行（2.1.0） |
| 27 | 音量键后台开始 | 开启无障碍后，桌面按组合键 | 启动会话 | 未执行（2.1.0） |
| 28 | Shizuku 真熄屏 | 选模式 C 并开始 | 屏幕真正熄灭；唤醒后覆盖层仍在 | 未执行（2.1.0） |
| 29 | 覆盖升级 | 2.0.0 上直接安装 2.1.0 | PIN/设置保留 | 未执行（2.1.0） |

## 验证命令参考

```bash
adb shell dumpsys window | grep -i "FakeScreenOverlay"
adb shell settings get system screen_brightness
adb shell settings get system screen_brightness_mode
adb shell am force-stop com.fakescreen.app
adb logcat -s FakeScreen
```

> 荣耀设备可能限制 `adb` 与第三方应用安装，需在开发者选项中开启“USB 调试”和“仅充电模式下允许 ADB 调试”。
