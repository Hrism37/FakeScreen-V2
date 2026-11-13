# 应用更新与数据保留

## 结论

FakeScreen 的 PIN、设置和未完成会话记录都保存在应用私有目录：

- PIN：`SharedPreferences`（AES/GCM 加密后存储，密钥在 Android Keystore）
- 设置：DataStore Preferences
- 会话与亮度快照：`SharedPreferences`（同步 `commit()` 落盘）

**覆盖安装（同一个包名、同一签名、versionCode 更大）不会清除这些数据。** 不需要卸载旧版本。

## 正确更新步骤

1. 保留同一份签名密钥：`release.keystore` + `keystore.properties`。
2. 修改代码后把 `app/build.gradle.kts` 的 `versionCode` 加 1（当前为 3），`versionName` 同步改。
3. 构建新的 release APK：
   ```bash
   ./gradlew :app:assembleRelease
   ```
4. 直接安装新 APK（不要先卸载）。系统会走“更新”而不是“全新安装”。
5. 打开应用，PIN、默认模式、最长会话等设置保持不变；`MY_PACKAGE_REPLACED` 接收器会检查是否有未完成的会话并按规则恢复亮度。

## 什么情况下会丢数据

- **卸载应用**（包括先卸载再安装）：Android 会删除应用私有数据，PIN 和设置都会丢失。
- **换了签名密钥**：Android 拒绝覆盖安装，只能卸载重装，数据丢失。
- **清除应用数据**：系统设置中“存储 → 清除数据”。
- **恢复出厂设置 / 换设备**：本应用 `allowBackup=false`，不会通过云备份恢复 PIN（这是有意的安全设计）。

## 为什么 PIN 不参与云备份

PIN 记录用本机 Keystore 密钥加密，换设备后无法解密；`allowBackup=false` 也避免把加密记录同步到云端。换设备需要重新设置 PIN。

## 开发者注意

- 不要修改 `applicationId`（`com.fakescreen.app`），否则会被当成另一个应用。
- 不要修改 `SessionStore` / `PasswordManager` 的 SharedPreferences 文件名，否则升级后读不到旧数据。
- `versionCode` 必须递增；相同 versionCode 的覆盖安装可能被部分系统拒绝。
- 发布前确认 `keystore.properties` 指向正确密钥；GitHub 仓库不包含该文件（`.gitignore` 已排除）。
