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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.sarat.app.ui.AppNav
import com.sarat.app.ui.theme.C
import com.sarat.app.ui.theme.SaratTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // الواجهة فاتحة دائماً، فنخلي أيقونات شريط الحالة داكنة
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.WHITE, android.graphics.Color.WHITE),
        )
        val repo = (application as SaratApp).container.repository

        setContent {
            SaratTheme {
                // الواجهة عربية دائماً، فنثبت الاتجاه من اليمين لليسار بغض النظر عن لغة الجهاز
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    val hasProfile by produceState<Boolean?>(initialValue = null) {
                        value = repo.getProfile() != null
                    }
                    Box(Modifier.fillMaxSize().background(C.Ground)) {
                        hasProfile?.let { AppNav(hasProfile = it) }
                    }
                }
            }
        }
    }
}
