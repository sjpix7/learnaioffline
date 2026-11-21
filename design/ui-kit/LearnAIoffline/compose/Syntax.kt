package com.aiengineer.academy.ui.theme

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

@Immutable
data class ExtraColors(
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    val onDangerContainer: Color,
    val codeBackground: Color,
    val codeText: Color,
    val codeKeyword: Color,
    val codeString: Color,
    val codeComment: Color,
    val codeNumber: Color,
    val codeFunction: Color,
    val codeDecorator: Color,
)

internal val LightExtra = ExtraColors(
    success = Color(0xFF206142),
    successContainer = Color(0xFFC8EED9),
    onSuccessContainer = Color(0xFF113C2C),
    danger = Color(0xFF972D39),
    dangerContainer = Color(0xFFFFDFE1),
    onDangerContainer = Color(0xFF641825),
    codeBackground = Color(0xFFEDF1E9),
    codeText = Color(0xFF193324),
    codeKeyword = Color(0xFF633E86),
    codeString = Color(0xFF245A37),
    codeComment = Color(0xFF495B50),
    codeNumber = Color(0xFF87431B),
    codeFunction = Color(0xFF1D5576),
    codeDecorator = Color(0xFF75401C),
)

internal val DarkExtra = ExtraColors(
    success = Color(0xFF9CDBB7),
    successContainer = Color(0xFF234D39),
    onSuccessContainer = Color(0xFFD6F5E2),
    danger = Color(0xFFFFABB1),
    dangerContainer = Color(0xFF682831),
    onDangerContainer = Color(0xFFFFE2E4),
    codeBackground = Color(0xFF101B18),
    codeText = Color(0xFFE3EEE4),
    codeKeyword = Color(0xFFD6BAFF),
    codeString = Color(0xFF9CDDB6),
    codeComment = Color(0xFFAABBAE),
    codeNumber = Color(0xFFF6C485),
    codeFunction = Color(0xFF9FCFEF),
    codeDecorator = Color(0xFFF3CD96),
)

val LocalExtraColors = staticCompositionLocalOf { LightExtra }
