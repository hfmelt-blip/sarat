package com.sarat.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.sarat.app.ui.AppNav
import com.sarat.app.ui.theme.C
import com.sarat.app.ui.theme.SaratTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // الوضع الداكن يتبع إعداد الموبايل، وتغييره يعيد إنشاء الشاشة تلقائياً
        val night = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        C.dark = night
        val clear = android.graphics.Color.TRANSPARENT
        enableEdgeToEdge(
            statusBarStyle = if (night) SystemBarStyle.dark(clear) else SystemBarStyle.light(clear, clear),
            navigationBarStyle = if (night) SystemBarStyle.dark(C.Card.toArgb()) else SystemBarStyle.light(C.Card.toArgb(), C.Card.toArgb()),
        )
        val repo = (application as SaratApp).container.repository
        val startAction = intent?.getStringExtra(com.sarat.app.notify.Reminders.EXTRA_OPEN)

        setContent {
            SaratTheme {
                // الواجهة عربية دائماً، فنثبت الاتجاه من اليمين لليسار بغض النظر عن لغة الجهاز
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    val hasProfile by produceState<Boolean?>(initialValue = null) {
                        value = repo.getProfile() != null
                    }
                    Box(Modifier.fillMaxSize().background(C.Ground)) {
                        hasProfile?.let { AppNav(hasProfile = it, startAction = startAction) }
                    }
                }
            }
        }
    }
}
