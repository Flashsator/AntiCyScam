package com.anticyscam.app.ui.bind

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anticyscam.app.data.repository.BoundAppRepository
import com.anticyscam.app.data.system.InstalledAppsProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BindAppsViewModel @Inject constructor(
    private val installedApps: InstalledAppsProvider,
    private val repository: BoundAppRepository
) : ViewModel() {

    private val _state = MutableStateFlow(BindAppsUiState())
    val state: StateFlow<BindAppsUiState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val installed = installedApps.listLaunchableApps()
            val bound = repository.allPackageNamesSnapshot()
            _state.value = BindAppsUiState(
                isLoading = false,
                apps = installed.map {
                    BindableApp(
                        packageName = it.packageName,
                        label = it.label,
                        isBound = it.packageName in bound
                    )
                },
                selected = bound
            )
        }
    }

    fun toggle(packageName: String) {
        val current = _state.value
        val next = if (packageName in current.selected) {
            current.selected - packageName
        } else {
            current.selected + packageName
        }
        _state.value = current.copy(
            selected = next,
            apps = current.apps.map { app ->
                if (app.packageName == packageName) app.copy(isBound = packageName in next)
                else app
            }
        )
    }

    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            val current = _state.value
            val toBind = current.apps
                .filter { it.packageName in current.selected }
                .map { com.anticyscam.app.domain.model.BoundApp(it.packageName, it.label) }
            repository.replaceAll(toBind)
            onDone()
        }
    }
}

data class BindAppsUiState(
    val isLoading: Boolean = true,
    val apps: List<BindableApp> = emptyList(),
    val selected: Set<String> = emptySet()
)

data class BindableApp(
    val packageName: String,
    val label: String,
    val isBound: Boolean
)
