package com.tyranor.next.core.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Winlator 外置启动设置归一：对齐 winlator-cn `LaunchArgs` 校验规则，非法值一律回退空串（不下发）。
 */
class WinlatorSettingsTest {

    /** 图形驱动仅接受固定 Vulkan/OpenGL 组合，非法值回退到容器配置。 */
    @Test
    fun graphicsDriverOnlyAcceptsFixedVulkanOpenGlCombinations() {
        assertEquals("turnip,zink", EngineSettingsStore.normalizeWinlatorGraphicsDriver(" turnip, zink "))
        assertEquals("vortek,virgl", EngineSettingsStore.normalizeWinlatorGraphicsDriver("VORTEK,VIRGL"))
        assertEquals("vortek,gladio", EngineSettingsStore.normalizeWinlatorGraphicsDriver("vortek,gladio"))
        assertEquals("", EngineSettingsStore.normalizeWinlatorGraphicsDriver(""))
        assertEquals("", EngineSettingsStore.normalizeWinlatorGraphicsDriver("turnip"))
        assertEquals("", EngineSettingsStore.normalizeWinlatorGraphicsDriver("zink"))
        assertEquals("", EngineSettingsStore.normalizeWinlatorGraphicsDriver("turnip,bad"))
        assertEquals("", EngineSettingsStore.normalizeWinlatorGraphicsDriver("auto"))
    }

    /** 接受现有 wrapper 与 GL/GDI 预设，并将未知值归一回跟随容器。 */
    @Test
    fun dxWrapperAcceptsKnownWrappersAndRendererPresets() {
        assertEquals("dxvk", EngineSettingsStore.normalizeWinlatorDxWrapper("DXVK"))
        assertEquals("wined3d", EngineSettingsStore.normalizeWinlatorDxWrapper("wined3d"))
        assertEquals("wined3d_gl", EngineSettingsStore.normalizeWinlatorDxWrapper(" WINED3D_GL "))
        assertEquals("wined3d_gdi", EngineSettingsStore.normalizeWinlatorDxWrapper(" WINED3D_GDI "))
        assertEquals("", EngineSettingsStore.normalizeWinlatorDxWrapper(null))
        assertEquals("", EngineSettingsStore.normalizeWinlatorDxWrapper("wined3d_vulkan"))
        assertEquals("", EngineSettingsStore.normalizeWinlatorDxWrapper("vkd3d"))
        assertEquals("", EngineSettingsStore.normalizeWinlatorDxWrapper("auto"))
    }

    /** Box64 预设接受内置名称与 CUSTOM 编号，未知名称不下发。 */
    @Test
    fun wined3dRendererOnlyAcceptsGdiGlVulkan() {
        assertEquals("gdi", EngineSettingsStore.normalizeWinlatorWined3dRenderer("GDI"))
        assertEquals("gl", EngineSettingsStore.normalizeWinlatorWined3dRenderer(" gl "))
        assertEquals("vulkan", EngineSettingsStore.normalizeWinlatorWined3dRenderer("Vulkan"))
        assertEquals("", EngineSettingsStore.normalizeWinlatorWined3dRenderer("d3d12"))
        assertEquals("", EngineSettingsStore.normalizeWinlatorWined3dRenderer(null))
    }

    @Test
    fun box64PresetAcceptsBuiltinsAndCustom() {
        assertEquals("PERFORMANCE", EngineSettingsStore.normalizeWinlatorBox64Preset("performance"))
        assertEquals("STABILITY", EngineSettingsStore.normalizeWinlatorBox64Preset("STABILITY"))
        assertEquals("CUSTOM-3", EngineSettingsStore.normalizeWinlatorBox64Preset("custom-3"))
        assertEquals("", EngineSettingsStore.normalizeWinlatorBox64Preset("TURBO"))
    }

    /** 分辨率仅接受应用提供的固定档位，不放行接收端允许但界面未提供的值。 */
    @Test
    fun screenSizeOnlyAcceptsFixedOptions() {
        assertEquals("1600x900", EngineSettingsStore.normalizeWinlatorScreenSize("1600x900"))
        assertEquals("1920x1080", EngineSettingsStore.normalizeWinlatorScreenSize(" 1920x1080 "))
        assertEquals("1280x800", EngineSettingsStore.normalizeWinlatorScreenSize("1280x800"))
        assertEquals("", EngineSettingsStore.normalizeWinlatorScreenSize("auto"))
        assertEquals("", EngineSettingsStore.normalizeWinlatorScreenSize("1600X900"))
        assertEquals("", EngineSettingsStore.normalizeWinlatorScreenSize("320x160"))
        assertEquals("", EngineSettingsStore.normalizeWinlatorScreenSize("2560x1440"))
    }

