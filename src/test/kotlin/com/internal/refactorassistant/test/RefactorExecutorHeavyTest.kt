package com.internal.refactorassistant.test

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.internal.refactorassistant.executor.RefactorExecutor
import com.internal.refactorassistant.model.SynonymDictionary
import com.internal.refactorassistant.planner.RefactorPlanner
import com.internal.refactorassistant.report.ReportRenderer
import com.internal.refactorassistant.rules.NamingRuleEngine
import java.nio.file.Path
import kotlin.test.assertTrue

class RefactorExecutorHeavyTest : BasePlatformTestCase() {
    override fun getTestDataPath(): String = Path.of("src/test/resources/testData").toAbsolutePath().toString()

    fun testDryRunReportShapeForSampleProject() {
        myFixture.copyDirectoryToProject("sampleProject", "")

        val request = TestFixtures.sampleRequest().copy(
            options = TestFixtures.sampleRequest().options.copy(dryRunOnly = false),
        )
        val planner = RefactorPlanner(NamingRuleEngine(request, SynonymDictionary()))
        val plan = planner.buildPlan(request, TestFixtures.sampleScanResult(Path.of(project.basePath!!)))
        val report = RefactorExecutor(project).execute(plan)

        val markdown = ReportRenderer.renderExecutionMarkdown(report)
        assertTrue(markdown.contains("Refactor Execution Report"))
        assertTrue(report.phaseResults.isNotEmpty())
    }
}
