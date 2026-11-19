package com.example.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WifiConnectivityState(
  val isWifiConnected: Boolean = false,
  val isAnyNetworkConnected: Boolean = false,
) {
  val statusLabel: String
    get() =
      when {
        isWifiConnected -> "Wi-Fi conectado"
        isAnyNetworkConnected -> "Datos móviles (en espera de Wi-Fi)"
        else -> "Sin conexión (modo offline)"
      }
}

interface ConnectivityMonitor {
  val connectivityState: StateFlow<WifiConnectivityState>

  fun refreshNow()

  fun stop()
}

class WifiConnectivityObserver(context: Context) : ConnectivityMonitor {
  private val connectivityManager =
    context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as?
      ConnectivityManager

  private val _connectivityState = MutableStateFlow(readCurrentState())
  override val connectivityState: StateFlow<WifiConnectivityState> = _connectivityState.asStateFlow()

  private val networkCallback =
    object : ConnectivityManager.NetworkCallback() {
      override fun onAvailable(network: Network) {
        refreshNow()
      }

      override fun onLost(network: Network) {
        refreshNow()
      }

      override fun onCapabilitiesChanged(
        network: Network,
        networkCapabilities: NetworkCapabilities,
      ) {
        refreshNow()
      }
    }

  private var registered = false

  init {
    registerCallback()
  }

  private fun registerCallback() {
    val cm = connectivityManager ?: return
    if (registered) return
    runCatching {
      val request =
        NetworkRequest.Builder()
          .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
          .build()
      cm.registerNetworkCallback(request, networkCallback)
      registered = true
    }
  }

  override fun refreshNow() {
    _connectivityState.value = readCurrentState()
  }

  private fun readCurrentState(): WifiConnectivityState {
    val cm = connectivityManager ?: return WifiConnectivityState()
    return runCatching {
        val active = cm.activeNetwork
        val activeCaps = active?.let { cm.getNetworkCapabilities(it) }
        val hasInternet =
          activeCaps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val hasWifiOnActive =
          activeCaps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true && hasInternet

        @Suppress("DEPRECATION")
        val hasAnyWifi =
          hasWifiOnActive ||
            cm.allNetworks.any { net ->
              val caps = cm.getNetworkCapabilities(net)
              caps != null &&
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }

        WifiConnectivityState(
          isWifiConnected = hasAnyWifi,
          isAnyNetworkConnected = hasInternet || hasAnyWifi,
        )
      }
      .getOrDefault(WifiConnectivityState())
  }

  override fun stop() {
    val cm = connectivityManager ?: return
    if (!registered) return
    runCatching { cm.unregisterNetworkCallback(networkCallback) }
    registered = false
  }
}
