# 验证记录

日期：2026-10-05

## 工具链

- Android SDK：`D:\Users\manxi\AppData\Local\Sdk`
- Android API 34 revision 3
- Android Platform-Tools 35.0.2
- Gradle JDK：JetBrains Runtime 17.0.11
- AGP 8.6.1、Gradle 8.7、Kotlin 1.9.0、Compose Compiler 1.5.1
- Core KTX 1.13.1、Lifecycle 2.7.0、Activity Compose 1.8.2
- Compose BOM 2024.04.01（Compose 1.6.6、Material 3 1.2.1）

`debugRuntimeClasspath` 与 `dependencyInsight` 已确认没有传递依赖把 Core、Lifecycle 或 Activity 提升回要求更高 compileSdk 的版本。

## 自动验证

执行：

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

结果：`BUILD SUCCESSFUL`。

- 13 个纯逻辑测试通过，0 failures、0 errors。
- 覆盖半衰期、稳定排序、固定/排除、多入口按包去重、同包 Activity 合并、跨包会话、跨批状态、非启动包过滤、来源切换、清空导入边界、时间回拨和每日快照。
- lint：0 errors、38 warnings。剩余警告是有意锁定兼容版本、targetSdk 34 以及 kapt→KSP 的构建性能建议；未关闭 metadata 检查，也未强制压低传递依赖。

最终 Debug APK：`app/build/outputs/apk/debug/app-debug.apk`。

## 小米 14 实机证据

- 设备：Xiaomi 14，产品代号 `houji`，型号 `23127PN0CC`。
- 用户确认 Android Studio 构建、安装和普通启动成功。
- ADB 确认包 `io.github.manxisuo.spriglauncher` 已安装，versionCode 1、minSdk 29、targetSdk 34。
- 系统已解析普通 `CATEGORY_LAUNCHER` 入口和 `CATEGORY_HOME + CATEGORY_DEFAULT` 桌面入口。
- `PACKAGE_USAGE_STATS` 已声明，设备当前 AppOps 状态为 `allow`。
- 当前默认桌面仍是 `com.miui.home/.launcher.Launcher`；验证过程中未自动切换。

## 尚需手动验收

1. 从应用设置触发默认桌面请求，用户确认后按 Home 进入 Sprig。
2. 从 Sprig 启动其他应用再回桌面，验证键盘、菜单和全部应用页的返回顺序。
3. 撤销使用情况访问，确认降级说明和桌面点击学习；重新授权后确认来源整体切换且不双计。
4. 固定、排除、上移/下移后杀进程或重启，确认状态保持。
5. 安装/卸载/更新应用后确认目录与图标刷新。
6. 中文搜索、长名称、大字体、横屏和键盘 Insets。
7. 在 HyperOS 手势导航下观察 Home 返回、动画和稳定性；厂商行为不能由编译测试替代。
8. 点击时间和日期，确认分别进入系统时钟与系统日历；若设备有多个日历，确认优先打开系统应用。
9. 切换默认、纯色和图片背景，确认文字可读、照片方向正确，并在杀进程或重启后保持。
