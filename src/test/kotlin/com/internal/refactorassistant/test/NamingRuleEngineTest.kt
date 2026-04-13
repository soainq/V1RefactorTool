package com.internal.refactorassistant.test

import com.internal.refactorassistant.model.CustomNamingRule
import com.internal.refactorassistant.model.RenameTargetKind
import com.internal.refactorassistant.model.SynonymDictionary
import com.internal.refactorassistant.rules.NamingRuleEngine
import kotlin.test.Test
import kotlin.test.assertEquals

class NamingRuleEngineTest {
    @Test
    fun `renames kotlin class names with deterministic token replacement`() {
        val engine = NamingRuleEngine(TestFixtures.sampleRequest(), SynonymDictionary())

        assertEquals("HomeActivity", engine.renameClassName("MainActivity"))
        assertEquals("HomeFragment", engine.renameClassName("MainFragment"))
    }

    @Test
    fun `renames resource names to snake_case`() {
        val engine = NamingRuleEngine(TestFixtures.sampleRequest(), SynonymDictionary())

        assertEquals("activity_home", engine.renameResourceName("activity_main", RenameTargetKind.LAYOUT))
        assertEquals("home_screen_title", engine.renameResourceName("main_screen_title", RenameTargetKind.STRING))
    }

    @Test
    fun `applies custom regex rules after token replacement`() {
        val request = TestFixtures.sampleRequest().copy(
            customNamingRules = listOf(
                CustomNamingRule(
                    description = "Drawable prefix mapping",
                    pattern = "^ic_home_(.*)$",
                    replacement = "illustration_home_$1",
                    targets = setOf(RenameTargetKind.DRAWABLE),
                ),
            ),
        )
        val engine = NamingRuleEngine(request, SynonymDictionary())

        assertEquals("illustration_home_setting", engine.renameResourceName("ic_main_setting", RenameTargetKind.DRAWABLE))
    }
}