    /** 单游戏缺失字段继承全局，显式空值或非法覆盖不恢复成全局值。 */
    @Test
    fun mergeWinlatorUsesOverrideWhenPresentAndGlobalOtherwise() {
        val global = EngineSettingsStore.Winlator(
            containerId = 3,
            containerName = "全局容器",
            graphicsDriver = "turnip,zink",
            screenSize = "1280x720",
            tz = "Asia/Tokyo",
            save = true,
        )

        val merged = EffectiveEngineSettings.mergeWinlator(
            global,
            WinlatorOverride(
                containerId = "7",
                screenSize = "",                       // 显式不下发
                graphicsDriver = "vortek,virgl",
                tz = "invalid tz",                     // 非法覆盖归一回空串，不继承全局
            ),
        )

        assertEquals(7, merged.containerId)
        assertEquals("全局容器", merged.containerName)
        assertEquals("vortek,virgl", merged.graphicsDriver)
        assertEquals("", merged.screenSize)
        assertEquals("", merged.tz)
        assertEquals(true, merged.save)
    }

    /** 没有单游戏覆盖时完整保留全局 Winlator 设置。 */
    @Test
    fun mergeWinlatorWithoutOverrideKeepsGlobal() {
        val global = EngineSettingsStore.Winlator(containerId = 5, containerName = "C")
        assertEquals(global, EffectiveEngineSettings.mergeWinlator(global, null))
    }

    /** 只覆盖容器名时不能继承全局 ID：协议里 container_id 优先于 container_name。 */
    @Test
    fun mergeWinlatorClearsInheritedIdWhenOnlyNameIsOverridden() {
        val global = EngineSettingsStore.Winlator(containerId = 5, containerName = "C")
        val merged = EffectiveEngineSettings.mergeWinlator(global, WinlatorOverride(containerName = "D"))
        assertEquals(0, merged.containerId)
        assertEquals("D", merged.containerName)
    }

    /** 同时给出 ID 与名称时保留显式 ID。 */
    @Test
    fun mergeWinlatorKeepsExplicitIdAlongsideName() {
        val global = EngineSettingsStore.Winlator(containerId = 5, containerName = "C")
        val merged = EffectiveEngineSettings.mergeWinlator(
            global,
            WinlatorOverride(containerId = "9", containerName = "D"),
        )
        assertEquals(9, merged.containerId)
        assertEquals("D", merged.containerName)
    }

    /** 单游戏快照保留 GDI 选择、显式空值与 save，缺失字段仍表示继承。 */
    @Test
    fun perGameOverrideSnapshotMapsFlatKeys() {
        val json = org.json.JSONObject()
            .put(PerGameSettingsStore.F_WINLATOR_CONTAINER_ID, "2")
            .put(PerGameSettingsStore.F_WINLATOR_DXWRAPPER, "wined3d_gdi")
            .put(PerGameSettingsStore.F_WINLATOR_SCREEN_SIZE, "")
            .put(PerGameSettingsStore.F_WINLATOR_SAVE, true)
        val override = PerGameSettingsStore.toWinlatorOverride(json)

        assertEquals("2", override?.containerId)
        assertEquals("wined3d_gdi", override?.dxwrapper)
        assertEquals("", override?.screenSize)
        assertEquals(true, override?.save)
        assertEquals(null, override?.containerName)
    }

    /** 语言环境保留白名单中的规范拼写，其它值归一为空串。 */
    @Test
    fun lcAllOnlyAcceptsFixedOptions() {
        assertEquals("ja_JP.UTF-8", EngineSettingsStore.normalizeWinlatorLcAll(" ja_JP.UTF-8 "))
        assertEquals("zh_CN.utf8", EngineSettingsStore.normalizeWinlatorLcAll("zh_CN.utf8"))
        assertEquals("", EngineSettingsStore.normalizeWinlatorLcAll("ru_RU.UTF-8"))
        assertEquals("", EngineSettingsStore.normalizeWinlatorLcAll("ja JP"))
    }

    /** 时区仅接受固定列表，避免接收端因无效参数拒绝整次启动。 */
    @Test
    fun tzOnlyAcceptsFixedOptions() {
        assertEquals("Asia/Tokyo", EngineSettingsStore.normalizeWinlatorTz("Asia/Tokyo"))
        assertEquals("UTC", EngineSettingsStore.normalizeWinlatorTz("UTC"))
        assertEquals("", EngineSettingsStore.normalizeWinlatorTz("Etc/GMT-8"))
        assertEquals("", EngineSettingsStore.normalizeWinlatorTz("Asia Tokyo"))
    }

    /** 默认参数均不下发，运行配置由 Winlator 容器决定。 */
    @Test
    fun audioDriverOnlyAcceptsAlsaAndPulseAudio() {
        assertEquals("alsa", EngineSettingsStore.normalizeWinlatorAudioDriver("ALSA"))
        assertEquals("pulseaudio", EngineSettingsStore.normalizeWinlatorAudioDriver(" PulseAudio "))
        assertEquals("", EngineSettingsStore.normalizeWinlatorAudioDriver("oss"))
        assertEquals("", EngineSettingsStore.normalizeWinlatorAudioDriver(null))
    }

