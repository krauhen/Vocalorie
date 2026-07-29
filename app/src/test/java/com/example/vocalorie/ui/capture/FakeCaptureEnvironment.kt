package com.example.vocalorie.ui.capture

import com.example.vocalorie.ai.NutritionEstimateOutcome
import com.example.vocalorie.ai.NutritionEstimator
import com.example.vocalorie.data.BackupImportResult
import com.example.vocalorie.data.repository.ActivityRepository
import com.example.vocalorie.data.repository.BackupRepository
import com.example.vocalorie.data.repository.FakeVocalorieData
import com.example.vocalorie.data.repository.MealCacheRepository
import com.example.vocalorie.data.repository.MealRepository
import com.example.vocalorie.data.repository.SecretRepository
import com.example.vocalorie.data.repository.ThemeSettingsRepository
import com.example.vocalorie.model.ConfidenceLevel
import com.example.vocalorie.model.FoodItemEstimate
import com.example.vocalorie.model.MealCategory
import com.example.vocalorie.model.NutritionAgentResult
import com.example.vocalorie.model.NutritionTotals
import com.example.vocalorie.settings.InMemorySharedPreferences
import com.example.vocalorie.settings.NutritionSettingsStore
import com.example.vocalorie.settings.OpenAiApiKeyStore
import com.example.vocalorie.settings.ThemeSettingsStore
import com.example.vocalorie.settings.ToolSettings
import com.example.vocalorie.settings.ToolSettingsStore
import com.example.vocalorie.settings.testContext
import com.example.vocalorie.ui.voice.GalleryImageAttachment
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import java.time.Instant
import java.time.ZoneId

/**
 * Everything [MealCaptureViewModel] depends on, wired to in-memory doubles.
 *
 * The data repositories are the real ones over [FakeVocalorieData]'s DAO fakes, so the state holder
 * is exercised against the same contracts production uses — including Room-shaped change
 * notification. The two collaborators that cannot exist on the JVM are doubled outright: an abstract
 * Room `@Database` cannot be instantiated (so [BackupRepository] is a fake), and there is no
 * AndroidKeyStore (so a saved OpenAI key can never be decrypted here).
 */
internal class FakeCaptureEnvironment(
    val data: FakeVocalorieData = FakeVocalorieData(),
    val estimator: FakeNutritionEstimator = FakeNutritionEstimator(),
    val backups: FakeBackupRepository = FakeBackupRepository(),
    private val settingsPrefs: InMemorySharedPreferences = InMemorySharedPreferences(),
) {
    private val settingsContext = testContext(settingsPrefs)

    val themeSettingsStore = ThemeSettingsStore(settingsContext)
    val nutritionSettingsStore = NutritionSettingsStore(settingsContext)
    val toolSettingsStore = ToolSettingsStore(testContext(InMemorySharedPreferences()))

    val mealRepository = MealRepository(
        mealDao = data.mealDao,
        cacheDao = data.cacheDao,
        transactions = data.transactions,
        tableChanges = data.tableChanges,
        dispatcher = Dispatchers.Unconfined,
    )

    val activityRepository = ActivityRepository(
        activityDao = data.activityDao,
        tableChanges = data.tableChanges,
        dispatcher = Dispatchers.Unconfined,
    )

    val mealCacheRepository = MealCacheRepository(cacheDao = data.cacheDao, dispatcher = Dispatchers.Unconfined)

    val themeSettingsRepository = ThemeSettingsRepository(
        store = themeSettingsStore,
        nutritionSettings = nutritionSettingsStore,
        dispatcher = Dispatchers.Unconfined,
    )

    val secretRepository = SecretRepository(
        apiKeyStore = OpenAiApiKeyStore(testContext(InMemorySharedPreferences())),
        toolSettingsStore = toolSettingsStore,
        dispatcher = Dispatchers.Unconfined,
    )

    /** A clock the test moves by hand, so no assertion depends on the machine's wall time. */
    var nowMillis: Long = 1_700_000_000_000L

    fun viewModel(
        initialRuntimeApiKey: String = "",
        zone: ZoneId = ZoneId.of("UTC"),
    ): MealCaptureViewModel = MealCaptureViewModel(
        mealRepository = mealRepository,
        activityRepository = activityRepository,
        mealCacheRepository = mealCacheRepository,
        themeSettingsRepository = themeSettingsRepository,
        secretRepository = secretRepository,
        backupRepository = backups,
        nutritionEstimator = estimator,
        initialSettings = themeSettingsRepository.currentSnapshot(),
        initialRuntimeApiKey = initialRuntimeApiKey,
        clock = { Instant.ofEpochMilli(nowMillis) },
        zone = zone,
    )
}

/** Records every estimate request and replies with whatever the test configured. */
internal class FakeNutritionEstimator : NutritionEstimator {
    val requests = mutableListOf<String>()
    val imageCounts = mutableListOf<Int>()
    val keys = mutableListOf<String>()

    var outcome: NutritionEstimateOutcome = NutritionEstimateOutcome(result = nutritionAgentResult())
    var failWith: Throwable? = null

    /** Set to hold the estimate open, so a test can act while one is genuinely in flight. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun estimate(
        openAiApiKey: String,
        query: String,
        toolSettings: ToolSettings,
        imageAttachments: List<GalleryImageAttachment>,
    ): NutritionEstimateOutcome {
        requests += query
        imageCounts += imageAttachments.size
        keys += openAiApiKey
        gate?.await()
        failWith?.let { throw it }
        return outcome
    }
}

internal fun nutritionAgentResult(
    query: String = "Apfel",
    caloriesKcal: Double = 52.0,
): NutritionAgentResult = NutritionAgentResult(
    title = query,
    query = query,
    items = listOf(
        FoodItemEstimate(
            name = query,
            quantity = "1",
            amountGml = 100.0,
            caloriesKcal = caloriesKcal,
            proteinG = null,
            carbsG = null,
            fatG = null,
            saturatedFatG = null,
            sugarG = null,
            saltG = null,
            source = "",
            reasoning = "",
        ),
    ),
    totals = NutritionTotals(
        caloriesKcal = caloriesKcal,
        amountGml = 100.0,
        proteinG = null,
        carbsG = null,
        fatG = null,
        saturatedFatG = null,
        sugarG = null,
        saltG = null,
    ),
    assumptions = emptyList(),
    warnings = emptyList(),
    confidence = ConfidenceLevel.MEDIUM,
    needsHumanReview = false,
    category = MealCategory.MEAL,
)

/** Stands in for the Room-backed backup repository, which cannot be built on the JVM. */
internal class FakeBackupRepository : BackupRepository {
    val exported = mutableListOf<Pair<String, Long>>()
    val imported = mutableListOf<String>()

    var exportFailure: Throwable? = null
    var importFailure: Throwable? = null
    var importResult: BackupImportResult = BackupImportResult(imported = 3, skipped = 1)

    override suspend fun exportTo(documentUri: String, exportedAtEpochMillis: Long) {
        exportFailure?.let { throw it }
        exported += documentUri to exportedAtEpochMillis
    }

    override suspend fun importFrom(documentUri: String): BackupImportResult {
        importFailure?.let { throw it }
        imported += documentUri
        return importResult
    }
}
