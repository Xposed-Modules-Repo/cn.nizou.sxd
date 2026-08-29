<div align="center">

# 老挂戏老叟

**小猿口算（`com.fenbi.android.leo`）LSPosed 增强模块 · libxposed Modern API 102**

[![Build APK](https://github.com/sxd91/cn.nizou.sxd/actions/workflows/ci.yml/badge.svg)](https://github.com/sxd91/cn.nizou.sxd/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/sxd91/cn.nizou.sxd?include_prereleases&label=release)](https://github.com/sxd91/cn.nizou.sxd/releases)
[![Downloads](https://img.shields.io/github/downloads/sxd91/cn.nizou.sxd/total?label=downloads)](https://github.com/sxd91/cn.nizou.sxd/releases)
![Android 16](https://img.shields.io/badge/Android-16-3DDC84?logo=android&logoColor=white)
![LSPosed API 102](https://img.shields.io/badge/LSPosed-API%20102-blue)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

</div>

## 介绍

**老挂戏老叟** 是一款为 **小猿口算**（猿辅导旗下口算练习 App，包名 `com.fenbi.android.leo`）量身定制的 **LSPosed / Xposed 增强模块**，基于 libxposed Modern API 102 开发，灵感来自 [AutoOralCalculation](https://github.com/TinyHai/AutoOralCalculation)。

模块为小猿口算提供**口算自动答题、秒结算、循环 PK、自定义结算时间、用户信息采集**等增强能力，UI 对齐 [WeKit](https://github.com/Ujhhgtg/WeKit)（注入宿主全屏面板 + 悬浮胶囊底栏 + 动态配色）。

> 支持在小猿口算 App 内通过注入面板直接配置，也可独立打开模块本体设置。

## 功能

### 口算练习
- 口算练习自动答题
- 口算练习秒结算（进局环境加速）
- 口算练习循环练习（可与秒结算一起开）
- 口算练习刷分（可自定义次数）
- 识别结果永远为正确答案

### 口算 PK
- 口算 PK 自动答题
- **口算 PK 秒结算**（移植自 [ExElectron/Xiaoyuan_Kousuan_2026](https://github.com/ExElectron/Xiaoyuan_Kousuan_2026) 的 7 大 patch，运行时注入）：
  - 进局环境加速：CSS 动画压至 0s / 音效静音 / 自动模拟笔画（AUTODRAW）/ 跳题 0ms / 判题恒真兜底 / 跳过手写识别等待
  - 与自动答题（极速/标准）配合实现「进局秒结算」
- 口算 PK 循环 PK
- 自定义答题脚本功能（有前端开发经验应该可以自己定义答题逻辑）

### 其它
- 自定义结算时间（毫秒）
- 自定义分数 / 改答案 / 改题目数量
- 用户信息采集（多子账号卡片：昵称 / 头像 / ID / Cookie）
- 无视名字限制（昵称长度与字符/格式限制全部放开）
- 实时日志悬浮窗 / 运行与崩溃日志查看、分享、保存
- 抓包 / 改包（okhttp 拦截器）

## 下载

前往 [Releases](https://github.com/sxd91/cn.nizou.sxd/releases) 获取最新构建。

## 当前支持

| 项目 | 支持范围 |
| --- | --- |
| 宿主 App | 小猿口算 `com.fenbi.android.leo` |
| 宿主版本 | **无限制**（不校验版本号；各 Hook 对目标类做存在性容错，已适配 3.140.x） |
| Android | Android 16 / API 36（更早版本未验证） |
| 架构 | arm64-v8a / armeabi-v7a / x86 / x86_64（universal） |
| LSPosed | Modern API 102（npatch / Zygisk 均可） |
| 作用域 | `com.fenbi.android.leo` |
| Root | 需要（LSPosed 环境） |

> 模块**不校验小猿口算版本号**：各 Hook 对目标类 / 方法做存在性容错（`findClassIfExists` + `runCatching`），宿主升级导致类名 / 接口漂移时自动跳过对应功能，不影响其它功能与稳定性。

## 安装与使用

1. 安装 APK。
2. 在 LSPosed 中启用 **老挂戏老叟**，作用域选择 **小猿口算（`com.fenbi.android.leo`）**。
3. 重启小猿口算（建议 force-stop 后冷启动），模块自动注入。
4. 打开模块本体 App，或在小猿口算内通过注入面板（入口见宿主设置 / 悬浮入口）配置功能。

## 排查

**模块未注入 / 激活卡显示未激活**
确认 LSPosed 已启用模块且作用域包含 `com.fenbi.android.leo`；若 `adb install -r` 覆盖安装过，请重新检查模块开关（安装可能将其重置为关闭），然后冷启动小猿口算。模块注入成功会在 logcat 出现 `(com.fenbi.android.leo)[cn.nizou.sxd,...]` 前缀日志。

**秒结算 / 自动答题不生效**
确认小猿口算已登录并进入对应口算练习 / PK 页面；模块的秒结算依赖前端页面加载（`leo-web-oral-pk` / `animation-oral.html`），进局后再观察实时日志。

**用户信息卡片为空**
打开一次小猿口算「个人中心」触发用户接口后，卡片会在数秒内自动补全；模块页可点击卡片或刷新图标手动刷新。

**功能没有生效**
进入模块的「日志」页查看运行日志，或抓取 logcat 中 `AutoOral` 标签的日志一并排查；提交反馈时请附上小猿口算版本号和日志。

## 技术说明

模块基于 libxposed Modern API 102，注入小猿口算主进程：`Application.attach` 后加载各 Hook（练习 / 识别 / WebView / 设置 / Retrofit 拦截 / 昵称 / Simian），秒结算通过运行时注入前端 JS（`assets/js/quick.js` / `fastsettle.js` / `cyclic.js`），用户信息与抓包通过 okhttp 拦截器捕获。构建由 GitHub Actions 自动完成（每次成功构建自动递增版本号）。

## 许可

本项目采用 [MIT License](LICENSE)。

---

本项目与猿辅导 / 小猿口算官方**无关**，仅用于学习交流。

## 鸣谢

- [TinyHai/AutoOralCalculation](https://github.com/TinyHai/AutoOralCalculation) —— 本项目前身（原 `cn.tinyhai.auto_oral_calculation`）：口算自动答题 / 极速模式 / 循环练习 / 刷分 / 自定义答题脚本的 hook 逻辑基础
- [Ujhhgtg/WeKit](https://github.com/Ujhhgtg/WeKit) —— 完整 UI 体系（注入面板 / 悬浮胶囊底栏 / 日志体系 / 动态配色 / 转场动画）
- [z2010643575/Simian](https://github.com/z2010643575/Simian) —— Simian 改题目 / 改题目数量 / 口算答案 / 解锁 VIP 的 hook 点
- [ExElectron/Xiaoyuan_Kousuan_2026](https://github.com/ExElectron/Xiaoyuan_Kousuan_2026) —— PK 秒结算 7 大 patch 的运行时注入移植
- [libxposed](https://github.com/libxposed/api) / [LSPosed](https://github.com/LSPosed/LSPosed) —— Xposed 框架
- [Miuix](https://github.com/compose-miuix-ui/miuix) —— LiquidGlass 悬浮底栏 / 导航
