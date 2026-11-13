# 编译说明

## 环境要求

| 组件 | 版本 |
| --- | --- |
| JDK | 17（AGP 8.7 要求） |
| Android SDK Platform | 35 |
| Android Build-Tools | 35.0.0（AGP 会自动安装 34，建议显式使用 35） |
| Gradle | 8.11.1（随 Wrapper 提供） |
| Kotlin | 2.0.21 |
| AGP | 8.7.3 |

Windows、macOS、Linux（x86_64/arm64）均可构建；项目不依赖任何 NDK 或原生模块。

## 命令行构建

```bash
# 1. 指定 SDK
export ANDROID_HOME="$HOME/Android/Sdk"       # 或写进 local.properties: sdk.dir=...
export JAVA_HOME="/path/to/jdk-17"

# 2. Debug APK
./gradlew :app:assembleDebug
# app/build/outputs/apk/debug/app-debug.apk

# 3. 单元测试
./gradlew :app:testDebugUnitTest

# 4. 已签名 Release APK
./gradlew :app:assembleRelease
# app/build/outputs/apk/release/app-release.apk
```

`local.properties` 与 `keystore.properties` 都不进版本库。

## 签名

仓库不包含签名密钥。正式打包时在项目根目录新建 `keystore.properties`：

```properties
storeFile=/absolute/path/to/release.keystore
storePassword=******
keyAlias=fakescreen
keyPassword=******
```

没有该文件时 `assembleRelease` 会生成未签名 APK，`assembleDebug` 始终使用调试签名。

## 在 ARM64 Linux 上构建（本项目的开发环境）

Google 的 `aapt2` 只有 x86_64 Linux 二进制。在 ARM64 主机（例如 Termux/proot Ubuntu）上需要：

```bash
# 安装 qemu 用户态与 x86_64 glibc
sudo dpkg --add-architecture amd64
sudo apt update
sudo apt install qemu-user-static libc6:amd64 libstdc++6:amd64 zlib1g:amd64

# 用官方 build-tools 的 aapt2 做一层包装
cat > /opt/toolchain/aapt2 <<'EOF'
#!/bin/sh
exec /usr/bin/qemu-x86_64-static "$ANDROID_HOME/build-tools/35.0.0/aapt2" "$@"
EOF
chmod +x /opt/toolchain/aapt2

# 构建时覆盖 aapt2，无需修改仓库
./gradlew :app:assembleDebug -Pandroid.aapt2FromMavenOverride=/opt/toolchain/aapt2
```

AIDL 也会用到 Build-Tools 里的 `aidl` 二进制，同一台 ARM64 主机上需要同样包一层（项目的 Shizuku 用户服务用到了 AIDL）：

```bash
D="$ANDROID_HOME/build-tools/35.0.0"
mv "$D/aidl" "$D/aidl.real"
printf '#!/bin/sh\nexec /usr/bin/qemu-x86_64-static %s/aidl.real "$@"\n' "$D" > "$D/aidl"
chmod +x "$D/aidl"
```

x86_64 主机不需要这一步，正常 `./gradlew assembleDebug` 即可。仓库中的 `gradle.properties` 不含任何主机相关路径。

## 常见问题

**`Failed to install the following Android SDK packages ... licenses`**
先执行 `sdkmanager --licenses` 接受许可，或安装 Android Studio 后打开一次 SDK Manager。

**`AAPT2 aapt2-...-linux.jar` 无法执行 / Exec format error**
说明在 ARM64 主机上直接跑了 x86_64 的 aapt2，按上一节配置 qemu 包装。

**OOM / Gradle daemon 被杀**
`gradle.properties` 已把堆限制在 1.5 GB；内存更小的机器可再降到 `-Xmx1024m`，并关闭并行（默认已关闭）。

**Gradle 下载慢**
可配置镜像或使用本机已下载的 Gradle 发行版：`/path/to/gradle/bin/gradle :app:assembleDebug`。

## CI

`.github/workflows/android.yml` 在 push / 手动触发时构建 Debug APK 并上传 artifact，不需要签名密钥。
