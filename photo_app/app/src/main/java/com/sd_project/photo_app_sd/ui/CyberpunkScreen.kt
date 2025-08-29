package com.sd_project.photo_app_sd.ui

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import androidx.core.content.getSystemService
import com.sd_project.photo_app_sd.ui.theme.*

object CyberpunkColors {
    val PastelPurple = Color(0xFFB388FF)
    val PastelBlue = Color(0xFF82B1FF)
    val PastelGreen = Color(0xFF69F0AE)
    val NeonGradient = Brush.linearGradient(listOf(PastelPurple, PastelGreen))
    val BgGradient = Brush.verticalGradient(
        colors = listOf(PastelPurple, PastelBlue),
        startY = 0f, endY = 2000f
    )
    val ButtonBg = PastelPurple.copy(alpha = 0.85f)
    val ButtonPressed = PastelBlue.copy(alpha = 0.85f)
    val TextFieldBg = Color.White.copy(alpha = 0.15f)
    val Placeholder = Color.White.copy(alpha = 0.5f)
    val WhiteGlow = Color.White.copy(alpha = 0.7f)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CyberpunkScreen(
    onTakePhoto: () -> Unit = {},
    onSendPhoto: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Gradiente animado de fundo
    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    val gradientShift by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1000f, animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing), repeatMode = RepeatMode.Reverse
        ), label = "bgAnim"
    )
    val animatedBrush = Brush.verticalGradient(
        colors = listOf(
            CyberpunkColors.PastelPurple,
            CyberpunkColors.PastelBlue,
            CyberpunkColors.PastelPurple
        ),
        startY = gradientShift,
        endY = 2000f + gradientShift
    )

    var serverAddress by remember { mutableStateOf(TextFieldValue("")) }
    var isTakePressed by remember { mutableStateOf(false) }
    var isSendPressed by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Surface(
        modifier = modifier
            .fillMaxSize()
            .background(animatedBrush)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // TextField estilizado
            OutlinedTextField(
                value = serverAddress,
                onValueChange = { serverAddress = it },
                placeholder = {
                    Text(
                        "Servidor (host:porta)",
                        color = CyberpunkColors.Placeholder
                    )
                },
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 18.sp,
                    fontFamily = FontFamily.SansSerif
                ),
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    containerColor = CyberpunkColors.TextFieldBg,
                    focusedBorderColor = CyberpunkColors.PastelBlue,
                    unfocusedBorderColor = CyberpunkColors.PastelPurple,
                    cursorColor = CyberpunkColors.PastelGreen
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .blur(8.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Container da câmera (placeholder)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(28.dp))
                    .border(
                        width = 4.dp,
                        brush = CyberpunkColors.NeonGradient,
                        shape = RoundedCornerShape(28.dp)
                    )
                    .shadow(24.dp, RoundedCornerShape(28.dp), ambientColor = CyberpunkColors.PastelGreen)
                    .background(Color.Black.copy(alpha = 0.7f))
                    .drawBehind {
                        // Glow nas bordas
                        drawRoundRect(
                            brush = CyberpunkColors.NeonGradient,
                            style = Stroke(width = 10f),
                            cornerRadius = CornerRadius(28.dp.toPx()),
                            alpha = 0.3f
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Prévia da Câmera",
                    color = CyberpunkColors.Placeholder,
                    fontSize = 20.sp,
                    fontFamily = FontFamily.SansSerif
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Botões lado a lado
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                CyberpunkButton(
                    text = "Tirar Foto",
                    pressed = isTakePressed,
                    onClick = {
                        isTakePressed = true
                        vibrate(context)
                        onTakePhoto()
                        LaunchedEffect(Unit) {
                            kotlinx.coroutines.delay(150)
                            isTakePressed = false
                        }
                    }
                )
                CyberpunkButton(
                    text = "Enviar Foto",
                    pressed = isSendPressed,
                    onClick = {
                        isSendPressed = true
                        vibrate(context)
                        onSendPhoto()
                        LaunchedEffect(Unit) {
                            kotlinx.coroutines.delay(150)
                            isSendPressed = false
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun CyberpunkButton(
    text: String,
    pressed: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(if (pressed) 0.93f else 1f, animationSpec = spring(stiffness = Spring.StiffnessMedium), label = "btnScale")
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (pressed) CyberpunkColors.ButtonPressed else CyberpunkColors.ButtonBg,
            contentColor = Color.White
        ),
        elevation = ButtonDefaults.buttonElevation(10.dp, 16.dp),
        modifier = Modifier
            .weight(1f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = 24f
            }
            .border(
                width = 2.dp,
                brush = CyberpunkColors.NeonGradient,
                shape = RoundedCornerShape(20.dp)
            )
            .drawWithContent {
                drawContent()
                // Glow
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.12f),
                    cornerRadius = CornerRadius(20.dp.toPx()),
                    style = Stroke(width = 8f)
                )
            }
    ) {
        Text(
            text,
            style = TextStyle(
                fontSize = 18.sp,
                fontFamily = FontFamily.SansSerif,
                color = Color.White,
                shadow = Shadow(
                    color = CyberpunkColors.WhiteGlow,
                    blurRadius = 8f
                )
            )
        )
    }
}

private fun vibrate(context: android.content.Context) {
    val vibrator = context.getSystemService<Vibrator>()
    vibrator?.let {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            it.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            it.vibrate(40)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CyberpunkScreenPreview() {
    CyberpunkScreen()
}
