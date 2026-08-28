# 老挂戏老叟

一款为小猿口算（`com.fenbi.android.leo`）量身定制的 Xposed 模块（libxposed API 102）。

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

## 界面（对齐 WeKit）

- 注入宿主面板：全屏 4 tab（首页 / 功能 / 日志 / 设置）+ 悬浮胶囊底栏（LiquidGlass，MaterialSymbols 图标）
- 模块本体（独立 APK）：启动器页（激活卡片 / 打开宿主 / 打开模块设置 / GitHub）
- 动态配色（material-kolor）：9 种调色板样式 + 色彩规范 2021/2025 + 主题模式 + 动态壁纸取色 + 自定义种子色
- 预测返回动画 + 页面转场动画（AOSP / Miuix），返回逻辑优化

## 借鉴仓库

本项目的 UI、注入架构与部分功能参考 / 移植自以下开源项目：

| 仓库 | 借鉴内容 |
|------|---------|
| [Ujhhgtg/WeKit](https://github.com/Ujhhgtg/WeKit) | 完整 UI 体系：注入面板（全屏 4 tab pager + 悬浮胶囊底栏 LiquidGlass + M3ListScaffold + SegmentedColumn）、模块本体启动器页、MaterialSymbols 图标、动态配色（material-kolor 9 调色板 + 色彩规范 2021/2025 + 主题模式 + 动态壁纸取色 + 种子色）、预测返回动画 + 页面转场动画（miuix NavDisplay / AospNavTransition）、返回逻辑、设置页（UI 引擎 / 主题模式 / 预见性返回动画 / 页面过渡动画 / 调色板样式 / 颜色规格） |
| [TinyHai/AutoOralCalculation](https://github.com/TinyHai/AutoOralCalculation) | 本项目前身（原 cn.tinyhai.auto_oral_calculation）：口算自动答题 / 极速模式 / 循环练习 / 刷分 / 自定义答题脚本的 hook 逻辑基础 |
| [z2010643575/Simian](https://github.com/z2010643575/Simian) | Simian 改题目 / 改题目数量 / 口算答案 / 解锁 VIP 的 hook 点（EncryptResult / JsBridgeBean / QuestionVO / UserVipVO） |
| [ExElectron/Xiaoyuan_Kousuan_2026](https://github.com/ExElectron/Xiaoyuan_Kousuan_2026) | PK 秒结算（秒答题）：MITM 7 大 patch 的运行时注入移植（CSS 动画 0s / 音效静音 / 自动画线 / 跳题 0ms / 判题恒真 / 跳过识别等待） |

## 构建

```bash
./gradlew assembleRelease   # 签名版（需 GitHub secrets：KEYSTORE_BASE64/KEYSTORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD）
```

CI（GitHub Actions）：`ci.yml`（lint+test+unsigned build）、`release.yml`（签名发布）、`codeql.yml`。
