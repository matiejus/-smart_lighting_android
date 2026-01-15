package com.example.myapplication.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.datasource.LampRepository
import com.example.myapplication.models.Lamp
import com.example.myapplication.models.Reading
import com.example.myapplication.models.Schedule
import com.example.myapplication.models.UsageStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LampDetailViewModel : ViewModel() {
    private val repository = LampRepository.getInstance()

    private val _lamp = MutableStateFlow<Lamp?>(null)
    val lamp: StateFlow<Lamp?> = _lamp.asStateFlow()

    private val _readings = MutableStateFlow<List<Reading>>(emptyList())
    val readings: StateFlow<List<Reading>> = _readings.asStateFlow()

    private val _schedules = MutableStateFlow<List<Schedule>>(emptyList())
    val schedules: StateFlow<List<Schedule>> = _schedules.asStateFlow()

    private val _usageStats = MutableStateFlow<UsageStats?>(null)
    val usageStats: StateFlow<UsageStats?> = _usageStats.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun loadLamp(espId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val loadedLamp = repository.getLamp(espId)
                _lamp.value = loadedLamp
                
                if (loadedLamp != null) {
                    loadReadings(espId)
                    loadSchedules(espId)
                    loadUsageStats(espId)
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load lamp"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun loadReadings(espId: String) {
        try {
            val loadedReadings = repository.getReadings(espId)
            _readings.value = loadedReadings
        } catch (e: Exception) {
            _error.value = e.message ?: "Failed to load readings"
        }
    }

    private suspend fun loadSchedules(espId: String) {
        try {
            val loadedSchedules = repository.getSchedules(espId)
            _schedules.value = loadedSchedules
        } catch (e: Exception) {
            _error.value = e.message ?: "Failed to load schedules"
        }
    }

    private suspend fun loadUsageStats(espId: String) {
        try {
            val stats = repository.getUsageStats(espId)
            _usageStats.value = stats
        } catch (e: Exception) {
            _error.value = e.message ?: "Failed to load usage stats"
        }
    }

    fun toggleLamp(lamp: Lamp) {
        viewModelScope.launch {
            try {
                val success = repository.toggleLamp(lamp.espId)
                if (success) {
                    val updated = lamp.copy(isOn = !lamp.isOn)
                    _lamp.value = updated
                } else {
                    _error.value = "Failed to toggle lamp"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Error toggling lamp"
            }
        }
    }

    fun setPower(espId: String, watts: Float) {
        viewModelScope.launch {
            try {
                val success = repository.setPower(espId, watts)
                if (!success) {
                    _error.value = "Failed to set power"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Error setting power"
            }
        }
    }

    fun createSchedule(espId: String, timeHm: String, action: String, days: String = "daily") {
        viewModelScope.launch {
            try {
                val (success, errorMsg) = repository.createSchedule(espId, timeHm, action, days)
                if (success) {
                    loadSchedules(espId)
                } else {
                    _error.value = errorMsg ?: "Failed to create schedule"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Error creating schedule"
            }
        }
    }

    fun updateSchedule(espId: String, scheduleId: Int, timeHm: String, action: String, days: String = "daily") {
        viewModelScope.launch {
            try {
                val (success, errorMsg) = repository.updateSchedule(scheduleId, timeHm, action, days)
                if (success) {
                    loadSchedules(espId)
                } else {
                    _error.value = errorMsg ?: "Failed to update schedule"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Error updating schedule"
            }
        }
    }

    fun filterSchedules(
        espId: String,
        action: String?,
        day: Int?,
        timeFrom: String?,
        timeTo: String?
    ) {
        viewModelScope.launch {
            try {
                val filtered = repository.filterSchedules(espId, action, day, timeFrom, timeTo)
                _schedules.value = filtered
            } catch (e: Exception) {
                _error.value = e.message ?: "Error filtering schedules"
            }
        }
    }

    fun reloadSchedules(espId: String) {
        viewModelScope.launch {
            loadSchedules(espId)
        }
    }

    fun clearError() {
        _error.value = null
    }
}
