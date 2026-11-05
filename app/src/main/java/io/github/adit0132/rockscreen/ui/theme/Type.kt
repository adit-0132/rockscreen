package io.github.adit0132.rockscreen.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import io.github.adit0132.rockscreen.R

// Fonts are bundled (SIL OFL 1.1, licenses in assets/licenses) as variable fonts, one instance per weight.
// Bundling replaces the Google Play services downloadable-fonts provider: no network fetch, works without GMS.

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: FontWeight, vararg extra: FontVariation.Setting) =
    Font(res, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight), *extra))

val SpaceGroteskFamily = FontFamily(
    variable(R.font.space_grotesk, FontWeight.Light),
    variable(R.font.space_grotesk, FontWeight.Normal),
    variable(R.font.space_grotesk, FontWeight.Medium),
    variable(R.font.space_grotesk, FontWeight.SemiBold),
    variable(R.font.space_grotesk, FontWeight.Bold),
)

@OptIn(ExperimentalTextApi::class)
private val dmSansOpticalSize = FontVariation.Setting("opsz", 14f)

val DmSansFamily = FontFamily(
    variable(R.font.dm_sans, FontWeight.Light, dmSansOpticalSize),
    variable(R.font.dm_sans, FontWeight.Normal, dmSansOpticalSize),
    variable(R.font.dm_sans, FontWeight.Medium, dmSansOpticalSize),
    variable(R.font.dm_sans, FontWeight.SemiBold, dmSansOpticalSize),
    variable(R.font.dm_sans, FontWeight.Bold, dmSansOpticalSize),
)

val PlayfairDisplayFamily = FontFamily(
    variable(R.font.playfair_display, FontWeight.Normal),
    variable(R.font.playfair_display, FontWeight.Medium),
    variable(R.font.playfair_display, FontWeight.Bold),
)
