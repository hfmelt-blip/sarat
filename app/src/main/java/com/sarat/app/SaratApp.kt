package com.sarat.app

import android.app.Application
import com.sarat.app.data.ApiKeyStore
import com.sarat.app.data.AppPrefs
import com.sarat.app.data.Repository
import com.sarat.app.data.db.AppDatabase
import com.sarat.app.data.gemini.GeminiClient

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

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
