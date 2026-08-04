package com.nexaflow.feature.themes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexaflow.core.datastore.ThemeMode
import com.nexaflow.core.datastore.ThemePreferences
import com.nexaflow.core.datastore.ThemeSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val themePreferences: ThemePreferences
) : ViewModel() {

    val theme: StateFlow<ThemeSettings> = themePreferences.theme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeSettings())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { themePreferences.setThemeMode(mode) }
    }

    fun setAccent(accent: String) {
        viewModelScope.launch { themePreferences.setAccent(accent) }
    }
}
