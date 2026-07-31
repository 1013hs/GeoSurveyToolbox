package com.geosurvey.toolbox.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geosurvey.toolbox.gnss.GnssManager
import com.geosurvey.toolbox.gnss.model.GnssFix
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GnssViewModel(application: Application) : AndroidViewModel(application) {

    private val gnssManager = GnssManager(application)

    private val _fix = MutableStateFlow<GnssFix?>(null)
    val fix: StateFlow<GnssFix?> = _fix.asStateFlow()

    private val _isStationary = MutableStateFlow(false)
    val isStationary: StateFlow<Boolean> = _isStationary.asStateFlow()

    private val _permissionGranted = MutableStateFlow(false)
    val permissionGranted: StateFlow<Boolean> = _permissionGranted.asStateFlow()

    fun onPermissionGranted() {
        _permissionGranted.value = true
        startObserving()
    }

    private fun startObserving() {
        viewModelScope.launch {
            gnssManager.observeFixes().collect { newFix ->
                _fix.value = newFix
                _isStationary.value = gnssManager.isStationary(newFix)
            }
        }
    }

    fun reset() {
        gnssManager.resetFilters()
    }
}
