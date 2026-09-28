package com.vnnami.appkit.internal.language

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign.Companion.Center
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vnnami.appkit.R
import com.vnnami.appkit.internal.language.Language

// PicMove palette mirrored locally because the wrapper cannot depend on the host app theme module.
private val LangBg = Color(0xFF0B0A14)
private val LangSurface = Color(0xFF181920)
private val LangSelectedSurface = Color(0xFF2A2B48)
private val LangAccent = Color(0xFF0AA6ED)
private val LangSelectedBorder = Color(0xFF5A65B6)
private val LangGradientStart = Color(0xFF8A79FF)
private val LangGradientEnd = Color(0xFF22B8FB)
private val LangOnAccent = Color(0xFFFFFFFF)
private val LangTextHi = Color(0xFFFFFFFF)
private val LangTextMut = Color(0xFF9B9EB0)
private val LangBorder = Color(0xFF3A3C4B)
private val LangFlagBg = Color(0xFF1C1930)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
/**
 * The wrapper's language picker: a selected row on top, then every language, with a search box.
 * Applying writes through the ViewModel; the caller decides what to do next — see
 * [com.vnnami.appkit.api.LanguageRoute].
 */
internal fun LanguageScreen(
    navController: NavController,
    onNavigateBack: () -> Unit,
    onLanguageApplied: () -> Unit,
    viewModel: LanguageViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val isChanged = uiState.selectedLanguage?.id != uiState.appliedLanguage?.id

    LanguageScreenContent(
        uiState = uiState,
        isChanged = isChanged,
        onSearchQueryChanged = { viewModel.onSearchQueryChanged(it) },
        onLanguageSelected = { viewModel.onLanguageSelected(it) },
        onApply = {
            keyboardController?.hide()
            focusManager.clearFocus()
            viewModel.applyLanguage(onSuccess = onLanguageApplied)
        },
        onNavigateBack = onNavigateBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
/** The screen as a pure function of [uiState], so it renders in a preview without Hilt. */
internal fun LanguageScreenContent(
    uiState: LanguageUiState,
    isChanged: Boolean,
    onSearchQueryChanged: (String) -> Unit,
    onLanguageSelected: (Language) -> Unit,
    onApply: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.language_title),
                        fontFamily = LangFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = LangTextHi,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_content_description),
                            tint               = LangTextHi
                        )
                    }
                },
                actions = {
                    val interactionSource = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .height(40.dp)
                            .wrapContentWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .then(
                                if (isChanged) Modifier.background(
                                    Brush.horizontalGradient(
                                        listOf(LangGradientStart, LangGradientEnd)
                                    )
                                ) else Modifier.background(LangSelectedSurface)
                            )
                            .clickable(
                                interactionSource = interactionSource,
                                indication = ripple(
                                    color = Color.White
                                ),
                                enabled = isChanged,
                                onClick = onApply
                            )
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.apply),
                            color = if (isChanged) LangOnAccent else LangTextMut,
                            fontFamily = LangFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = LangBg
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Search Bar
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchQueryChanged,
                    placeholder = {
                        Text(
                            stringResource(R.string.search_language_placeholder),
                            fontFamily = LangFontFamily,
                            fontSize = 13.sp,
                            color = LangTextMut,
                            textAlign = Center
                        )
                    },
                    textStyle = TextStyle(
                        fontFamily = LangFontFamily,
                        fontSize = 14.sp,
                        color = LangTextHi,
                        platformStyle = PlatformTextStyle(
                            includeFontPadding = false
                        )
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 12.dp)
                        .heightIn(min = 56.dp),
                    shape = RoundedCornerShape(10.dp),
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.search_content_description),
                            tint = LangTextMut,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = LangSurface,
                        unfocusedContainerColor = LangSurface,
                        focusedBorderColor = LangAccent.copy(alpha = 0.8f),
                        unfocusedBorderColor = LangAccent.copy(alpha = 0.5f),
                        cursorColor = LangAccent,
                        focusedTextColor = LangTextHi,
                        unfocusedTextColor = LangTextHi
                    ),
                    singleLine = true
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(15.dp),
                    verticalArrangement = Arrangement.spacedBy(15.dp)
                ) {


                    // Section: Selected
                    item(span = { GridItemSpan(2) }) {
                        Text(
                            stringResource(R.string.selected_section_title),
                            fontFamily = LangFontFamily,
                            fontWeight = FontWeight(500),
                            color = LangTextMut,
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    uiState.appliedLanguage?.let { applied ->
                        item(span = { GridItemSpan(2) }) {
                            SelectedLanguageCard(
                                language = applied,
                                isSelected = uiState.isShowingInitialFocus || uiState.selectedLanguage?.id == applied.id,
                                onClick = { onLanguageSelected(applied) }
                            )
                        }
                    }

                    // Section: All language
                    item(span = { GridItemSpan(2) }) {
                        Text(
                            stringResource(R.string.all_languages_section_title),
                            fontFamily = LangFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = LangTextMut,
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                        )
                    }

                    items(uiState.filteredLanguages) { language ->
                        LanguageCard(
                            language = language,
                            isSelected = uiState.selectedLanguage?.id == language.id,
                            onClick = { onLanguageSelected(language) }
                        )
                    }

                    // Extra space at the bottom removed for cleaner look
                }
            }
        }

        // Full screen loading
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x4D000000))
                    .clickable(enabled = false) { },
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = LangAccent
                )
            }
        }
    }
}

