package com.gree1d.reappzuku.ui.main

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gree1d.reappzuku.AppConstants
import com.gree1d.reappzuku.AppModel
import com.gree1d.reappzuku.AutoKillManager
import com.gree1d.reappzuku.BackgroundAppManager
import com.gree1d.reappzuku.CpuMonitor
import com.gree1d.reappzuku.PreferenceKeys
import com.gree1d.reappzuku.RamMonitor
import com.gree1d.reappzuku.ShellManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.Executors


data class MainUiState(
    // app list
    val fullApps: List<AppModel>     = emptyList(),
    val filteredApps: List<AppModel> = emptyList(),
    val isRefreshing: Boolean        = false,
    // search
    val searchQuery: String          = "",
    val isSearchActive: Boolean      = false,
    // sort / filters
    val sortMode: Int                = AppConstants.SORT_MODE_DEFAULT,
    val showSystemApps: Boolean      = false,
    val showPersistentApps: Boolean  = false,
    // RAM
    val ramPercent: Float            = 0f,
    val ramLabel: String             = "",
    // selection
    val selectedCount: Int           = 0,
)


class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val handler  = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val prefs    = application.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)

    val shellManager: ShellManager   = ShellManager(application, handler, executor)
    val appManager: BackgroundAppManager =
        BackgroundAppManager(application, handler, executor, shellManager)
    val autoKillManager: AutoKillManager =
        AutoKillManager(application, handler, executor, shellManager, mutableListOf())

    private val cpuMonitor = CpuMonitor(handler, executor, shellManager)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    fun onRamUpdate(percent: Float, label: String) {
        _uiState.update { it.copy(ramPercent = percent, ramLabel = label) }
    }


    init {
        loadSettingsFromPrefs()
        cpuMonitor.setOnCpuUpdateListener {
            val current = _uiState.value
            if (current.sortMode == AppConstants.SORT_MODE_CPU_DESC ||
                current.sortMode == AppConstants.SORT_MODE_CPU_ASC
            ) {
                val sorted = current.filteredApps.toMutableList().also {
                    appManager.sortAppList(it, current.sortMode)
                }
                _uiState.update { s -> s.copy(filteredApps = sorted) }
            }
        }
    }


    fun loadBackgroundApps() {
        _uiState.update { it.copy(isRefreshing = true) }

        val selectedPkgs = _uiState.value.fullApps
            .filter { it.isSelected }
            .map { it.packageName }
            .toSet()

        appManager.loadBackgroundApps { result ->
            for (app in result) {
                if (app.packageName in selectedPkgs && !app.isProtected) {
                    app.isSelected = true
                }
            }
            val filtered = filter(result, _uiState.value.searchQuery, _uiState.value.sortMode)
            _uiState.update { s ->
                s.copy(
                    fullApps     = result,
                    filteredApps = filtered,
                    isRefreshing = false,
                    selectedCount = result.count { it.isSelected },
                )
            }
            cpuMonitor.refreshAppsList(result)
        }
    }


    fun onSearchQueryChange(query: String) {
        val filtered = filter(_uiState.value.fullApps, query, _uiState.value.sortMode)
        _uiState.update { it.copy(searchQuery = query, filteredApps = filtered) }
    }

    fun onSearchActiveChange(active: Boolean) {
        _uiState.update { it.copy(isSearchActive = active) }
        if (!active) onSearchQueryChange("")
    }


    fun applySortAndFilters(
        sortMode: Int,
        showSystem: Boolean,
        showPersistent: Boolean,
    ) {
        prefs.edit()
            .putInt(PreferenceKeys.KEY_SORT_MODE, sortMode)
            .putBoolean(PreferenceKeys.KEY_SHOW_SYSTEM_APPS, showSystem)
            .putBoolean(PreferenceKeys.KEY_SHOW_PERSISTENT_APPS, showPersistent)
            .apply()

        appManager.setShowSystemApps(showSystem)
        appManager.setShowPersistentApps(showPersistent)

        _uiState.update { it.copy(sortMode = sortMode, showSystemApps = showSystem, showPersistentApps = showPersistent) }
        loadBackgroundApps()
    }


    fun onAppClick(app: AppModel) {
        if (app.isProtected || app.isWhitelisted) return
        app.isSelected = !app.isSelected
        refreshSelectedCount()
    }

    fun selectAll() {
        _uiState.value.fullApps.forEach { app ->
            if (!app.isProtected && !app.isWhitelisted) app.isSelected = true
        }
        refreshSelectedCount()
    }

    fun deselectAll() {
        _uiState.value.fullApps.forEach { it.isSelected = false }
        refreshSelectedCount()
    }

    private fun refreshSelectedCount() {
        val count = _uiState.value.fullApps.count { it.isSelected }
        _uiState.update { s ->
            s.copy(
                selectedCount = count,
                filteredApps  = s.filteredApps.toList(),
            )
        }
    }


    fun killSelected() {
        val pkgs = _uiState.value.fullApps
            .filter { it.isSelected }
            .map { it.packageName }
        deselectAll()
        autoKillManager.killPackages(pkgs) { loadBackgroundApps() }
    }

    fun killApp(app: AppModel) {
        autoKillManager.killApp(app.packageName) { loadBackgroundApps() }
    }


    fun toggleListMembership(app: AppModel, listType: String) {
        val packageName = app.packageName
        when (listType) {
            "whitelist" -> {
                val set = appManager.getWhitelistedApps()
                val wasIn = set.contains(packageName)
                if (wasIn) set.remove(packageName) else set.add(packageName)
                appManager.saveWhitelistedApps(set)
                app.isWhitelisted = !wasIn
            }
            "blacklist" -> {
                val set = autoKillManager.getBlacklistedApps()
                if (set.contains(packageName)) set.remove(packageName) else set.add(packageName)
                autoKillManager.saveBlacklistedApps(set)
            }
            "hidden" -> {
                val set = appManager.getHiddenApps()
                if (set.contains(packageName)) set.remove(packageName) else set.add(packageName)
                appManager.saveHiddenApps(set)
            }
        }
        _uiState.update { s -> s.copy(filteredApps = s.filteredApps.toList()) }
    }


    fun toggleWhitelist(app: AppModel): Boolean {
        val isNow = autoKillManager.toggleWhitelist(app.packageName)
        app.isWhitelisted = isNow
        _uiState.update { s -> s.copy(filteredApps = s.filteredApps.toList()) }
        return isNow
    }


    fun startCpuMonitor() = cpuMonitor.startMonitoring()
    fun stopCpuMonitor()  = cpuMonitor.stopMonitoring()


    private fun filter(
        source: List<AppModel>,
        query: String,
        sortMode: Int,
    ): List<AppModel> {
        val q = query.lowercase()
        val result = if (q.isEmpty()) source.toMutableList()
        else source.filterTo(mutableListOf()) {
            it.appName?.lowercase()?.contains(q) == true ||
                it.packageName.lowercase().contains(q)
        }
        appManager.sortAppList(result, sortMode)
        return result
    }

    private fun loadSettingsFromPrefs() {
        val sortMode       = prefs.getInt(PreferenceKeys.KEY_SORT_MODE, AppConstants.SORT_MODE_DEFAULT)
        val showSystem     = prefs.getBoolean(PreferenceKeys.KEY_SHOW_SYSTEM_APPS, false)
        val showPersistent = prefs.getBoolean(PreferenceKeys.KEY_SHOW_PERSISTENT_APPS, false)
        appManager.setShowSystemApps(showSystem)
        appManager.setShowPersistentApps(showPersistent)
        _uiState.update { it.copy(sortMode = sortMode, showSystemApps = showSystem, showPersistentApps = showPersistent) }
    }

    override fun onCleared() {
        super.onCleared()
        executor.shutdown()
    }
}
