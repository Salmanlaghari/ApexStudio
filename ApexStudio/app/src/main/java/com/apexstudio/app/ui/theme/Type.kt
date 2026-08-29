package com.apexstudio.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val sans = FontFamily.SansSerif

val ApexTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.Black,
        fontSize = 40.sp,
        letterSpacing = (-0.5).sp
    ),
    displayMedium = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        letterSpacing = (-0.4).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        letterSpacing = (-0.3).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp
    ),
    titleLarge = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp
    ),
    titleMedium = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp
    ),
    titleSmall = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp
    ),
    bodySmall = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp
    ),
    labelLarge = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        letterSpacing = 0.3.sp
    ),
    labelMedium = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 0.4.sp
    ),
    labelSmall = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        letterSpacing = 0.5.sp
    )
)