@Composable
/** The highlighted row for the language currently in force. */
internal fun SelectedLanguageCard(
    language: Language,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val contentColor = LangTextHi
    val subTextColor = LangTextMut
    val cardBg = LangSurface

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .then(
                if (isSelected) Modifier.border(
                    2.dp,
                    LangSelectedBorder,
                    RoundedCornerShape(16.dp)
                ) else Modifier.border(
                    1.5.dp,
                    LangBorder,
                    RoundedCornerShape(16.dp)
                )
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isSelected) Modifier.background(LangSelectedSurface)
                    else Modifier
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FlagCircle(iconResName = language.flagIconName, size = 48.dp)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = language.localName.ifBlank { language.name },
                        color = contentColor,
                        fontFamily = LangFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        lineHeight = 27.sp
                    )
                    Text(
                        text = language.name,
                        color = subTextColor,
                        fontFamily = LangFontFamily,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(LangAccent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = LangOnAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
/** One selectable row: flag, English name, native name. */
internal fun LanguageCard(
    language: Language,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val contentColor = LangTextHi
    val cardBg = LangSurface

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(114.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .then(
                if (isSelected) Modifier.border(2.dp, LangSelectedBorder, RoundedCornerShape(16.dp))
                else Modifier.border(1.5.dp, LangBorder, RoundedCornerShape(16.dp))
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isSelected) Modifier.background(LangSelectedSurface)
                    else Modifier
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                FlagCircle(iconResName = language.flagIconName, size = 56.dp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = language.localName.ifBlank { language.name },
                    color = contentColor,
                    fontFamily = LangFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    textAlign = Center
                )
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(LangAccent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = LangOnAccent,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
/** Circular flag, resolved from [Language.flagIconName] at runtime. */
internal fun FlagCircle(iconResName: String, size: Dp) {
    val context = LocalContext.current
    val isPreview = androidx.compose.ui.platform.LocalInspectionMode.current
    val resId = remember(iconResName, isPreview) {
        if (isPreview) {
            0
        } else {
            try {
                context.resources.getIdentifier(iconResName, "drawable", context.packageName)
            } catch (e: Exception) {
                0
            }
        }
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(LangFlagBg),
        contentAlignment = Alignment.Center
    ) {
        if (resId != 0) {
            Icon(
                painter = painterResource(id = resId),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = iconResName.takeLast(2).uppercase(),
                fontFamily = LangFontFamily,
                fontSize = (size.value * 0.4f).sp,
                fontWeight = FontWeight.Bold,
                color = LangTextMut
            )
        }
    }
}

@Preview(showBackground = true, name = "Language Screen – empty")
@Composable
private fun LanguageScreenPreview() {
    val fakeState = LanguageUiState(
        appliedLanguage = Language(
            id = "en_GB",
            name = "English UK",
            localName = "English UK",
            countryCode = "GB",
            flagIconName = "ic_gb"
        ),
        selectedLanguage = Language(
            id = "en_GB",
            name = "English UK",
            localName = "English UK",
            countryCode = "GB",
            flagIconName = "ic_gb"
        ),
        filteredLanguages = listOf(
            Language("vi_VN", stringResource(R.string.ting_vit), stringResource(R.string.ting_vit), "VN", "ic_vn"),
            Language("fr_FR", "Français", "Français", "FR", "ic_fr"),
            Language("de_DE", "Deutsch", "Deutsch", "DE", "ic_de"),
            Language("pt_BR", stringResource(R.string.portugus), stringResource(R.string.portugus), "BR", "ic_br"),
            Language("it_IT", "Italiano", "Italiano", "IT", "ic_it"),
            Language("es_ES", "Español", "Español", "ES", "ic_es"),
            Language("ja_JP", "日本語", "日本語", "JP", "ic_jp"),
            Language("ko_KR", "한국어", "한국어", "KR", "ic_kr")
        ),
        isShowingInitialFocus = true,
        isLoading = false
    )
    LanguageScreenContent(
        uiState = fakeState,
        onSearchQueryChanged = {},
        onLanguageSelected = {},
        onApply = {},
        onNavigateBack = {},
        isChanged = false,
    )
}

@Preview(showBackground = true, name = "Language Screen – Selectable")
@Composable
private fun LanguageScreenSelectablePreview() {
    val fakeState = LanguageUiState(
        appliedLanguage = Language(
            id = "en_GB",
            name = "English UK",
            localName = "English UK",
            countryCode = "GB",
            flagIconName = "ic_gb"
        ),
        selectedLanguage = Language(
            id = "vi_VN",
            name = stringResource(R.string.ting_vit),
            localName = stringResource(R.string.ting_vit),
            countryCode = "VN",
            flagIconName = "ic_vn"
        ),
        filteredLanguages = listOf(
            Language("vi_VN", stringResource(R.string.ting_vit), stringResource(R.string.ting_vit), "VN", "ic_vn"),
            Language("fr_FR", "Français", "Français", "FR", "ic_fr"),
            Language("de_DE", "Deutsch", "Deutsch", "DE", "ic_de"),
            Language("pt_BR", stringResource(R.string.portugus), stringResource(R.string.portugus), "BR", "ic_br"),
            Language("it_IT", "Italiano", "Italiano", "IT", "ic_it"),
            Language("es_ES", "Español", "Español", "ES", "ic_es"),
            Language("ja_JP", "日本語", "日本語", "JP", "ic_jp"),
            Language("ko_KR", "한국어", "한국어", "KR", "ic_kr")
        ),
        isShowingInitialFocus = false,
        isLoading = false
    )
    LanguageScreenContent(
        uiState = fakeState,
        onSearchQueryChanged = {},
        onLanguageSelected = {},
        onApply = {},
        onNavigateBack = {},
        isChanged = true,
    )
}

/**
 * "Poppins" font family for the wrapper's Language screen — the SAME typeface the Android Phone
 * Ringtone app uses everywhere (app/.../ui/theme/PoppinsFonts.kt). The wrapper is an upstream module
 * so it can't import the app's Poppins family; the ttf files are mirrored into this module's res/font/
 * and re-declared here so the Language screen matches the app theme 1:1.
 */
internal val LangFontFamily = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semi_bold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
)
