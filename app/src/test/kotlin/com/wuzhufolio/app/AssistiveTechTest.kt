package com.wuzhufolio.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * DEF-43 回归：辅助技术可用性校验。
 *
 * 守护「属性为空/类可加载 → 不动属性」与「存在不可加载的类 → 清空属性并报告」两条语义。
 * 该用例去掉实现（不调用 sanitize）必红：第三条会因 `applied` 为空而失败。
 */
class AssistiveTechTest {

    private class Recorder {
        val applied = mutableListOf<String>()
        fun apply(value: String) = applied.add(value)
    }

    @Test
    fun `no configuration leaves the property untouched`() {
        val recorder = Recorder()
        val missing = AssistiveTech.sanitize(configured = null, canLoad = { true }, apply = recorder::apply)
        assertTrue(missing.isEmpty())
        assertTrue(recorder.applied.isEmpty())

        val blank = AssistiveTech.sanitize(configured = "  , ", canLoad = { true }, apply = recorder::apply)
        assertTrue(blank.isEmpty())
        assertTrue(recorder.applied.isEmpty())
    }

    @Test
    fun `all classes available leaves the property untouched`() {
        val recorder = Recorder()
        val missing = AssistiveTech.sanitize(
            configured = "com.sun.java.accessibility.AccessBridge",
            canLoad = { true },
            apply = recorder::apply,
        )
        assertTrue(missing.isEmpty())
        assertTrue(recorder.applied.isEmpty())
    }

    @Test
    fun `missing class clears the property and reports it`() {
        val recorder = Recorder()
        val missing = AssistiveTech.sanitize(
            configured = "com.sun.java.accessibility.AccessBridge",
            canLoad = { false },
            apply = recorder::apply,
        )
        assertEquals(listOf("com.sun.java.accessibility.AccessBridge"), missing)
        assertEquals(listOf(""), recorder.applied)
    }

    @Test
    fun `one missing class among several clears the property once`() {
        val recorder = Recorder()
        val missing = AssistiveTech.sanitize(
            configured = "java.lang.String, com.example.NotBundled",
            canLoad = { it != "com.example.NotBundled" },
            apply = recorder::apply,
        )
        assertEquals(listOf("com.example.NotBundled"), missing)
        assertEquals(listOf(""), recorder.applied)
    }
}
