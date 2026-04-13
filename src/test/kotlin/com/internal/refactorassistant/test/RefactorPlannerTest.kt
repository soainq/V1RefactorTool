package com.internal.refactorassistant.test

import com.internal.refactorassistant.model.SynonymDictionary
import com.internal.refactorassistant.planner.RefactorPlanner
import com.internal.refactorassistant.rules.NamingRuleEngine
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RefactorPlannerTest {
    private val root = Path.of("src/test/resources/testData")

    @Test
    fun `builds a v1 plan for the sample Android project`() {
        val request = TestFixtures.sampleRequest()
        val planner = RefactorPlanner(NamingRuleEngine(request, SynonymDictionary()))
        val plan = planner.buildPlan(request, TestFixtures.sampleScanResult(root))

        assertEquals(1, plan.packageOperations.size)
        assertEquals(4, plan.sourceOperations.size)
        assertEquals(4, plan.resourceOperations.size)
        assertTrue(plan.referenceOperations.isNotEmpty())
        assertTrue(plan.conflicts.isEmpty(), "Expected no blocking conflicts for the sample project")

        val classNames = plan.sourceOperations.map { it.after }
        assertTrue("HomeActivity" in classNames)
        assertTrue("HomeFragment" in classNames)

        val resourceNames = plan.resourceOperations.map { it.after }
        assertTrue("activity_home" in resourceNames)
        assertTrue("ic_home_setting" in resourceNames)
        assertTrue("home_screen_title" in resourceNames)
        assertTrue("home_content_padding" in resourceNames)
    }
}
