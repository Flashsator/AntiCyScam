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
        // Phase L2: any explicit chip tap locks the user's category preference
        // so the auto-jump (see visibleTactics) won't override it for the
        // remainder of this search session.
        _state.update { it.copy(selectedCategoryId = categoryId, manualCategoryChange = true) }
    }

    fun onSearchChanged(query: String) {
        // Phase L2: when the user starts a fresh search (blank → non-blank),
        // reset manualCategoryChange so the new query can auto-jump across
        // categories. Mid-search edits and clears leave the flag alone.
        _state.update {
            val startingNewSearch = it.searchQuery.isBlank() && query.isNotBlank()
            it.copy(
                searchQuery = query,
                manualCategoryChange = if (startingNewSearch) false else it.manualCategoryChange
            )
        }
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
    val expandedTacticIds: Set<String> = emptySet(),
    val manualCategoryChange: Boolean = false
) {
    val visibleTactics: List<ScamTactic>
        get() {
            val query = searchQuery.trim()
            val inCategory: (ScamTactic) -> Boolean = { tactic ->
                selectedCategoryId == null || tactic.categoryId == selectedCategoryId
            }
            if (query.isEmpty()) return allTactics.filter(inCategory)
            val matchesQuery: (ScamTactic) -> Boolean = { tactic ->
                tactic.title.contains(query, ignoreCase = true) ||
                    tactic.description.contains(query, ignoreCase = true) ||
                    tactic.tags.any { tag -> tag.contains(query, ignoreCase = true) }
            }
            val filtered = allTactics.filter { inCategory(it) && matchesQuery(it) }
            // Phase L2: if the active category yields nothing for this search
            // but the query *does* match elsewhere, fall through to a global
            // match — unless the user explicitly chose this category since the
            // search began (manualCategoryChange). Goal: surface a result so
            // the user sees the search is working, not an empty page.
            if (filtered.isEmpty() && !manualCategoryChange && selectedCategoryId != null) {
                return allTactics.filter(matchesQuery)
            }
            return filtered
        }

    fun severityCount(severity: ScamSeverity): Int =
        allTactics.count { it.severity == severity }
}
