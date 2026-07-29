package com.example.vocalorie

import android.content.Context
import com.example.vocalorie.ai.KoogNutritionAgent
import com.example.vocalorie.ai.NutritionEstimator
import com.example.vocalorie.data.ContentResolverDocumentTextStore
import com.example.vocalorie.data.VocalorieDatabase
import com.example.vocalorie.data.repository.ActivityRepository
import com.example.vocalorie.data.repository.BackupRepository
import com.example.vocalorie.data.repository.MealCacheRepository
import com.example.vocalorie.data.repository.MealRepository
import com.example.vocalorie.data.repository.RoomBackupRepository
import com.example.vocalorie.data.repository.RoomDatabaseSeam
import com.example.vocalorie.data.repository.SecretRepository
import com.example.vocalorie.data.repository.ThemeSettingsRepository
import com.example.vocalorie.settings.NutritionSettingsStore
import com.example.vocalorie.settings.OpenAiApiKeyStore
import com.example.vocalorie.settings.ThemeSettingsStore
import com.example.vocalorie.settings.ToolSettingsStore
import com.example.vocalorie.tools.HttpTextFetcher
import com.example.vocalorie.tools.KtorHttpTextFetcher

/**
 * The application's object graph: one instance of every long-lived collaborator, created once per
 * process and handed to whoever needs it.
 *
 * Deliberately a hand-written container rather than a dependency-injection framework, and
 * deliberately shaped like [VocalorieDatabase.get] — the same double-checked singleton this app
 * already uses — so it introduces no new concept to learn.
 *
 * What it exists to stop:
 * - `ThemeSettingsStore` was constructed twice (once per activity composition, once per capture
 *   screen), so a preference write through one instance was invisible to the other until a reload.
 * - The nutrition agent and its HTTP client were reached through process-wide `shared` singletons
 *   that no one owned.
 *
 * Everything below the repository boundary takes a `Context`; nothing above it does.
 */
class AppContainer private constructor(context: Context) {

    private val appContext: Context = context.applicationContext

    val database: VocalorieDatabase = VocalorieDatabase.get(appContext)

    private val databaseSeam = RoomDatabaseSeam(database)

    /** The single appearance store in the process. */
    val themeSettingsStore: ThemeSettingsStore = ThemeSettingsStore(appContext)

    /** The single nutrition-target store in the process; reads the same preference file. */
    val nutritionSettingsStore: NutritionSettingsStore = NutritionSettingsStore(appContext)

    private val apiKeyStore = OpenAiApiKeyStore(appContext)

    private val toolSettingsStore = ToolSettingsStore(appContext)

    /** One HTTP text fetcher, so the research tools never build an engine per invocation. */
    val httpTextFetcher: HttpTextFetcher = KtorHttpTextFetcher.shared

    /** One nutrition agent, holding its per-key prompt-executor cache for the whole process. */
    val nutritionEstimator: NutritionEstimator = KoogNutritionAgent(httpTextFetcher)

    val mealRepository = MealRepository(
        mealDao = database.mealDao(),
        cacheDao = database.cacheDao(),
        transactions = databaseSeam,
        tableChanges = databaseSeam,
    )

    val activityRepository = ActivityRepository(
        activityDao = database.activityDao(),
        tableChanges = databaseSeam,
    )

    val mealCacheRepository = MealCacheRepository(cacheDao = database.cacheDao())

    val themeSettingsRepository = ThemeSettingsRepository(
        store = themeSettingsStore,
        nutritionSettings = nutritionSettingsStore,
    )

    val secretRepository = SecretRepository(
        apiKeyStore = apiKeyStore,
        toolSettingsStore = toolSettingsStore,
    )

    val backupRepository: BackupRepository = RoomBackupRepository(
        database = database,
        documents = ContentResolverDocumentTextStore(appContext),
    )

    /**
     * The build-time key from `local.properties`, used until the user saves one in Settings. Read
     * here so nothing above the container has to know `BuildConfig` exists.
     */
    val defaultOpenAiApiKey: String = BuildConfig.DEFAULT_OPENAI_API_KEY

    companion object {
        @Volatile
        private var instance: AppContainer? = null

        fun get(context: Context): AppContainer {
            instance?.let { return it }
            return synchronized(this) {
                instance ?: AppContainer(context).also { instance = it }
            }
        }
    }
}
