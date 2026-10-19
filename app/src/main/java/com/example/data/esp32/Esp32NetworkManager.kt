package com.example.data.esp32

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.util.Log
import com.example.data.model.DeviceConnectionState
import com.example.data.model.Esp32Status
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Esp32NetworkManager(
    private val context: Context,
    private val apiClient: Esp32ApiClient
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var activeNetworkCallback: ConnectivityManager.NetworkCallback? = null
    private var boundNetwork: Network? = null
    private var connectionJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _connectionState = MutableStateFlow(DeviceConnectionState())
    val connectionState: StateFlow<DeviceConnectionState> = _connectionState.asStateFlow()

    fun connectToEsp32(
        ssid: String,
        password: String,
        ip: String,
        port: Int,
        timeoutSec: Int,
        isSimulationMode: Boolean
    ) {
        if (_connectionState.value.isConnecting) return

        connectionJob?.cancel()
        connectionJob = scope.launch {
            _connectionState.value = DeviceConnectionState(
                isConnecting = true,
                currentSsid = ssid,
                isSimulationMode = isSimulationMode,
                message = if (isSimulationMode) "Connecting to Simulated ESP32..." else "Connecting to $ssid..."
            )

            if (isSimulationMode) {
                // Mock connection simulation
                delay(1200)
                _connectionState.value = DeviceConnectionState(
                    isConnected = true,
                    isConnecting = false,
                    isSimulationMode = true,
                    currentSsid = ssid,
                    deviceStatus = Esp32Status(
                        status = "transfer_ready",
                        device = "ESP32-NotesMaker-SIM",
                        apSsid = ssid,
                        ipAddress = ip,
                        freeHeap = 184520,
                        totalFiles = 3,
                        transferMode = true,
                        batteryPercent = 88
                    ),
                    message = "Connected to Simulated ESP32 ($ssid)"
                )
                return@launch
            }

            // Real Android Wi-Fi connection flow
            val baseUrl = "http://$ip:$port"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val specifierBuilder = WifiNetworkSpecifier.Builder()
                        .setSsid(ssid)
                    if (password.isNotBlank()) {
                        specifierBuilder.setWpa2Passphrase(password)
                    }

                    val request = NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .setNetworkSpecifier(specifierBuilder.build())
                        .build()

                    unregisterNetworkCallback()

                    val callback = object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) {
                            super.onAvailable(network)
                            Log.d("Esp32NetworkManager", "Wi-Fi network available: $network")
                            boundNetwork = network
                            // Bind this process so HTTP requests to local ESP32 IP route through this Wi-Fi AP
                            connectivityManager.bindProcessToNetwork(network)

                            scope.launch {
                                verifyReachabilityAndSetConnected(baseUrl, ssid)
                            }
                        }

                        override fun onUnavailable() {
                            super.onUnavailable()
                            Log.e("Esp32NetworkManager", "ESP32 Wi-Fi network unavailable or cancelled")
                            _connectionState.value = DeviceConnectionState(
                                isConnected = false,
                                isConnecting = false,
                                message = "ESP32 Wi-Fi ($ssid) not found or connection rejected. Ensure button was held for 3s (Blue LED ON)."
                            )
                        }

                        override fun onLost(network: Network) {
                            super.onLost(network)
                            Log.d("Esp32NetworkManager", "ESP32 Wi-Fi lost: $network")
                            disconnect()
                        }
                    }

                    activeNetworkCallback = callback
                    connectivityManager.requestNetwork(request, callback, timeoutSec * 1000)

                } catch (e: Exception) {
                    Log.e("Esp32NetworkManager", "Error requesting Wi-Fi connection", e)
                    // If WifiNetworkSpecifier fails or is denied, test direct HTTP ping on current Wi-Fi
                    verifyReachabilityAndSetConnected(baseUrl, ssid)
                }
            } else {
                // Pre-Android 10 reachability verification
                verifyReachabilityAndSetConnected(baseUrl, ssid)
            }
        }
    }

    private suspend fun verifyReachabilityAndSetConnected(baseUrl: String, ssid: String) {
        withContext(Dispatchers.IO) {
            _connectionState.value = _connectionState.value.copy(
                message = "Verifying ESP32 status at $baseUrl..."
            )
            val result = apiClient.getStatus(baseUrl)
            if (result.isSuccess) {
                val status = result.getOrNull()
                _connectionState.value = DeviceConnectionState(
                    isConnected = true,
                    isConnecting = false,
                    currentSsid = ssid,
                    deviceStatus = status,
                    message = "Connected to ${status?.device ?: "ESP32"} ($ssid)"
                )
            } else {
                _connectionState.value = DeviceConnectionState(
                    isConnected = false,
                    isConnecting = false,
                    message = "Connected to Wi-Fi, but ESP32 API not responding at $baseUrl: ${result.exceptionOrNull()?.message}"
                )
                unregisterNetworkCallback()
            }
        }
    }

    fun disconnect() {
        connectionJob?.cancel()
        unregisterNetworkCallback()
        _connectionState.value = DeviceConnectionState(
            isConnected = false,
            isConnecting = false,
            message = "Disconnected from ESP32"
        )
    }

    private fun unregisterNetworkCallback() {
        try {
            connectivityManager.bindProcessToNetwork(null)
            boundNetwork = null
            activeNetworkCallback?.let {
                connectivityManager.unregisterNetworkCallback(it)
            }
        } catch (e: Exception) {
            Log.e("Esp32NetworkManager", "Error unregistering network callback", e)
        } finally {
            activeNetworkCallback = null
        }
    }
}
