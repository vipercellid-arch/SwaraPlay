package com.swara.play.data.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import com.swara.play.BuildConfig
import com.swara.play.model.ConnectedDevice
import com.swara.play.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class DeviceIntegrationManager(context: Context) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val serviceType = "_swaraplay._tcp."
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private val _discoveredDevices = MutableStateFlow<List<ConnectedDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<ConnectedDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var registrationListener: NsdManager.RegistrationListener? = null

    fun registerThisDevice() {
        val mgr = nsdManager ?: return
        runCatching {
            val info = NsdServiceInfo().apply {
                val kind = if (BuildConfig.IS_TV_BUILD) "SwaraPlay TV" else "SwaraPlay"
                serviceName = "$kind - ${Build.MODEL}"
                serviceType = this@DeviceIntegrationManager.serviceType
                port = 8765
            }
            val listener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {}
                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            }
            registrationListener = listener
            mgr.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        }
    }

    fun startScanning() {
        val mgr = nsdManager ?: return
        stopScanning()
        _isScanning.value = true
        runCatching {
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {}
                override fun onServiceFound(service: NsdServiceInfo) {
                    if (!service.serviceType.contains("swaraplay", ignoreCase = true)) return
                    @Suppress("DEPRECATION")
                    mgr.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            @Suppress("DEPRECATION")
                            val hostAddr = serviceInfo.host?.hostAddress ?: return
                            val name = serviceInfo.serviceName.ifBlank { "Perangkat SwaraPlay" }
                            val type = if (name.contains("TV", ignoreCase = true)) "TV" else "HP/Tablet"
                            val dev = ConnectedDevice(
                                id = "$hostAddr:${serviceInfo.port}",
                                name = name,
                                host = hostAddr,
                                port = serviceInfo.port,
                                deviceType = type
                            )
                            val cur = _discoveredDevices.value.toMutableList()
                            cur.removeAll { it.host == dev.host }
                            cur.add(0, dev)
                            _discoveredDevices.value = cur.take(10)
                        }
                    })
                }
                override fun onServiceLost(service: NsdServiceInfo) {}
                override fun onDiscoveryStopped(serviceType: String) { _isScanning.value = false }
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { _isScanning.value = false }
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) { _isScanning.value = false }
            }
            discoveryListener = listener
            mgr.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure {
            _isScanning.value = false
        }
    }

    fun stopScanning() {
        val mgr = nsdManager ?: return
        discoveryListener?.let { l -> runCatching { mgr.stopServiceDiscovery(l) } }
        discoveryListener = null
        _isScanning.value = false
    }

    suspend fun castMediaToDevice(device: ConnectedDevice, item: MediaItem, season: Int, episode: Int): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject()
                    .put("id", item.id)
                    .put("title", item.title)
                    .put("category", item.category.name)
                    .put("imdbId", item.imdbId)
                    .put("aniListId", item.aniListId ?: -1)
                    .put("season", season)
                    .put("episode", episode)
                val req = Request.Builder()
                    .url("http://${device.host}:${device.port}/swaraplay/cast")
                    .post(payload.toString().toRequestBody(jsonType))
                    .build()
                HttpClientProvider.client.newCall(req).execute().use { it.isSuccessful }
            } catch (_: Exception) {
                false
            }
        }
}
