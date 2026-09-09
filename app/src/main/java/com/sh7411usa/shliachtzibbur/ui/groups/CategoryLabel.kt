package com.sh7411usa.shliachtzibbur.ui.groups

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sh7411usa.shliachtzibbur.R
import java.util.Locale

@StringRes
private fun categoryRes(category: String): Int? = when (category.lowercase(Locale.ROOT)) {
    "family" -> R.string.category_family
    "neighborhood" -> R.string.category_neighborhood
    "shul" -> R.string.category_shul
    "school" -> R.string.category_school
    "other" -> R.string.category_other
    else -> null
}

/** Localised category name, falling back to the raw server string, capitalised. */
@Composable
fun categoryLabel(category: String): String =
    categoryRes(category)?.let { stringResource(it) }
        ?: category.replaceFirstChar { it.uppercase(Locale.getDefault()) }
