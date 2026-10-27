# 模块级精确审计与迁移状态

日期：2026-10-27
基准：官方 abf119df / personal-v2
旧分支：personal c2e507bc（只读）

## 1. 覆盖检查

当前 1.76 源码相对 bd8a94b0 共 51 个文件；旧 1.62.10 personal 为 67 个文件。两者 Winlator 核心文件一致，1.76 额外包含版本号、字符串和分区差异。全部文件有归属，0 未分类。

| 模块 | 文件数 | 说明 |
| --- | ---: | --- |
| M1 Winlator | 17 | 核心 8 + 设置页 4 + 测试 4 + 文档 1 |
| M2 封面绑定 | 10 | 封面服务/任务/管理/游戏页/封面设置 |
| M3 页面壁纸 | 11 | 存储/图层/主题/应用设置/主页/导航/资源 |
| M4 封面模糊 | 2 | 存储 + 图标 |
| M5 UI 调整 | 10 | 下拉/弹窗/布局/顶栏/首页/引擎页/图标 |
| M6 工程/CI/文档 | 14 | CI、工具、维护文档、版本号、gitignore |
| 三语资源（共享） | 3 | values / values-en / values-ja，按键归属拆分 |

## 2. 共享文件的方法/字段归属

| 文件 | M1 | M2 | M3 | M4 | M5 |
| --- | --- | --- | --- | --- | --- |
| GameScreen.kt | 无 | CoverBindingManager.bind/states/retry、CoverBindingProgress、GameCoverFlow、CoverSearchDialog 绑定 | 无 | GameCoverBlurStore、GameCard 模糊入口 | TopBarBlurHost、布局/inset、导入 |
| PerGameSettingsScreen.kt | Winlator 状态/save/扩展行/快捷方式 | 无 | 无 | 无 | 顶栏/布局/SectionCard/Override 组件 |
| SettingsScreen.kt | Winlator 全局行、扩展行 | 无 | wallpaperSliderColors | 无 | 顶栏/布局、设置页结构 |
| AppSettingsActivity.kt | 无 | 无 | 壁纸选择/新增/透明度/预览 | 无 | 顶栏/布局/主题 |
| MainScreen.kt | 无 | 无 | PageWallpaperStore.init/selectedId/容器透明度 | 无 | 布局/动画 |
| MiuixSettingsTheme.kt | 无 | 无 | 壁纸下的卡片/弹窗取色 | 无 | 顶栏/主题导入 |
| AppNavigationRail.kt | 无 | 无 | 壁纸下侧栏透明度 | 无 | 布局/留白 |
| AppSettingsStore.kt | 无 | 无 | 首页样式/默认渐变/壁纸相关键（按字段再拆） | 无 | 首页样式 UI 键 |
| AppAlertDialog.kt | 无 | 无 | 无 | 无 | containerColor/scrimAlpha 参数 |
| AppSidePanel.kt | 无 | 无 | 无 | 无 | 个人面板条目参数 |
| values*/strings.xml | Winlator 键 | 封面绑定键 | 壁纸键 | 模糊键 | UI/首页键 |

## 3. 官方实现 vs 个人扩展

| 能力 | 官方 abf119df | 个人旧版 | 处理 |
| --- | --- | --- | --- |
| Winlator 三态模式 | 已实现（#123） | 独立实现 | M1 只做增量，不复制三态 |
| 容器 ID/名称规则 | 已实现 | 同 | 直接复用官方 |
| WineD3D GL/GDI | 已实现 wined3d_gl/gdi | wined3d + renderer | M1 两套兼容 |
| 快捷方式/音频/环境变量/Win 组件 | 未实现 | 有 | M1 增量迁移 |
| 封面搜索/绑定基础 | 已有同步 bind | 后台绑定/进度/重试 | M2 适配，不替换官方来源 |
| 封面模糊 | 无 | 有 | M4 独立新增 |
| 页面壁纸 | 无 | 有 | M3 独立新增 |
| 顶栏渐进模糊 | 已删除（#133） | 有 | M5 不迁移，采用官方布局 |
| 图形滤镜/Siglus | 已实现 | 无 | 直接保留官方 |

## 4. M1 与官方 #123 兼容结论

- 官方三态模式、容器基线、ID/名称规则、六空迁移全部复用。
- M1 只增加 4 个模型字段、shortcut_path、协议 overrides 扩展、分区键、启动参数。
- 官方反射测试 containerModeClearsEveryFieldExceptContainer 会覆盖新增字段，防止容器模式漏清。
- 本地验证：compileDebugKotlin 成功；68 项 Winlator 测试通过（官方 + 个人）。

## 5. M2 与官方封面机制兼容结论

- 官方当前只有同步 bindCoverCandidate，没有后台任务/进度/重试；官方 #125 尚未进入 main。
- M2 保留官方来源/候选/缓存结构，只增加：
  - CoverBindingManager（应用级任务/进度/失败重试）
  - CoverDownloadCancel 与下载取消
  - CoverBindingProgress 卡片覆盖层
  - 批量刮削目标/进度
- 若未来官方 #125 合并，先比较 CoverBindingManager 语义，再决定复用官方还是保留个人增量，不能留两套。

## 6. 迁移状态矩阵

状态定义：代码迁移 / PR 创建 / PR 合并 / 构建 / 测试 / 真机验收，分别独立。

| 模块 | 分支 | PR | 代码迁移 | PR 创建 | 合并 | 构建 | 测试 | 真机 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| M0 方案 | docs/personal-v2-migration-plan | #3 | 完成 | 完成 | 未合并 | 不适用 | 文档审阅 | 不适用 |
| M1-core | feat/winlator-personal-core | #4 | 完成（Winlator 文件与 1.76 一致；分区键为兼容修复） | 完成 | 未合并 | 通过 | 68 tests OK | 未做 |
| M1-UI | 待建 | 待建 | 未开始 | 未开始 | 未开始 | 未开始 | 未开始 | 未做 |
| M2 | 待建 | 待建 | 未开始 | 未开始 | 未开始 | 未开始 | 未开始 | 未做 |
| M3 | 待建 | 待建 | 未开始 | 未开始 | 未开始 | 未开始 | 未开始 | 未做 |
| M4 | 待建 | 待建 | 未开始 | 未开始 | 未开始 | 未开始 | 未开始 | 未做 |
| M5 | 待建 | 待建 | 未开始 | 未开始 | 未开始 | 未开始 | 未开始 | 未做 |
| M6 | 待建 | 待建 | 未开始 | 未开始 | 未开始 | 未开始 | 未开始 | 不适用 |

## 7. 剩余问题

1. M1 仅完成核心/协议/模型/启动，设置页 UI 未接入，用户暂时看不到个人扩展配置项。
2. M1-core PR 未合并，personal-v2 仍是纯官方基线。
3. M2 需要等 M1 合并后从 personal-v2 切分支，避免 GameScreen/strings 冲突。
4. M5 顶栏模糊按官方 #133 决策不迁移；如需保留，另开 opt-in。
5. 所有模块均未做真机验收。