# SpotShift

一个简洁的 Android 模拟定位工具：选好地点，一键传送。使用 Kotlin + Jetpack Compose + Material 3 实现。

> 仅用于开发与测试，请遵守高德地图瓦片服务条款及当地法律法规。

## 功能

- 模拟定位：基于 Android Mock Location API，持续向前台服务写入位置，支持随时停止；可调 1–30 秒的定位校准间隔。
- 快捷传送点：新增、编辑、删除、设为当前；自动保留上次选择。
- 地图选点：高德 XYZ 栅格瓦片，支持拖动、双指缩放、点击取点；可全屏选点、按名称搜索地点；管理页右下角「地图选点」可直接从地图开始新建。
- 位置导入：解析高德 / 百度分享文本、URI Scheme、网页链接与高德/百度短链，自动换算坐标系，支持批量预览、改名与勾选。
- 地点搜索：使用 OpenStreetMap Photon 服务，中文地点名可直接搜索；系统 Geocoder 作为兜底。
- 外观：可切换跟随系统 / 浅色 / 深色；提供预设种子色与 `#RRGGBB` 自定义颜色，统一生成 MD3 主题。
- 定位设置：用 MD3 滑块调整校准间隔（1–30 秒）；点击传送时若检测到 Wi-Fi，会弹出“强制开启模拟 / 去关闭 Wi-Fi / 取消”提示。
- 关于页：应用图标、作者 he0llo、版本、软件介绍与 GitHub 仓库一键打开。
- 状态反馈：主界面显示正在模拟的地点，常驻通知提供“停止模拟”操作。

## 环境

- Android Studio（AGP 9.3.0）
- JDK 21（Android Studio 内置 JBR 即可）
- Android SDK 37（Compose BOM 2026.09.00 要求 `compileSdk 37`）
- minSdk 24 / targetSdk 36

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

## 使用

1. 打开系统「设置 → 开发者选项 → 选择模拟位置信息应用 → SpotShift」。
   > 若列表中没有 SpotShift，请确认安装的是本文档对应的最新构建；应用在清单中声明了 `ACCESS_MOCK_LOCATION`，系统才会把它列为候选应用。
2. 在 SpotShift 中新增或导入传送点，选中目标；也可以从管理页右下角「地图选点」直接开始。
3. 点击底部「传送」按钮；首次会请求定位权限，未选择模拟位置应用时会弹出引导。若当前连接 Wi-Fi，会弹窗提示：可强制开启模拟、前往关闭 Wi-Fi，或取消本次操作。
4. 需要结束时点「停止模拟」，或使用通知中的「停止模拟」。

## 位置导入

支持以下输入（可一次粘贴多条）：

- `androidamap://`、`amapuri://`、`iosamap://`（GCJ-02）
- `baidumap://`（BD-09）
- 高德 / 百度网页链接中的 `position`、`location`、`lat/lng`、`p` 等参数
- 高德短链（`surl.amap.com/...`）与百度短链（`j.map.baidu.com/...`），导入时自动跟随跳转
- 普通分享文本中的坐标，如 `高德地点：天安门 39.908692,116.397477`

分享面板中的“发送到 SpotShift”会直接打开导入页并自动识别。

应用内部统一使用 WGS-84：导入时 GCJ-02 / BD-09 转为 WGS-84；地图展示时再转为 GCJ-02 以匹配高德瓦片。无法解析的内容会明确列出原因，不会静默丢弃。

## 地点搜索

编辑页与全屏选点都能按名称搜索，例如「北京」「内蒙古大厦」。搜索优先请求 `https://photon.komoot.io/api/`（OpenStreetMap 数据，无需 Key），失败时回退到系统 Geocoder；结果为 WGS-84，选中后按当前所选坐标系回填。搜索与短链解析需要网络。

## 地图与缓存

瓦片地址：`https://webst0{1-4}.is.autonavi.com/appmaptile?style=7&x={x}&y={y}&z={z}`

为减少加载等待与放大重绘：

- 内存 `LruCache` + `cacheDir/amap_tiles` 磁盘缓存（上限 1500 个文件，自动清理）
- 同一瓦片并发请求去重
- 可见瓦片预取，手势结束后延迟预取相邻层级；缺失瓦片按 1.2 / 2.5 / 4 / 8 / 15 秒自动重试
- 放大时若子级瓦片未到，先用父级瓦片对应区域填充，避免白屏

## 代码结构

```
app/src/main/java/he/spotshift/llo/
├── MainActivity.kt          入口，接收分享 / 深链
├── data/                    坐标转换、Spot 模型、SharedPreferences 仓库
├── imports/                 分享文本 / URI / 短链解析
├── map/                     瓦片下载缓存、Compose 地图
├── mock/                    Mock Location 前台服务与权限
└── ui/                      主页、管理、编辑、导入、设置、关于、搜索与地图选点
```