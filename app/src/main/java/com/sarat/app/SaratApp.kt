package com.sarat.app

import android.app.Application
import androidx.glance.appwidget.updateAll
import com.sarat.app.data.ApiKeyStore
import com.sarat.app.data.AppPrefs
import com.sarat.app.data.Repository
import com.sarat.app.data.db.AppDatabase
import com.sarat.app.data.gemini.GeminiClient
import com.sarat.app.notify.Reminders
import com.sarat.app.widget.SaratWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class AppContainer(app: Application) {
    private val db = AppDatabase.create(app)
    val repository = Repository(db)
    val apiKeys = ApiKeyStore(app)
    val prefs = AppPrefs(app)
    val thumbs = com.sarat.app.util.Thumbs(app)
    val gemini = GeminiClient(
        keyProvider = { apiKeys.current },
        models = listOf(BuildConfig.GEMINI_MODEL, GeminiClient.FALLBACK_MODEL).distinct(),
    )
}

class SaratApp : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        Reminders.createChannel(this)
        Reminders.scheduleAll(this, replace = false)

        // أي تغيير بالوجبات أو الملف أو الوزن يحدّث الودجت
        appScope.launch {
            combine(container.repository.dailyTotals, container.repository.profile, container.repository.weights) { _, _, _ -> }
                .collect { runCatching { SaratWidget().updateAll(this@SaratApp) } }
        }
    }
}
