package com.sarat.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sarat.app.data.ApiKeyStore
import com.sarat.app.data.gemini.GeminiClient
import com.sarat.app.ui.components.InfoNote
import com.sarat.app.ui.components.PrimaryButton
import com.sarat.app.ui.theme.C
import kotlinx.coroutines.launch

class ApiKeyViewModel(private val store: ApiKeyStore, private val gemini: GeminiClient) : ViewModel() {
    var key by mutableStateOf(store.current)
    var checking by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun onKey(value: String) {
        key = value.trim()
        error = null
    }

    fun verifyAndSave(onSaved: () -> Unit) {
        if (key.isBlank() || checking) return
        checking = true
        error = null
        viewModelScope.launch {
            val problem = gemini.verifyKey(key)
            checking = false
            if (problem == null) {
                store.save(key)
                onSaved()
            } else {
                error = problem
            }
        }
    }
}

private const val AI_STUDIO_URL = "https://aistudio.google.com/apikey"

@Composable
fun ApiKeyScreen(vm: ApiKeyViewModel, onSaved: () -> Unit, onBack: (() -> Unit)?) {
    val uri = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current

    Column(
        Modifier
            .fillMaxSize()
            .background(C.Ground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(20.dp)
    ) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.White)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "رجوع", tint = C.Ink)
                }
            }
            Text("اربط Gemini مجاناً", style = MaterialTheme.typography.headlineMedium)
            Text(
                "التطبيق يستخدم ذكاء Gemini من Google لتحليل الوجبات. تحتاج مفتاح مجاني من حسابك على Google، وياخذ أقل من دقيقة.",
                style = MaterialTheme.typography.bodyMedium, color = C.Muted,
            )

            Step(1, "افتح موقع Google AI Studio وسجل دخول بحساب Gmail مالتك.")
            OutlinedButton(onClick = { uri.openUri(AI_STUDIO_URL) }, shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Outlined.OpenInNew, contentDescription = null, tint = C.Green, modifier = Modifier.size(18.dp))
                Text("  افتح AI Studio", color = C.Green, fontWeight = FontWeight.SemiBold)
            }
            Step(2, "اضغط «Create API key» (إنشاء مفتاح)، وإذا سألك اختر أي مشروع أو أنشئ مشروع جديد.")
            Step(3, "انسخ المفتاح (يبدأ عادةً بـ AIza) وارجع هنا والصقه.")

            OutlinedTextField(
                value = vm.key,
                onValueChange = vm::onKey,
                label = { Text("مفتاح Gemini") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                isError = vm.error != null,
                trailingIcon = {
                    IconButton(onClick = { clipboard.getText()?.let { vm.onKey(it.text) } }) {
                        Icon(Icons.Outlined.ContentPaste, contentDescription = "لصق المفتاح")
                    }
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            vm.error?.let { Text(it, color = C.Red, fontSize = 13.sp) }

            InfoNote(
                "الخطة المجانية ما تحتاج بطاقة دفع، ولها حد يومي يكفي للاستخدام الشخصي. " +
                    "بالخطة المجانية ممكن Google تستخدم الصور والنصوص المرسلة لتحسين خدماتها، فلا ترسل صور فيها معلومات شخصية."
            )
            Text("المفتاح ينحفظ على موبايلك فقط.", fontSize = 12.sp, color = C.Muted)
        }
        Box(Modifier.padding(top = 12.dp)) {
            PrimaryButton(
                if (vm.checking) "نتحقق من المفتاح…" else "تحقق واحفظ",
                { vm.verifyAndSave(onSaved) },
                enabled = vm.key.isNotBlank() && !vm.checking,
            )
        }
    }
}

@Composable
private fun Step(n: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(C.GreenSoft),
            contentAlignment = Alignment.Center,
        ) { Text("$n", color = C.GreenDark, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
        Text(text, fontSize = 15.sp, lineHeight = 24.sp, modifier = Modifier.weight(1f))
    }
}
