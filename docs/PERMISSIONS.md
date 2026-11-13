# 权限配置说明

FakeScreen 遵循最小权限原则。下表列出全部权限、用途、请求时机，以及被拒绝后的降级行为。

| 权限 | 类型 | 用途 | 请求时机 | 拒绝后的行为 |
| --- | --- | --- | --- | --- |
| `SYSTEM_ALERT_WINDOW` | 特殊权限（设置页） | 创建全屏黑色悬浮窗 | 第一次点“开始模拟息屏”时跳转系统设置 | 无法开始会话，主页状态行提示“去授权” |
| `FOREGROUND_SERVICE` | 普通 | 会话期间运行前台服务 | 安装时 | 无法运行，属于硬依赖 |
| `FOREGROUND_SERVICE_SPECIAL_USE` | 普通 | Android 14+ 前台服务类型声明 | 安装时 | 无法启动前台服务 |
| `POST_NOTIFICATIONS` | 运行时（Android 13+） | 显示“正在模拟息屏”常驻通知与紧急结束入口 | 开始会话前 | 服务仍可运行，但通知不可见，紧急结束入口不可用（仍有电源键与计时器） |
| `WRITE_SETTINGS` | 特殊权限（设置页） | 仅在开启“系统级亮度备用”时写系统亮度 | 用户打开该开关后手动授权 | 不写系统亮度，回退到窗口级亮度 |
| `RECEIVE_BOOT_COMPLETED` | 普通 | 重启后检查未完成的会话并恢复亮度 | 安装时 | 重启后不会主动恢复，需打开一次 App |
| `WAKE_LOCK` | 普通 | 仅供默认关闭的“实验性静音音频保活”使用 | 安装时 | 实验开关不可用，不影响主流程 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 普通 | 弹窗请求忽略电池优化（可选） | 用户点“电池优化”行时 | 不请求，仅提示手动设置 |

## 没有申请的权限

- 不申请 `BIND_DEVICE_ADMIN`、`SYSTEM_ALERT_WINDOW` 以外的系统窗口权限、`WRITE_SECURE_SETTINGS`。
- 不申请网络权限（`INTERNET`），应用完全离线。
- 不申请读取联系人、存储、位置、相机、麦克风、无障碍服务。
- 不使用 `QUERY_ALL_PACKAGES`；只通过 `<queries>` 声明 Shizuku 包名用于安装检测。

## Shizuku 授权

Shizuku 是独立应用，FakeScreen 通过其提供的 `ShizukuProvider` 申请运行时授权。授权不是安装时权限，随时可在 Shizuku 应用内撤销；撤销后 FakeScreen 自动降级，不影响会话与 PIN。

## 用户可见的系统设置入口

- 悬浮窗：`设置 → 应用 → 特殊权限 → 显示在其他应用上层`
- 修改系统设置：`设置 → 应用 → 特殊权限 → 修改系统设置`
- 电池优化：`设置 → 电池 → 电池优化`
- 通知：`设置 → 应用 → FakeScreen → 通知`
- 荣耀 MagicOS：`设置 → 应用 → 应用启动管理 → FakeScreen`
