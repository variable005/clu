package com.example.clu

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*
import java.util.LinkedList
import java.util.Queue

@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {

    private val bluetoothManager: BluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var bluetoothGatt: BluetoothGatt? = null
    private val descriptorQueue: Queue<BluetoothGattDescriptor> = LinkedList()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices

    private val _temperature = MutableStateFlow("--")
    val temperature: StateFlow<String> = _temperature

    private val _humidity = MutableStateFlow("--")
    val humidity: StateFlow<String> = _humidity

    private val _ruleIndex = MutableStateFlow("--")
    val ruleIndex: StateFlow<String> = _ruleIndex

    companion object {
        const val DEVICE_NAME = "VaultNodeAlpha"
        val SERVICE_UUID: UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
        val TEMP_CHAR_UUID: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
        val HUMID_CHAR_UUID: UUID = UUID.fromString("e3223119-9445-4e96-a4a1-85358c4046a2")
        val RULE_INDEX_CHAR_UUID: UUID = UUID.fromString("c82fb12e-13cb-4f4c-b715-385c49ea0718")
        val CCCD_DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val TAG = "BleManager"
    }

    enum class ConnectionState {
        DISCONNECTED, SCANNING, CONNECTING, CONNECTED
    }

    fun startScanning() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth not enabled")
            return
        }

        val scanner = bluetoothAdapter.bluetoothLeScanner
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        _connectionState.value = ConnectionState.SCANNING
        _discoveredDevices.value = emptyList()
        scanner.startScan(listOf(filter), settings, scanCallback)
    }

    fun stopScanning() {
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val currentList = _discoveredDevices.value
            if (currentList.none { it.address == device.address }) {
                _discoveredDevices.value = currentList + device
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error: $errorCode")
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    fun connectToDevice(device: BluetoothDevice) {
        stopScanning()
        _connectionState.value = ConnectionState.CONNECTING
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Connected to GATT server.")
                _connectionState.value = ConnectionState.CONNECTED
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT server.")
                _connectionState.value = ConnectionState.DISCONNECTED
                bluetoothGatt = null
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                service?.let {
                    subscribeToCharacteristic(gatt, it.getCharacteristic(TEMP_CHAR_UUID))
                    subscribeToCharacteristic(gatt, it.getCharacteristic(HUMID_CHAR_UUID))
                    subscribeToCharacteristic(gatt, it.getCharacteristic(RULE_INDEX_CHAR_UUID))
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val value = characteristic.value?.toString(Charsets.UTF_8) ?: "--"
            Log.d(TAG, "Characteristic changed: ${characteristic.uuid}, value: $value")
            when (characteristic.uuid) {
                TEMP_CHAR_UUID -> _temperature.value = value
                HUMID_CHAR_UUID -> _humidity.value = value
                RULE_INDEX_CHAR_UUID -> _ruleIndex.value = value
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Descriptor write successful: ${descriptor.uuid}")
            } else {
                Log.e(TAG, "Descriptor write failed: ${descriptor.uuid}, status: $status")
            }
            descriptorQueue.poll() // Remove the one that just finished
            processNextDescriptor() // Try the next one in the queue
        }
    }

    private fun subscribeToCharacteristic(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic?) {
        characteristic?.let {
            gatt.setCharacteristicNotification(it, true)
            val descriptor = it.getDescriptor(CCCD_DESCRIPTOR_UUID)
            descriptor?.let { desc ->
                desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                descriptorQueue.add(desc)
                if (descriptorQueue.size == 1) {
                    processNextDescriptor()
                }
            }
        }
    }

    private fun processNextDescriptor() {
        bluetoothGatt?.let { gatt ->
            val nextDesc = descriptorQueue.peek()
            if (nextDesc != null) {
                val success = gatt.writeDescriptor(nextDesc)
                if (!success) {
                    Log.e(TAG, "Failed to initiate descriptor write, skipping")
                    descriptorQueue.poll()
                    processNextDescriptor()
                }
            }
        }
    }

    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        descriptorQueue.clear()
        _connectionState.value = ConnectionState.DISCONNECTED
    }
}
