# personal-v2 模块化迁移方案

## 目标

以官方最新 main 为干净基线，新建 personal-v2，把旧 personal 的个人功能拆成独立 PR 逐项迁入；以后只维护个人扩展，不再整包搬运。

## 当前基线

| 项目 | 值 |
| --- | --- |
| 官方上游 | https://github.com/Weiss-UltimateSavior/Tyranor-Next |
| 官方 main | abf119df（2026-10-26，PR #123） |
| 旧 personal | c2e507bc（备份 tag：backup/personal-pre-sync-20261027） |
| 旧 personal 基线 | bd8a94b0（官方 1.61） |
| personal-v2 | abf119df（新建，已推送） |
| 旧 personal 差异 | 67 文件，+5622/-785，0 删除 |

## 模块划分

| 模块 | 功能 | 主要文件 | 依赖 | 风险 |
| --- | --- | --- | --- | --- |
| M0 基线 | 官方 main，无个人改动 | 全部官方文件 | 无 | 低 |
| M1 Winlator 个人扩展 | 官方三态之外的快捷键启动、音频、环境变量、Win 组件、渲染器、容器扩展 | WinlatorContract、EngineSettingsStore、EffectiveEngineSettings、EngineSettingsResolver、GameOverridePartitions、PerGameSettingsStore、EngineLauncher、ExternalEmulatorLauncher、PerGameSettingsScreen、SettingsScreen、EngineSettingsText、三语、4 个测试 | M0；官方 #123 三态 | 高 |
| M2 封面绑定 | 搜索后继续下载、进度圈、失败重下、批量抓取目标/进度 | CoverBindingManager、CoverSupport、CoverScraperService、CoverScrapeTaskManager、NextMoeCoverService、VndbCoverService、CoverBindingProgress、GameCoverFlow、GameScreen、封面设置、三语 | M0；与官方封面逻辑共存 | 高 |
| M3 页面壁纸 | 多壁纸、选择、透明度、默认恢复 | PageWallpaperStore、PageWallpaperLayer、WallpaperSliderColors、DefaultPageBackground、MiuixSettingsTheme、AppSettingsActivity、MainScreen、AppNavigationRail、drawable、三语、AppSettingsStore | M0；官方主题/设置页 | 中 |
| M4 封面模糊 | 单游戏封面模糊开关与半径 | GameCoverBlurStore、GameScreen、封面操作入口、drawable、三语 | M2；复用封面 UI | 中 |
| M5 个人 UI 调整 | AppDropdown、弹窗参数、布局/留白、顶栏模糊 | AppDropdown、AppAlertDialog、AppNavigationRail、ScreenSize、TopBarBlur、HomeScreen、EngineScreen、GameScreen 局部、drawable、三语 | M0；先决定顶栏模糊去留 | 中 |
| M6 工程/CI/文档 | 个人版本号、CI、上游同步脚本、维护文档、契约 | app/build.gradle.kts、.github/workflows/personal-ci.yml、upstream-sync-check.yml、tools/personal、docs/personal、PERSONAL_FORK、.gitignore、docs 外置主机 | M0；可在功能 PR 后 | 低 |

## PR 顺序与分支

所有 PR 以 personal-v2 为目标；有依赖的 PR 依次合并后再切下一条，避免同文件重复冲突。

1. docs/personal-v2-migration-plan -> personal-v2（本 PR）
2a. feat/winlator-personal-core -> personal-v2（M1 核心，已完成 PR #4）
2b. feat/winlator-personal-ui -> personal-v2（M1 设置页 UI，待 M1-core 合并）
3. feat/cover-binding-progress -> personal-v2
4. feat/cover-blur -> personal-v2
5. feat/page-wallpaper -> personal-v2
6. feat/personal-ui-tweaks -> personal-v2
7. chore/personal-ci-docs -> personal-v2

依赖关系：

    M0 -> M1 -> M2 -> M4
    M0 -> M3
    M0 -> M5
    M1 + M3 + M5 -> M6（M6 最后统一整理）

M2 与 M4 分两个 PR；M4 依赖 M2 的封面卡片入口。M1 与 M5 都改 PerGameSettingsScreen/SettingsScreen，M5 必须排在 M1 之后。

## M1 设计要点

官方 #123 已实现三态模式、容器基线、ID/名称规则和 9 个分区键。个人扩展只做增量：

- 模型增量：wined3dRenderer、winComponents、audioDriver、envVars。
- 覆盖增量：WinlatorOverride 增加 4 字段；containerBaseline 清空全部非容器字段。
- 协议增量：保留官方 wined3d_gl/gdi 预设；同时支持 wined3d + renderer；overrides 增加 wincomponents/audio/env；shortcut_path 独立。
- 持久化增量：TYRANO_KEYS 与 CURRENT_OVERRIDE_FIELDS 增加 4 个个人键。
- 启动增量：EngineLauncher 传扩展字段；新增 openWinlatorShortcut。
- UI 增量：在官方三态卡之后增加快捷方式、音频、环境变量、Win 组件、渲染器分组。
- 测试增量：保留官方 EngineSettingsResolverWinlatorTest 反射清空测试，新增个人扩展测试。

## 验收标准（每个 PR）

- 功能说明、涉及文件/方法、与官方实现关系、对原版影响、架构兼容性。
- 构建：compileDebugKotlin 成功；JVM 回归；lint 不新增错误。
- 契约：个人功能契约脚本通过。
- 不删除旧个人功能；不修改官方无关代码。
- 明确未执行的真机检查。

## 测试策略

- 每个 PR：JUnitCore 直跑相关测试 + Gradle compile。
- M1：官方 Winlator 测试 + 个人 Winlator 测试。
- M2/M4：封面缓存/任务管理测试 + 手工封面流程。
- M3/M5：编译 + 页面截图/真机布局检查。
- M6：CI 实跑 + 上游 sync check。
- 真机重点：Winlator 容器/快捷方式/参数继承、封面下载/重试/模糊、壁纸持久化、三语。

## 顶栏模糊决策

官方已因性能回归（#133）删除 TopBarBlur。M5 默认采用官方上下分离布局，不迁移个人顶栏模糊；如确需保留，另开 opt-in PR，不影响 M1-M4。

## 当前状态

- personal 未改，备份 tag 已推送。
- personal-v2 已从官方 main 建立并推送。
- 本 PR 只提交迁移方案，不含代码移植。
- 下一步：M1-UI 设置页接入；M1-core PR #4 等待审核合并。