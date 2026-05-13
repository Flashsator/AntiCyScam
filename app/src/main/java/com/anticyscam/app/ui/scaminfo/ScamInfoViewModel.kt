package com.anticyscam.app.ui.scaminfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anticyscam.app.data.repository.ScamInfoRepository
import com.anticyscam.app.domain.model.EmergencyChannel
import com.anticyscam.app.domain.model.ScamCategory
import com.anticyscam.app.domain.model.ScamSeverity
import com.anticyscam.app.domain.model.ScamTactic
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScamInfoViewModel @Inject constructor(
    private val repository: ScamInfoRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ScamInfoState())
    val state: StateFlow<ScamInfoState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { repository.load() }
                .onSuccess { catalog ->
                    _state.update {
                        it.copy(
                            loading = false,
                            version = catalog.version,
                            lastUpdated = catalog.lastUpdated,
                            source = catalog.source,
                            notice = catalog.notice,
                            channels = catalog.channels,
                            categories = catalog.categories,
                            allTactics = catalog.tactics.sortedBy { tactic -> tactic.severity.ordinal }
                        )
                    }
                }
                .onFailure { err ->
                    _state.update {
                        it.copy(loading = false, errorMessage = err.message ?: "讀取詐騙資料庫失敗")
                    }
                }
        }
    }

    fun onCategorySelected(categoryId: String?) {
        _state.update { it.copy(selectedCategoryId = categoryId) }
    }

    fun onSearchChanged(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun onTacticExpanded(tacticId: String) {
        _state.update {
            val newSet = if (tacticId in it.expandedTacticIds) {
                it.expandedTacticIds - tacticId
            } else {
                it.expandedTacticIds + tacticId
            }
            it.copy(expandedTacticIds = newSet)
        }
    }
}

data class ScamInfoState(
    val loading: Boolean = true,
    val errorMessage: String? = null,
    val version: Int = 0,
    val lastUpdated: String = "",
    val source: String = "",
    val notice: String = "",
    val channels: List<EmergencyChannel> = emptyList(),
    val categories: List<ScamCategory> = emptyList(),
    val allTactics: List<ScamTactic> = emptyList(),
    val selectedCategoryId: String? = null,
    val searchQuery: String = "",
    val expandedTacticIds: Set<String> = emptySet()
) {
    val visibleTactics: List<ScamTactic>
        get() {
            val byCategory = selectedCategoryId
                ?.let { id -> allTactics.filter { it.categoryId == id } }
                ?: allTactics
            val query = searchQuery.trim()
            if (query.isEmpty()) return byCategory
            return byCategory.filter { tactic ->
                tactic.title.contains(query, ignoreCase = true) ||
                    tactic.description.contains(query, ignoreCase = true) ||
                    tactic.tags.any { tag -> tag.contains(query, ignoreCase = true) }
            }
        }

    fun severityCount(severity: ScamSeverity): Int =
        allTactics.count { it.severity == severity }
}
