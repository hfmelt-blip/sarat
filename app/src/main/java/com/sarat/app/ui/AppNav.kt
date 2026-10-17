package com.sarat.app.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sarat.app.AppContainer
import com.sarat.app.SaratApp
import com.sarat.app.ui.components.Tab
import com.sarat.app.ui.history.HistoryScreen
import com.sarat.app.ui.history.HistoryViewModel
import com.sarat.app.ui.home.HomeScreen
import com.sarat.app.ui.home.HomeViewModel
import com.sarat.app.ui.meal.MealPhotoScreen
import com.sarat.app.ui.meal.MealViewModel
import com.sarat.app.ui.meal.TextEntryScreen
import com.sarat.app.ui.onboarding.OnboardingScreen
import com.sarat.app.ui.onboarding.OnboardingViewModel
import com.sarat.app.ui.progress.ProgressScreen
import com.sarat.app.ui.progress.ProgressViewModel
import com.sarat.app.ui.settings.ApiKeyScreen
import com.sarat.app.ui.settings.ApiKeyViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

object Routes {
    const val ONBOARDING = "onboarding"
    const val EDIT_PROFILE = "edit_profile"
    const val HOME = "home"
    const val HISTORY = "history"
    const val PROGRESS = "progress"
    const val MEAL_TEXT = "meal/text"
    const val API_KEY_SETUP = "api_key_setup"
    const val API_KEY = "api_key"
    const val PLAN = "plan"
    const val MEAL_PHOTO = "meal/photo?uri={uri}"
    fun mealPhoto(uri: Uri) = "meal/photo?uri=${Uri.encode(uri.toString())}"
}

@Composable
private fun container(): AppContainer = (LocalContext.current.applicationContext as SaratApp).container

private fun NavHostController.goTab(tab: Tab) {
    navigate(tab.route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun AppNav(hasProfile: Boolean) {
    val nav = rememberNavController()
    val c = container()
    val app = LocalContext.current.applicationContext as Application

    // شاشة الترحيب تطلع مرة وحدة باليوم، والاحتفال مرة وحدة لكل إنجاز
    var welcomeDay by remember { mutableStateOf(c.prefs.lastWelcomeDay) }
    var celebrated by remember { mutableStateOf(c.prefs.lastCelebratedStreak) }

    NavHost(nav, startDestination = if (hasProfile) Routes.HOME else Routes.ONBOARDING) {

        composable(Routes.ONBOARDING) {
            val vm = viewModel { OnboardingViewModel(c.repository, editMode = false) }
            OnboardingScreen(
                vm,
                onDone = {
                    val next = if (c.apiKeys.hasKey) Routes.HOME else Routes.API_KEY_SETUP
                    nav.navigate(next) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                },
                onExit = null,
            )
        }

        composable(Routes.API_KEY_SETUP) {
            val vm = viewModel { ApiKeyViewModel(c.apiKeys, c.gemini) }
            ApiKeyScreen(
                vm,
                onSaved = { nav.navigate(Routes.HOME) { popUpTo(Routes.API_KEY_SETUP) { inclusive = true } } },
                onBack = null,
            )
        }

        composable(Routes.API_KEY) {
            val vm = viewModel { ApiKeyViewModel(c.apiKeys, c.gemini) }
            ApiKeyScreen(vm, onSaved = { nav.popBackStack() }, onBack = { nav.popBackStack() })
        }

        composable(Routes.EDIT_PROFILE) {
            val vm = viewModel { OnboardingViewModel(c.repository, editMode = true) }
            OnboardingScreen(vm, onDone = { nav.popBackStack() }, onExit = { nav.popBackStack() })
        }

        composable(Routes.HOME) {
            val vm = viewModel { HomeViewModel(c.repository, c.thumbs) }
            val key by c.apiKeys.key.collectAsStateWithLifecycle()
            HomeScreen(
                vm,
                hasKey = key.isNotBlank(),
                onApiKey = { nav.navigate(Routes.API_KEY) },
                onTab = nav::goTab,
                onPhoto = { uri -> nav.navigate(Routes.mealPhoto(uri)) },
                onText = { nav.navigate(Routes.MEAL_TEXT) },
                onPlan = { nav.navigate(Routes.PLAN) },
                onWeighIn = { nav.goTab(Tab.PROGRESS) },
                showWelcome = welcomeDay != com.sarat.app.util.Dates.todayEpoch(),
                onWelcomeShown = {
                    val t = com.sarat.app.util.Dates.todayEpoch()
                    c.prefs.lastWelcomeDay = t
                    welcomeDay = t
                },
                lastCelebrated = celebrated,
                onCelebrated = { n -> c.prefs.lastCelebratedStreak = n; celebrated = n },
            )
        }

        composable(Routes.HISTORY) {
            val vm = viewModel { HistoryViewModel(c.repository) }
            HistoryScreen(vm, onTab = nav::goTab, onWeighIn = { nav.goTab(Tab.PROGRESS) })
        }

        composable(Routes.PROGRESS) {
            val vm = viewModel { ProgressViewModel(c.repository) }
            ProgressScreen(vm, onTab = nav::goTab)
        }

        composable(Routes.PLAN) {
            val vm = viewModel { com.sarat.app.ui.plan.PlanViewModel(c.repository, c.gemini, c.prefs) }
            com.sarat.app.ui.plan.PlanScreen(vm, onBack = { nav.popBackStack() }, onSaved = { nav.popBackStack(Routes.HOME, inclusive = false) })
        }

        composable(Routes.MEAL_TEXT) {
            val vm = viewModel { MealViewModel(app, c.repository, c.gemini, c.thumbs) }
            TextEntryScreen(vm, onBack = { nav.popBackStack() }, onSaved = { nav.popBackStack(Routes.HOME, inclusive = false) })
        }

        composable(
            Routes.MEAL_PHOTO,
            arguments = listOf(navArgument("uri") { type = NavType.StringType }),
        ) { entry ->
            val uri = Uri.parse(entry.arguments?.getString("uri").orEmpty())
            val vm = viewModel { MealViewModel(app, c.repository, c.gemini, c.thumbs) }
            MealPhotoScreen(vm, uri, onBack = { nav.popBackStack() }, onSaved = { nav.popBackStack(Routes.HOME, inclusive = false) })
        }
    }
}
