# X倍速（XSpeed）

个人用 Android 壳应用：内嵌官方 X（twitter.com / x.com）移动站时间线，**长按视频 2 倍速播放**。

界面与官方 X 一致（For You / Following 时间线），不是 TikTok 式竖滑。

## 功能

- 打开应用即进入 `https://x.com/home`（官方移动站）
- **长按正在播放（或视频区域）约 200ms → 2x**；松手恢复 1x
- 长按期间屏幕中央显示半透明 **「2x」** 提示
- 不拦截滚动 / 单击暂停 / 双击；用定时器识别长按，避免手势冲突
- Cookie 持久化，登录状态重启后仍保留
- 系统返回键 = WebView 后退；下拉刷新

## 安装

1. 从 [Releases](https://github.com/hnsjnd/XSpeed/releases) 下载最新 `XSpeed-v*.apk`
2. 在手机上允许「未知来源」安装
3. 打开 **X倍速**，用你的 X 账号登录（与浏览器相同）
4. 刷时间线，遇到视频：**按住**即可加速

> 本应用不收集账号密码；登录完全走官方 X 网页。

## 长按实现说明

注入的 JavaScript（`app/src/main/assets/xspeed.js`）：

1. 在 `document` 捕获阶段监听 `touchstart` / `touchmove` / `touchend`
2. `touchstart` 命中 `<video>` 或其播放器容器后启动 ~200ms 定时器
3. 期间若移动超过约 12px（滚动）则取消
4. 定时器触发：`video.playbackRate = 2`，显示「2x」浮层
5. `touchend` / `touchcancel`：`playbackRate = 1`，隐藏浮层
6. `MutationObserver` + `history.pushState` 钩子：SPA 换页后重新绑定

## 技术架构

- 单 Activity + `WebView` 加载官方移动站
- UA：Chrome Android（Pixel 7 / Chrome 120），便于登录与时间线正常渲染
- `mediaPlaybackRequiresUserGesture = false`，便于自动播放策略下的倍速
- 包名：`com.hnsjnd.xspeed`
- minSdk 26 / targetSdk 34

若 WebView 登录被强拦（验证码 / 仅 App），可再考虑 Squawker 式 API 方案；当前优先 WebView 以保持官方 UI。

## 自行编译

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
# 准备 keystore.properties + xspeed-release.jks（已 gitignore）
./gradlew :app:assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

签名密钥与 `keystore.properties` **不会**进入仓库；本机生成的 `xspeed-release.jks` 请自行备份。

## 隐私与声明

- 仅供个人使用
- 非 X / Twitter 官方应用
- 不修改服务端数据，仅本地改变 `HTMLMediaElement.playbackRate`

## License

MIT（个人自用；请遵守 X 服务条款）
