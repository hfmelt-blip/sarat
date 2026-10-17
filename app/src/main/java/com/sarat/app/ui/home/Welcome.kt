package com.sarat.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarat.app.ui.theme.C
import kotlinx.coroutines.delay

private val welcomeBrush = Brush.linearGradient(listOf(Color(0xFF164F3C), Color(0xFF2A8A68)))

/** بطاقة الترحيب والتحفيز أعلى الشاشة الرئيسية. */
@Composable
fun WelcomeCard(greeting: String, line: String, streak: Int, minuteOfDay: Int) {
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = visible,
        enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { it / 4 },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(welcomeBrush)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val hour = minuteOfDay / 60
                Icon(
                    if (hour in 5..17) Icons.Outlined.WbSunny else Icons.Outlined.DarkMode,
                    contentDescription = null, tint = Color(0xFFFFD27A), modifier = Modifier.size(22.dp),
                )
                Text(greeting, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Text(line, color = Color(0xFFE3F2EA), fontSize = 15.sp, lineHeight = 24.sp)
            if (streak >= 2) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x33FFFFFF))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icons.Outlined.LocalFireDepartment, contentDescription = null, tint = Color(0xFFFFB45C), modifier = Modifier.size(16.dp))
                    Text("$streak أيام التزام متتالية", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** شاشة ترحيب قصيرة تطلع عند فتح التطبيق، وتختفي وحدها أو باللمس. */
@Composable
fun WelcomeOverlay(title: String, line: String, onDone: () -> Unit) {
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    LaunchedEffect(Unit) {
        delay(3200)
        onDone()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(welcomeBrush)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDone),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visibleState = visible,
            enter = fadeIn(tween(600)) + scaleIn(tween(600), initialScale = 0.85f),
            exit = fadeOut(),
        ) {
            Column(
                Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    Modifier.size(88.dp).clip(CircleShape).background(Color(0x26FFFFFF)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.LocalFireDepartment, contentDescription = null, tint = Color(0xFFFFB45C), modifier = Modifier.size(48.dp))
                }
                Text(title, color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(line, color = Color(0xFFE3F2EA), fontSize = 19.sp, lineHeight = 30.sp, textAlign = TextAlign.Center)
                Text("اضغط للمتابعة", color = Color(0x99FFFFFF), fontSize = 13.sp, modifier = Modifier.padding(top = 24.dp))
            }
        }
    }
}

/** تذكير للمستخدمين القدامى حتى يضيفون اسمهم. */
@Composable
fun AddNameBanner(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(C.Beige)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("اكتب اسمك من «الملف» حتى نرحب بيك باسمك كل يوم", color = C.BeigeText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
