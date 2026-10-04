# Sprig Launcher

Sprig Launcher 是一个本地运行的 Android 桌面，目标是把经常使用的应用稳定地排到前面，同时始终保留可搜索的完整应用目录。项目不需要账号、网络、Root、无障碍服务或通知读取权限。

## 环境与构建

- Android Studio（JDK 17 或兼容的新版 JDK）
- Android SDK Platform 34、Build Tools 34.x
- minSdk 29，compileSdk/targetSdk 34
- AGP 8.6.1、Gradle 8.7、Kotlin 1.9.0、Compose Compiler 1.5.1

在 `local.properties` 中配置真实 SDK 路径后：

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Debug APK 生成在 `app/build/outputs/apk/debug/app-debug.apk`。

## 使用

1. 安装后从应用列表打开 Sprig Launcher，进入“设置”。
2. 点击“设为默认桌面”，在系统界面选择 Sprig Launcher。若系统角色请求不可用，会打开系统默认桌面设置。
3. 使用情况访问不是成为默认桌面的附带权限。未授权时，Sprig 只记录从自身发起的应用打开请求；授权后，使用系统公开的 Usage Events 估算前台使用会话。
4. 长按应用可固定、排除常用推荐、调整固定顺序、查看统计或打开系统应用详情。
5. 要切回系统桌面，在 Sprig 设置中打开默认桌面设置并重新选择系统桌面。Sprig 不卸载或禁用系统桌面。
6. 点击首页时间打开系统时钟，点击日期打开系统日历。
7. 设置页可选择默认背景、四种纯色背景或本地图片。图片通过系统文件选择器授权，不申请存储权限。

## 排序规则

每次有效会话的权重为 `2 ^ (-ageDays / 7)`，使用 90 天滚动窗口和 UTC 日桶中点近似。固定区不参与动态排序；常用区按包去重，排除固定项和被排除的包。相同分数保持上次快照顺序，没有快照时按名称和稳定入口 ID 排序。默认在本地日期变化后的首次进入时发布新快照，也可手动立即重排。

系统前台事件并不等同于精确打开次数。实现会合并 30 秒内同包的短暂恢复、过滤自身和没有启动入口的包，但多窗口、锁屏、系统弹层仍可能造成近似误差。桌面点击与系统事件分源保存，当前有权限时只用系统来源计分，撤销后只用桌面点击来源，不相加。

## 隐私与限制

- 数据只保存在本机 Room/DataStore 中；应用未声明 `INTERNET`，且关闭系统备份。
- 当前只处理个人用户。工作资料、私密空间和多用户入口暂不支持。
- 详细事件指纹仅用于有限窗口内去重，三天后清理；日桶保留 90 天。
- 清空学习数据会保留固定、排除和主题设置，并建立新的采集起点，避免重新导入旧历史。
- 小米/HyperOS 是否允许第三方桌面及导航手势兼容性必须在目标设备上实测，普通应用无法绕过厂商限制。

架构见 [docs/architecture.md](docs/architecture.md)，实际验证记录见 [docs/verification.md](docs/verification.md)，后续候选见 [docs/roadmap.md](docs/roadmap.md)。
