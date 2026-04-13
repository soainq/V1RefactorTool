package com.internal.refactorassistant.test

import com.internal.refactorassistant.model.RefactorItemType
import com.internal.refactorassistant.model.SuggestionSource
import com.internal.refactorassistant.model.UsedNameEntry
import com.internal.refactorassistant.model.UsedNamesRegistry
import com.internal.refactorassistant.suggest.SuggestionService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SuggestionServiceTest {
    private val service = SuggestionService()

    @Test
    fun `used names are filtered from suggestions by default`() {
        val item = TestFixtures.scannedItem(
            type = RefactorItemType.ACTIVITY,
            oldName = "MainActivity",
        )
        val registry = UsedNamesRegistry(
            namesByType = mapOf(
                RefactorItemType.ACTIVITY to listOf(
                    UsedNameEntry(
                        name = "HomeActivity",
                        lastUsedVersion = "reskin_v1",
                        lastUsedTimestamp = "2026-04-10T10:30:00+07:00",
                    )
                )
            )
        )

        val result = service.buildReviewItems(
            items = listOf(item),
            registry = registry,
            existingNamesByType = emptyMap(),
            showPreviouslyUsedNames = false,
        ).first()

        assertFalse(result.suggestions.any { it.value == "HomeActivity" })
        assertEquals("DashboardActivity", result.selectedNewName)
        assertEquals(SuggestionSource.EXACT_PHRASE, result.selectedSuggestionSource)
    }

    @Test
    fun `exact phrase replacement is preferred for activity`() {
        val item = TestFixtures.scannedItem(
            type = RefactorItemType.ACTIVITY,
            oldName = "MainActivity",
        )

        val result = service.buildReviewItems(listOf(item), UsedNamesRegistry(), emptyMap(), showPreviouslyUsedNames = false).first()

        assertEquals("HomeActivity", result.selectedNewName)
        assertEquals(SuggestionSource.EXACT_PHRASE, result.selectedSuggestionSource)
    }

    @Test
    fun `setting activity becomes preference activity`() {
        val item = TestFixtures.scannedItem(
            type = RefactorItemType.ACTIVITY,
            oldName = "SettingActivity",
        )

        val result = service.buildReviewItems(listOf(item), UsedNamesRegistry(), emptyMap(), showPreviouslyUsedNames = false).first()

        assertEquals("PreferenceActivity", result.selectedNewName)
    }

    @Test
    fun `main fragment becomes home fragment`() {
        val item = TestFixtures.scannedItem(
            type = RefactorItemType.FRAGMENT,
            oldName = "MainFragment",
        )

        val result = service.buildReviewItems(listOf(item), UsedNamesRegistry(), emptyMap(), showPreviouslyUsedNames = false).first()

        assertEquals("HomeFragment", result.selectedNewName)
        assertEquals("READY", result.status)
    }

    @Test
    fun `setting viewmodel becomes preference viewmodel`() {
        val item = TestFixtures.scannedItem(
            type = RefactorItemType.VIEWMODEL,
            oldName = "SettingViewModel",
        )

        val result = service.buildReviewItems(listOf(item), UsedNamesRegistry(), emptyMap(), showPreviouslyUsedNames = false).first()

        assertEquals("PreferenceViewModel", result.selectedNewName)
        assertEquals("READY", result.status)
    }

    @Test
    fun `exact phrase replacement is preferred for resource`() {
        val item = TestFixtures.scannedItem(
            type = RefactorItemType.DRAWABLE,
            oldName = "ic_setting",
        )

        val result = service.buildReviewItems(listOf(item), UsedNamesRegistry(), emptyMap(), showPreviouslyUsedNames = false).first()

        assertEquals("ic_preference", result.selectedNewName)
        assertEquals(SuggestionSource.EXACT_PHRASE, result.selectedSuggestionSource)
    }

    @Test
    fun `activity main becomes activity home`() {
        val item = TestFixtures.scannedItem(
            type = RefactorItemType.LAYOUT,
            oldName = "activity_main",
        )

        val result = service.buildReviewItems(listOf(item), UsedNamesRegistry(), emptyMap(), showPreviouslyUsedNames = false).first()

        assertEquals("activity_home", result.selectedNewName)
    }

    @Test
    fun `bottom sheet becomes bot sheet`() {
        val item = TestFixtures.scannedItem(
            type = RefactorItemType.STRING,
            oldName = "bottom_sheet",
        )

        val result = service.buildReviewItems(listOf(item), UsedNamesRegistry(), emptyMap(), showPreviouslyUsedNames = false).first()

        assertEquals("bot_sheet", result.selectedNewName)
        assertEquals("READY", result.status)
    }

    @Test
    fun `fragment item detail becomes fragment entry info`() {
        val item = TestFixtures.scannedItem(
            type = RefactorItemType.LAYOUT,
            oldName = "fragment_item_detail",
        )

        val result = service.buildReviewItems(listOf(item), UsedNamesRegistry(), emptyMap(), showPreviouslyUsedNames = false).first()

        assertEquals("fragment_entry_info", result.selectedNewName)
    }

    @Test
    fun `token synonym replacement keeps system suffix and replaces business tokens`() {
        val item = TestFixtures.scannedItem(
            type = RefactorItemType.ACTIVITY,
            oldName = "LanguageSettingActivity",
        )

        val result = service.buildReviewItems(listOf(item), UsedNamesRegistry(), emptyMap(), showPreviouslyUsedNames = false).first()

        assertEquals("LangPreferenceActivity", result.selectedNewName)
        assertEquals(SuggestionSource.EXACT_PHRASE, result.selectedSuggestionSource)
    }

    @Test
    fun `feedback bottom sheet becomes response bottom sheet`() {
        val item = TestFixtures.scannedItem(
            type = RefactorItemType.KOTLIN_CLASS,
            oldName = "FeedbackBottomSheet",
        )

        val result = service.buildReviewItems(listOf(item), UsedNamesRegistry(), emptyMap(), showPreviouslyUsedNames = false).first()

        assertTrue(
            result.selectedNewName == "ResponseBottomSheet" ||
                result.selectedNewName == "ResponseBotSheet"
        )
        assertEquals("READY", result.status)
    }

    @Test
    fun `token synonym replacement handles multi token business names`() {
        val item = TestFixtures.scannedItem(
            type = RefactorItemType.ACTIVITY,
            oldName = "FFFItemDetailActivity",
        )

        val result = service.buildReviewItems(listOf(item), UsedNamesRegistry(), emptyMap(), showPreviouslyUsedNames = false).first()

        assertEquals("FFFEntryInfoActivity", result.selectedNewName)
        assertEquals(SuggestionSource.TOKEN_SYNONYM, result.selectedSuggestionSource)
    }

    @Test
    fun `whole phrase replacement is preferred over structural fallback when available`() {
        val item = TestFixtures.scannedItem(
            type = RefactorItemType.ACTIVITY,
            oldName = "TipAndTrickDetailActivity",
        )

        val result = service.buildReviewItems(listOf(item), UsedNamesRegistry(), emptyMap(), showPreviouslyUsedNames = false).first()

        assertEquals("GuideInfoActivity", result.selectedNewName)
        assertEquals(SuggestionSource.WHOLE_PHRASE_REPLACEMENT, result.selectedSuggestionSource)
        assertTrue(result.suggestions.none { it.value == "TipAndTrickDetailScreenActivity" && it.source == SuggestionSource.RULE_FALLBACK })
    }

    @Test
    fun `item is blocked when only structural append fallback would exist`() {
        val item = TestFixtures.scannedItem(
            type = RefactorItemType.ACTIVITY,
            oldName = "ReportActivity",
        )

        val result = service.buildReviewItems(listOf(item), UsedNamesRegistry(), emptyMap(), showPreviouslyUsedNames = false).first()

        assertEquals("BLOCKED", result.status)
        assertEquals("", result.selectedNewName)
        assertEquals("No acceptable replacement candidate", result.warning)
    }

    @Test
    fun `whole phrase replacement wins and no append fallback is kept for calculator`() {
        val item = TestFixtures.scannedItem(
            type = RefactorItemType.ACTIVITY,
            oldName = "CalculatorActivity",
        )

        val result = service.buildReviewItems(listOf(item), UsedNamesRegistry(), emptyMap(), showPreviouslyUsedNames = false).first()

        assertEquals("ComputeActivity", result.selectedNewName)
        assertTrue(
            result.selectedSuggestionSource == SuggestionSource.TOKEN_SYNONYM ||
                result.selectedSuggestionSource == SuggestionSource.WHOLE_PHRASE_REPLACEMENT
        )
        assertTrue(result.suggestions.none { it.value == "CalculatorScreenActivity" })
    }

    @Test
    fun `candidate retry skips conflicting first candidate and keeps item ready`() {
        val item = TestFixtures.scannedItem(
            type = RefactorItemType.ACTIVITY,
            oldName = "MainActivity",
        )

        val result = service.buildReviewItems(
            items = listOf(item),
            registry = UsedNamesRegistry(),
            existingNamesByType = mapOf(RefactorItemType.ACTIVITY to setOf("HomeActivity")),
            showPreviouslyUsedNames = false,
        ).first()

        assertEquals("DashboardActivity", result.selectedNewName)
        assertEquals("READY", result.status)
    }

    @Test
    fun `same old name generates one canonical suggestion reused across all items`() {
        val items = listOf(
            TestFixtures.scannedItem(id = "a", type = RefactorItemType.DRAWABLE, oldName = "ic_error"),
            TestFixtures.scannedItem(id = "b", type = RefactorItemType.DRAWABLE, oldName = "ic_error"),
        )

        val result = service.buildReviewItems(
            items = items,
            registry = UsedNamesRegistry(),
            existingNamesByType = emptyMap(),
            showPreviouslyUsedNames = false,
        )

        assertEquals(1, result.map { it.canonicalNewName }.distinct().size)
        assertEquals(1, result.map { it.selectedNewName }.distinct().size)
        assertTrue(result.all { it.status == "READY" })
    }

    @Test
    fun `abbreviation is used when synonym is unavailable`() {
        val item = TestFixtures.scannedItem(
            type = RefactorItemType.STRING,
            oldName = "language_configuration",
        )

        val result = service.buildReviewItems(
            items = listOf(item),
            registry = UsedNamesRegistry(),
            existingNamesByType = emptyMap(),
            showPreviouslyUsedNames = false,
        ).first()

        assertEquals("lang_config", result.selectedNewName)
        assertEquals(SuggestionSource.TOKEN_ABBREVIATION, result.selectedSuggestionSource)
    }

    @Test
    fun `reverse abbreviation mapping supports icon shorthand`() {
        val item = TestFixtures.scannedItem(
            type = RefactorItemType.DRAWABLE,
            oldName = "ic_setting",
        )

        val result = service.buildReviewItems(
            items = listOf(item),
            registry = UsedNamesRegistry(),
            existingNamesByType = emptyMap(),
            showPreviouslyUsedNames = false,
        ).first()

        assertEquals("ic_preference", result.selectedNewName)
        assertFalse(result.selectedNewName.contains("setting"))
    }

    @Test
    fun `locale alias mapping supports french and vietnamese codes`() {
        val french = service.buildReviewItems(
            items = listOf(TestFixtures.scannedItem(type = RefactorItemType.STRING, oldName = "language_fr")),
            registry = UsedNamesRegistry(),
            existingNamesByType = emptyMap(),
            showPreviouslyUsedNames = false,
        ).first()
        val vietnamese = service.buildReviewItems(
            items = listOf(TestFixtures.scannedItem(type = RefactorItemType.STRING, oldName = "vietnamese_title")),
            registry = UsedNamesRegistry(),
            existingNamesByType = emptyMap(),
            showPreviouslyUsedNames = false,
        ).first()

        assertTrue(french.suggestions.any { it.value.contains("french") || it.value.contains("fr") })
        assertTrue(vietnamese.suggestions.any { it.value.contains("vi") || it.value.contains("vietnam") })
    }

    @Test
    fun `duplicated suffixes are normalized away`() {
        val item = TestFixtures.scannedItem(
            type = RefactorItemType.DRAWABLE,
            oldName = "ic_close_screen",
        )

        val result = service.buildReviewItems(
            items = listOf(item),
            registry = UsedNamesRegistry(),
            existingNamesByType = emptyMap(),
            showPreviouslyUsedNames = false,
        ).first()

        assertFalse(result.suggestions.any { it.value == "ic_close_screen_screen" })
        assertFalse(result.selectedNewName.contains("screen_screen"))
    }

    @Test
    fun `duplicated suffixes are normalized away for done screen`() {
        val item = TestFixtures.scannedItem(
            type = RefactorItemType.DRAWABLE,
            oldName = "ic_done_screen",
        )

        val result = service.buildReviewItems(
            items = listOf(item),
            registry = UsedNamesRegistry(),
            existingNamesByType = emptyMap(),
            showPreviouslyUsedNames = false,
        ).first()

        assertFalse(result.suggestions.any { it.value == "ic_done_screen_screen" })
        assertFalse(result.selectedNewName.contains("screen_screen"))
    }

    @Test
    fun `semantic duplicate tokens are collapsed`() {
        val item = TestFixtures.scannedItem(
            type = RefactorItemType.ACTIVITY,
            oldName = "MainActivity",
        )

        val result = service.buildReviewItems(
            items = listOf(item),
            registry = UsedNamesRegistry(),
            existingNamesByType = mapOf(RefactorItemType.ACTIVITY to setOf("HomeActivity")),
            showPreviouslyUsedNames = false,
        ).first()

        assertFalse(result.suggestions.any { it.value.contains("HomeMain", ignoreCase = true) })
        assertFalse(result.suggestions.any { it.value.contains("MainHome", ignoreCase = true) })
        assertEquals("DashboardActivity", result.selectedNewName)
    }

    @Test
    fun `canonical new name stores normalized result not raw result`() {
        val items = listOf(
            TestFixtures.scannedItem(id = "a", type = RefactorItemType.DRAWABLE, oldName = "ic_done_screen"),
            TestFixtures.scannedItem(id = "b", type = RefactorItemType.DRAWABLE, oldName = "ic_done_screen"),
        )

        val result = service.buildReviewItems(
            items = items,
            registry = UsedNamesRegistry(),
            existingNamesByType = emptyMap(),
            showPreviouslyUsedNames = false,
        )

        assertFalse(result.first().canonicalNewName.contains("screen_screen"))
        assertEquals(result.first().canonicalNewName, result.last().canonicalNewName)
    }

    @Test
    fun `replace beats append for setting drawable`() {
        val item = TestFixtures.scannedItem(
            type = RefactorItemType.DRAWABLE,
            oldName = "ic_setting",
        )

        val result = service.buildReviewItems(
            items = listOf(item),
            registry = UsedNamesRegistry(),
            existingNamesByType = emptyMap(),
            showPreviouslyUsedNames = false,
        ).first()

        assertEquals("ic_preference", result.selectedNewName)
        assertFalse(result.suggestions.any { it.value == "ic_setting_preference" })
    }

    @Test
    fun `language model replaces business token instead of appending core`() {
        val item = TestFixtures.scannedItem(
            type = RefactorItemType.KOTLIN_CLASS,
            oldName = "LanguageModel",
        )

        val result = service.buildReviewItems(
            items = listOf(item),
            registry = UsedNamesRegistry(),
            existingNamesByType = emptyMap(),
            showPreviouslyUsedNames = false,
        ).first()

        assertEquals("LocaleModel", result.selectedNewName)
        assertEquals("READY", result.status)
    }

    @Test
    fun `language item becomes language entry or locale entry`() {
        val item = TestFixtures.scannedItem(
            type = RefactorItemType.KOTLIN_CLASS,
            oldName = "LanguageItem",
        )

        val result = service.buildReviewItems(
            items = listOf(item),
            registry = UsedNamesRegistry(),
            existingNamesByType = emptyMap(),
            showPreviouslyUsedNames = false,
        ).first()

        assertTrue(
            result.selectedNewName == "LanguageEntry" ||
                result.selectedNewName == "LocaleEntry"
        )
        assertEquals("READY", result.status)
    }

    @Test
    fun `main is not blocked by do not replace for business rename`() {
        val item = TestFixtures.scannedItem(
            type = RefactorItemType.ACTIVITY,
            oldName = "MainActivity",
        )

        val result = service.buildReviewItems(
            items = listOf(item),
            registry = UsedNamesRegistry(),
            existingNamesByType = emptyMap(),
            showPreviouslyUsedNames = false,
        ).first()

        assertEquals("HomeActivity", result.selectedNewName)
        assertEquals("READY", result.status)
    }

    @Test
    fun `fields data must not become fields data core`() {
        val item = TestFixtures.scannedItem(
            type = RefactorItemType.KOTLIN_CLASS,
            oldName = "FieldsData",
        )

        val result = service.buildReviewItems(
            items = listOf(item),
            registry = UsedNamesRegistry(),
            existingNamesByType = emptyMap(),
            showPreviouslyUsedNames = false,
        ).first()

        assertEquals("BLOCKED", result.status)
        assertTrue(result.suggestions.none { it.value == "FieldsDataCore" })
        assertEquals("No acceptable replacement candidate", result.warning)
    }

    @Test
    fun `flag view holder must not become flag view holder core`() {
        val item = TestFixtures.scannedItem(
            type = RefactorItemType.KOTLIN_CLASS,
            oldName = "FlagViewHolder",
        )

        val result = service.buildReviewItems(
            items = listOf(item),
            registry = UsedNamesRegistry(),
            existingNamesByType = emptyMap(),
            showPreviouslyUsedNames = false,
        ).first()

        assertEquals("BLOCKED", result.status)
        assertTrue(result.suggestions.none { it.value == "FlagViewHolderCore" })
    }

    @Test
    fun `home ui model must not become home ui model core`() {
        val item = TestFixtures.scannedItem(
            type = RefactorItemType.KOTLIN_CLASS,
            oldName = "HomeUIModel",
        )

        val result = service.buildReviewItems(
            items = listOf(item),
            registry = UsedNamesRegistry(),
            existingNamesByType = emptyMap(),
            showPreviouslyUsedNames = false,
        ).first()

        assertEquals("BLOCKED", result.status)
        assertTrue(result.suggestions.none { it.value == "HomeUIModelCore" })
    }

    @Test
    fun `append based fallback is rejected for resource names`() {
        val item = TestFixtures.scannedItem(
            type = RefactorItemType.DRAWABLE,
            oldName = "ic_setting",
        )

        val result = service.buildReviewItems(
            items = listOf(item),
            registry = UsedNamesRegistry(),
            existingNamesByType = emptyMap(),
            showPreviouslyUsedNames = false,
        ).first()

        assertTrue(result.suggestions.none { it.value == "ic_setting_preference" })
        assertEquals("ic_preference", result.selectedNewName)
    }

    @Test
    fun `canonical name never uses append fallback for business items`() {
        val items = listOf(
            TestFixtures.scannedItem(id = "a", type = RefactorItemType.KOTLIN_CLASS, oldName = "FieldsData"),
            TestFixtures.scannedItem(id = "b", type = RefactorItemType.KOTLIN_CLASS, oldName = "FieldsData"),
        )

        val result = service.buildReviewItems(
            items = items,
            registry = UsedNamesRegistry(),
            existingNamesByType = emptyMap(),
            showPreviouslyUsedNames = false,
        )

        assertTrue(result.all { it.canonicalNewName.isBlank() })
        assertTrue(result.all { it.status == "BLOCKED" })
    }

    @Test
    fun `do not replace tokens are preserved while replaceable suffix still changes`() {
        val item = TestFixtures.scannedItem(
            type = RefactorItemType.ACTIVITY,
            oldName = "BaseSettingActivity",
        )

        val result = service.buildReviewItems(
            items = listOf(item),
            registry = UsedNamesRegistry(),
            existingNamesByType = emptyMap(),
            showPreviouslyUsedNames = false,
        ).first()

        assertEquals("BasePreferenceActivity", result.selectedNewName)
        assertTrue(result.selectedNewName.startsWith("Base"))
    }

    @Test
    fun `blocked only after all candidate generations fail`() {
        val item = TestFixtures.scannedItem(
            type = RefactorItemType.ACTIVITY,
            oldName = "MainActivity",
            safetyLevel = com.internal.refactorassistant.model.SafetyLevel.REVIEW_REQUIRED,
        )

        val result = service.buildReviewItems(
            items = listOf(item),
            registry = UsedNamesRegistry(
                namesByType = mapOf(
                    RefactorItemType.ACTIVITY to listOf(
                        UsedNameEntry("DashboardActivity", "v1", "ts"),
                        UsedNameEntry("LandingActivity", "v1", "ts"),
                    )
                )
            ),
            existingNamesByType = mapOf(
                RefactorItemType.ACTIVITY to setOf("HomeActivity", "MainScreenActivity", "MainEntryActivity", "MainFlowActivity")
            ),
            showPreviouslyUsedNames = false,
        ).first()

        assertEquals("BLOCKED", result.status)
        assertTrue(result.warning.contains("Blocked", ignoreCase = true))
    }
}
