package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelDisplayNameWindowsPathRegressionTest {
    @Test fun windowsSlashPathSelectsModelName() {
        assertEquals("qwen3-coder", ModelDisplayName.of("C:\\models\\qwen3-coder"))
    }
    @Test fun windowsTrailingSlashIsIgnored() {
        assertEquals("gemma-4", ModelDisplayName.of("models\\gemma-4\\"))
    }
    @Test fun forwardSlashStillWorks() {
        assertEquals("gemma-4", ModelDisplayName.of("models/gemma-4"))
    }
}
