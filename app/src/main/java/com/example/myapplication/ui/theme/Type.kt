package com.example.myapplication.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.myapplication.R

// Load the DTM-Mono font from res/font/dtm_mono.otf (add the file at app/src/main/res/font/dtm_mono.otf)
// Map the same OTF file to multiple weights so Compose will use the DTM font for Normal/Medium/Bold
private val DtmMono = FontFamily(
    Font(R.font.dtm_mono, weight = FontWeight.Normal),
    Font(R.font.dtm_mono, weight = FontWeight.Medium),
    Font(R.font.dtm_mono, weight = FontWeight.Bold)
)

// Set of Material typography styles to use the DTM-Mono font for all style slots
val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = DtmMono,
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp
    ),
    displayMedium = TextStyle(
        fontFamily = DtmMono,
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp
    ),
    displaySmall = TextStyle(
        fontFamily = DtmMono,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = DtmMono,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = DtmMono,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = DtmMono,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp
    ),
    titleLarge = TextStyle(
        fontFamily = DtmMono,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp
    ),
    titleMedium = TextStyle(
        fontFamily = DtmMono,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp
    ),
    titleSmall = TextStyle(
        fontFamily = DtmMono,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = DtmMono,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = DtmMono,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    ),
    bodySmall = TextStyle(
        fontFamily = DtmMono,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp
    ),
    labelLarge = TextStyle(
        fontFamily = DtmMono,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    ),
    labelMedium = TextStyle(
        fontFamily = DtmMono,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp
    ),
    labelSmall = TextStyle(
        fontFamily = DtmMono,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp
    )
)

// If you want every other TextStyle customized, add them here with fontFamily = DtmMono
// Note: ensure the font file is placed at app/src/main/res/font/dtm_mono.otf and its resource name is R.font.dtm_mono