    @Test
    fun envVarsKeepOnlyValidKeyValueTokens() {
        assertEquals(
            "DXVK_HUD=1 MESA_EXTENSION_MAX_YEAR=2003",
            EngineSettingsStore.normalizeWinlatorEnvVars(" DXVK_HUD=1  bad MESA_EXTENSION_MAX_YEAR=2003 "),
        )
        assertEquals("", EngineSettingsStore.normalizeWinlatorEnvVars("NO_EQUALS"))
        assertEquals("", EngineSettingsStore.normalizeWinlatorEnvVars(null))
        assertEquals(true, EngineSettingsStore.isValidWinlatorEnvVars(""))
        assertEquals(true, EngineSettingsStore.isValidWinlatorEnvVars("A=1 B=2"))
        assertEquals(false, EngineSettingsStore.isValidWinlatorEnvVars("A=1 BAD"))
        assertEquals(false, EngineSettingsStore.isValidWinlatorEnvVars("=1"))
    }

    @Test
    fun winComponentsOnlyAcceptKnownKeysAndBuiltinNativeValues() {
        assertEquals(
            "direct3d=1,vcrun2010=0",
            EngineSettingsStore.normalizeWinlatorWinComponents(
                " vcrun2010=0 , bad=1,direct3d=2, direct3d=1 ",
            ),
        )
        assertEquals("", EngineSettingsStore.normalizeWinlatorWinComponents("direct3d=container"))
        assertEquals("", EngineSettingsStore.normalizeWinlatorWinComponents(null))
        assertEquals(
            "direct3d=1,directsound=1,directmusic=1,directshow=0,directplay=0,xaudio=1,vcrun2005=0,vcrun2010=1,wmdecoder=1",
            EngineSettingsStore.normalizeWinlatorWinComponents(
                "direct3d=1,directsound=1,directmusic=1,directshow=0,directplay=0,xaudio=1,vcrun2005=0,vcrun2010=1,wmdecoder=1",
            ),
        )
    }

    @Test
    fun winComponentsOverrideKeepsContainerSentinelAndMergesPerComponent() {
        val global = EngineSettingsStore.normalizeWinlatorWinComponents("direct3d=1,xaudio=0")
        val override = EngineSettingsStore.setWinlatorWinComponentOverride(
            EngineSettingsStore.setWinlatorWinComponentOverride(null, "direct3d", ""),
            "vcrun2010",
            "1",
        )
        assertEquals("direct3d=container,vcrun2010=1", override)
        assertEquals("", EngineSettingsStore.winlatorWinComponent(global, "directsound"))
        assertEquals("1", EngineSettingsStore.winlatorWinComponent(global, "direct3d"))
        assertEquals("", EngineSettingsStore.winlatorWinComponentOverride(override, "direct3d"))

        val merged = EngineSettingsStore.mergeWinlatorWinComponents(global, override)
        assertEquals("xaudio=0,vcrun2010=1", merged)
    }

    @Test
    fun winComponentsOverrideFallsBackToGlobalAndCanBeCleared() {
        val global = "direct3d=1"
        assertEquals(global, EngineSettingsStore.mergeWinlatorWinComponents(global, null))
        assertEquals("", EngineSettingsStore.mergeWinlatorWinComponents(global, ""))
        assertEquals("", EngineSettingsStore.mergeWinlatorWinComponents(global, "unknown=1"))
        assertEquals(null, EngineSettingsStore.setWinlatorWinComponentOverride("direct3d=1", "direct3d", null))
        assertEquals(
            "direct3d=1,directshow=0",
            EngineSettingsStore.setWinlatorWinComponent("direct3d=1", "directshow", "0"),
        )
        assertEquals(
            "direct3d=1",
            EngineSettingsStore.setWinlatorWinComponent("direct3d=1,directshow=0", "directshow", ""),
        )
    }

    @Test
    fun defaultsKeepAllParametersUnset() {
        val defaults = EngineSettingsStore.Winlator()
        assertEquals(0, defaults.containerId)
        assertEquals("", defaults.containerName)
        assertEquals("", defaults.graphicsDriver)
        assertEquals("", defaults.dxwrapper)
        assertEquals("", defaults.wined3dRenderer)
        assertEquals("", defaults.screenSize)
        assertEquals("", defaults.lcAll)
        assertEquals("", defaults.tz)
        assertEquals("", defaults.box64Preset)
        assertEquals("", defaults.winComponents)
        assertEquals("", defaults.audioDriver)
        assertEquals("", defaults.envVars)
        assertEquals(false, defaults.save)
    }

    /** 缺失覆盖继承全局；显式空值、旧 wrapper 与新预设保持各自语义。 */
    @Test
    fun dxWrapperOverrideDistinguishesInheritanceContainerAndExplicitPresets() {
        val global = EngineSettingsStore.Winlator(dxwrapper = "wined3d_gdi")
        val overrides = listOf(
            null to "wined3d_gdi",
            "" to "",
            "wined3d" to "wined3d",
            " WINED3D_GL " to "wined3d_gl",
            "wined3d_gdi" to "wined3d_gdi",
            "dxvk" to "dxvk",
            "invalid" to "",
        )
        for ((value, expected) in overrides) {
            val resolved = EffectiveEngineSettings.mergeWinlator(global, WinlatorOverride(dxwrapper = value))
            assertEquals(expected, resolved.dxwrapper)
        }
    }
}
