package com.sarat.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarat.app.SaratApp
import com.sarat.app.ui.theme.C
import kotlinx.coroutines.delay
import kotlin.random.Random

// ───── صورة الوجبة المصغّرة ─────

/** صورة الوجبة إذا انحفظت، وإلا أيقونة الكاميرا أو الكتابة. */
@Composable
fun MealThumb(mealId: Long, isPhoto: Boolean, size: Dp = 44.dp) {
    val context = LocalContext.current
    val thumbs = (context.applicationContext as SaratApp).container.thumbs
    val image by produceState<ImageBitmap?>(initialValue = null, mealId) {
        value = if (isPhoto) thumbs.load(mealId)?.asImageBitmap() else null
    }
    val shape = RoundedCornerShape(12.dp)
    val img = image
    if (img != null) {
        Image(img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(shape))
    } else {
        Box(
            Modifier.size(size).clip(shape).background(if (isPhoto) C.GreenSoft else C.Beige),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (isPhoto) Icons.Outlined.PhotoCamera else Icons.Outlined.EditNote,
                contentDescription = null,
                tint = if (isPhoto) C.Green else C.OrangeText,
            )
        }
    }
}

// ───── عناصر الانتظار (Skeleton) ─────

@Composable
private fun pulse(): Float {
    val t = rememberInfiniteTransition(label = "skeleton")
    val a by t.animateFloat(
        initialValue = 0.45f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
        label = "alpha",
    )
    return a
}

@Composable
private fun Bone(width: Dp?, height: Dp, alpha: Float, radius: Dp = 8.dp) {
    val m = if (width == null) Modifier.fillMaxWidth() else Modifier.width(width)
    Box(m.height(height).alpha(alpha).clip(RoundedCornerShape(radius)).background(C.Skeleton))
}

/** شكل نتيجة الوجبة وهي تتحمّل، بدل دائرة الانتظار. */
@Composable
fun MealResultSkeleton(message: String) {
    val a = pulse()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(message, color = C.Muted, fontSize = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Bone(200.dp, 22.dp, a)
                Bone(150.dp, 12.dp, a)
            }
            Bone(64.dp, 40.dp, a)
        }
        Bone(null, 84.dp, a, 16.dp)
        repeat(3) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Card).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Bone(10.dp, 10.dp, a, 5.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Bone(120.dp, 14.dp, a)
                    Bone(70.dp, 10.dp, a)
                }
                Bone(36.dp, 14.dp, a)
            }
        }
    }
}

// ───── احتفال بسيط ─────

private data class Piece(val x: Float, val delay: Float, val speed: Float, val size: Float, val color: Color, val spin: Float)

/** قصاصات ملونة تنزل مع رسالة، وتختفي وحدها بعد ثواني أو باللمس. */
@Composable
fun Celebration(title: String, line: String, onDone: () -> Unit) {
    val pieces = remember {
        val colors = listOf(Color(0xFF2E9470), Color(0xFFFFB45C), Color(0xFFFFD27A), Color(0xFF7F9FE0), Color(0xFFE0675C))
        List(70) {
            Piece(
                x = Random.nextFloat(), delay = Random.nextFloat() * 0.35f, speed = 0.7f + Random.nextFloat() * 0.6f,
                size = 6f + Random.nextFloat() * 8f, color = colors.random(), spin = Random.nextFloat() * 720f - 360f,
            )
        }
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(3200, easing = LinearEasing))
        delay(300)
        onDone()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x66000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDone),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val p = progress.value
            pieces.forEach { pc ->
                val t = ((p - pc.delay) / (1f - pc.delay)).coerceIn(0f, 1f) * pc.speed
                if (t <= 0f) return@forEach
                val x = pc.x * size.width + kotlin.math.sin(t * 9f + pc.x * 6f) * 18f
                val y = -20f + t * (size.height + 40f)
                rotate(pc.spin * t, Offset(x, y)) {
                    drawRect(pc.color, topLeft = Offset(x - pc.size / 2, y - pc.size / 3), size = Size(pc.size, pc.size * 0.6f))
                }
            }
        }
        Column(
            Modifier
                .padding(32.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(C.Card)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = Color(0xFFFFB45C), modifier = Modifier.size(56.dp))
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = C.Ink, textAlign = TextAlign.Center)
            Text(line, fontSize = 15.sp, color = C.Muted, textAlign = TextAlign.Center, lineHeight = 24.sp)
        }
    }
}
