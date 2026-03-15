package com.example.clu

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

import kotlinx.coroutines.launch
import com.example.clu.data.VaultDatabase
import com.example.clu.data.VaultRecord
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import java.time.LocalDate
import java.time.ZoneId
import java.time.LocalTime

class VaultViewModel(application: Application) : AndroidViewModel(application) {

    private val bleManager = BleManager(application)
    private val database = VaultDatabase.getDatabase(application)
    private val vaultDao = database.vaultDao()

    val connectionState: StateFlow<BleManager.ConnectionState> = bleManager.connectionState
    val discoveredDevices: StateFlow<List<android.bluetooth.BluetoothDevice>> = bleManager.discoveredDevices
    
    private val prefs = application.getSharedPreferences("VaultPrefs", android.content.Context.MODE_PRIVATE)

    private val _tempThreshold = MutableStateFlow(prefs.getFloat("tempThreshold", 35f))
    val tempThreshold: StateFlow<Float> = _tempThreshold

    private val _humThreshold = MutableStateFlow(prefs.getFloat("humThreshold", 70f))
    val humThreshold: StateFlow<Float> = _humThreshold

    private val _ruleThreshold = MutableStateFlow(prefs.getFloat("ruleThreshold", 100f))
    val ruleThreshold: StateFlow<Float> = _ruleThreshold

    // null = System, true = Dark, false = Light
    private val _isDarkMode = MutableStateFlow<Boolean?>(
        when (prefs.getString("themeMode", "SYSTEM")) {
            "DARK" -> true
            "LIGHT" -> false
            else -> null
        }
    )
    val isDarkMode: StateFlow<Boolean?> = _isDarkMode

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate

    @OptIn(ExperimentalCoroutinesApi::class)
    val historicalRecords: StateFlow<List<VaultRecord>> = _selectedDate
        .flatMapLatest { date ->
            val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endOfDay = date.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            vaultDao.getRecordsForDateRange(startOfDay, endOfDay)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dailyMaxTemp: StateFlow<Float?> = historicalRecords.map { records ->
        records.maxOfOrNull { it.temperature }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val dailyMinTemp: StateFlow<Float?> = historicalRecords.map { records ->
        records.minOfOrNull { it.temperature }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val dailyAvgHumidity: StateFlow<Float?> = historicalRecords.map { records ->
        if (records.isEmpty()) null else records.map { it.humidity }.average().toFloat()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    
    val temperature: StateFlow<String> = bleManager.temperature.map { cleanNumericString(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "--")
    
    val humidity: StateFlow<String> = bleManager.humidity.map { cleanNumericString(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "--")
    
    val ruleIndex: StateFlow<String> = bleManager.ruleIndex.map { cleanNumericString(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "--")

    val isAlertActive: StateFlow<Boolean> = combine(
        combine(temperature, humidity, ruleIndex) { t, h, r -> Triple(t, h, r) },
        combine(_tempThreshold, _humThreshold, _ruleThreshold) { tt, ht, rt -> Triple(tt, ht, rt) }
    ) { data, thresholds ->
        val (t, h, r) = data
        val (tt, ht, rt) = thresholds
        val tempVal = t.toFloatOrNull() ?: 0f
        val humVal = h.toFloatOrNull() ?: 0f
        val ruleVal = r.toFloatOrNull() ?: 0f
        tempVal > tt || humVal > ht || ruleVal > rt
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val ruleStatus: StateFlow<RuleStatus> = ruleIndex.map { indexStr ->
        val index = indexStr.toFloatOrNull() ?: -1f
        
        // Trigger data persistence when data changes
        val t = temperature.value.toFloatOrNull()
        val h = humidity.value.toFloatOrNull()
        if (t != null && h != null && index >= 0) {
            saveRecord(t, h, index)
        }

        when {
            index < 0 -> RuleStatus.UNKNOWN
            index < 90 -> RuleStatus.OPTIMAL
            index <= 100 -> RuleStatus.WARNING
            else -> RuleStatus.MOLD_RISK
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RuleStatus.UNKNOWN)

    fun updateThresholds(temp: Float, hum: Float, rule: Float) {
        viewModelScope.launch {
            _tempThreshold.value = temp
            _humThreshold.value = hum
            _ruleThreshold.value = rule
            prefs.edit().apply {
                putFloat("tempThreshold", temp)
                putFloat("humThreshold", hum)
                putFloat("ruleThreshold", rule)
                apply()
            }
        }
    }

    private fun saveRecord(temp: Float, hum: Float, index: Float) {
        viewModelScope.launch {
            vaultDao.insert(
                VaultRecord(
                    timestamp = System.currentTimeMillis(),
                    temperature = temp,
                    humidity = hum,
                    ruleIndex = index
                )
            )
        }
    }

    fun selectNextDay() {
        _selectedDate.value = _selectedDate.value.plusDays(1)
    }

    fun selectPreviousDay() {
        _selectedDate.value = _selectedDate.value.minusDays(1)
    }

    fun toggleTheme() {
        val nextValue = when (_isDarkMode.value) {
            null -> true // From System to Dark
            true -> false // From Dark to Light
            false -> null // From Light back to System
        }
        _isDarkMode.value = nextValue
        prefs.edit().putString("themeMode", when (nextValue) {
            true -> "DARK"
            false -> "LIGHT"
            else -> "SYSTEM"
        }).apply()
    }

    private fun cleanNumericString(input: String): String {
        if (input == "--") return input
        val cleaned = input.filter { it.isDigit() || it == '.' || it == '-' }
        return cleaned.ifEmpty { "--" }
    }

    fun startScan() {
        bleManager.startScanning()
    }

    fun connectToDevice(device: android.bluetooth.BluetoothDevice) {
        bleManager.connectToDevice(device)
    }

    fun disconnect() {
        bleManager.disconnect()
    }

    override fun onCleared() {
        super.onCleared()
        bleManager.disconnect()
    }
}

enum class RuleStatus(val label: String) {
    OPTIMAL("OPTIMAL"),
    WARNING("WARNING"),
    MOLD_RISK("MOLD RISK"),
    UNKNOWN("UNKNOWN")
}
