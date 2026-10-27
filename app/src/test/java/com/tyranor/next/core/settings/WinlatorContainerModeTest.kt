package com.tyranor.next.core.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 容器模式：保留容器匹配，清空全部运行参数与写回开关。 */
class WinlatorContainerModeTest {

    private fun global() = EngineSettingsStore.Winlator(
        containerId = 1,
        containerName = "Global",
        graphicsDriver = "turnip,zink",
        dxwrapper = "wined3d",
        wined3dRenderer = "gdi",
        screenSize = "1280x720",
        lcAll = "ja_JP.UTF-8",
        tz = "Asia/Tokyo",
        box64Preset = "performance",
        winComponents = "direct3d=1",
        audioDriver = "alsa",
        envVars = "A=B",
        save = true,
    )

    @Test
    fun containerOverrideClearsRuntimeFieldsAndSave() {
        val override = EffectiveEngineSettings.containerModeOverride(
            WinlatorOverride(containerId = "2", containerName = "Custom"),
        )

        assertEquals("2", override.containerId)
        assertEquals("Custom", override.containerName)
        assertEquals("", override.graphicsDriver)
        assertEquals("", override.dxwrapper)
        assertEquals("", override.wined3dRenderer)
        assertEquals("", override.screenSize)
        assertEquals("", override.lcAll)
        assertEquals("", override.tz)
        assertEquals("", override.box64Preset)
        assertEquals("", override.winComponents)
        assertEquals("", override.audioDriver)
        assertEquals("", override.envVars)
        assertFalse(override.save!!)
    }

    @Test
    fun mergeContainerModeKeepsContainerButDropsRuntimeParams() {
        val merged = EffectiveEngineSettings.mergeWinlator(
            global(),
            EffectiveEngineSettings.containerModeOverride(
                WinlatorOverride(containerId = "3"),
            ),
        )

        assertEquals(3, merged.containerId)
        assertEquals("Global", merged.containerName)
        assertEquals("", merged.graphicsDriver)
        assertEquals("", merged.dxwrapper)
        assertEquals("", merged.wined3dRenderer)
        assertEquals("", merged.screenSize)
        assertEquals("", merged.lcAll)
        assertEquals("", merged.tz)
        assertEquals("", merged.box64Preset)
        assertEquals("", merged.winComponents)
        assertEquals("", merged.audioDriver)
        assertEquals("", merged.envVars)
        assertFalse(merged.save)
    }

    @Test
    fun globalModeWithoutOverrideIgnoresStaleRendererOutsideWined3d() {
        val stale = global().copy(dxwrapper = "dxvk", wined3dRenderer = "gdi")

        val merged = EffectiveEngineSettings.mergeWinlator(stale, null)

        assertEquals("dxvk", merged.dxwrapper)
        assertEquals("", merged.wined3dRenderer)
        assertTrue(merged.effectiveWined3dRenderer.isEmpty())
    }
}
