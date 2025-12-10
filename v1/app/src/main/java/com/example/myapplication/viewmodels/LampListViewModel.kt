package com.example.myapplication.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.datasource.LampRepository
import com.example.myapplication.models.Lamp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LampListViewModel : ViewModel() {
    private val repository = LampRepository.getInstance()

    private val _lamps = MutableStateFlow<List<Lamp>>(emptyList())
    val lamps: StateFlow<List<Lamp>> = _lamps.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadLamps()
        android.util.Log.d("LampListViewModel", "ViewModel initialized, loading lamps")
    }

    fun loadLamps() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                android.util.Log.d("LampListViewModel", "Fetching lamps from server")
                val loadedLamps = repository.getLamps()
                android.util.Log.d("LampListViewModel", "Loaded ${loadedLamps.size} lamps")
                _lamps.value = loadedLamps
                if (loadedLamps.isEmpty()) {
                    _error.value = "No devices found on server"
                }
            } catch (e: Exception) {
                android.util.Log.e("LampListViewModel", "Error loading lamps", e)
                _error.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggleLamp(lamp: Lamp) {
        viewModelScope.launch {
            try {
                val success = repository.toggleLamp(lamp.espId)
                if (success) {
                    val updated = lamp.copy(isOn = !lamp.isOn)
                    _lamps.value = _lamps.value.map {
                        if (it.espId == lamp.espId) updated else it
                    }
                } else {
                    _error.value = "Failed to toggle lamp"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Error toggling lamp"
            }
        }
    }
}
