package com.example.myapplication.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 网络状态管理器
 * 监控设备的网络连接状态
 */
@Singleton
class NetworkStateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val TAG = "NetworkStateManager"
    }
    
    private val connectivityManager = 
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    /**
     * 网络连接状态数据类
     */
    data class NetworkState(
        val isConnected: Boolean,
        val isWiFi: Boolean,
        val isCellular: Boolean,
        val isEthernet: Boolean
    )
    
    /**
     * 监控网络状态变化
     */
    fun networkState(): Flow<NetworkState> = callbackFlow {
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            
            private val networks = mutableSetOf<Network>()
            
            override fun onAvailable(network: Network) {
                Log.d(TAG, "网络连接可用: $network")
                networks.add(network)
                updateNetworkState()
            }
            
            override fun onLost(network: Network) {
                Log.d(TAG, "网络连接丢失: $network")
                networks.remove(network)
                updateNetworkState()
            }
            
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                Log.d(TAG, "网络能力变化: $network")
                updateNetworkState()
            }
            
            private fun updateNetworkState() {
                val currentState = getCurrentNetworkState()
                trySend(currentState)
            }
        }
        
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        connectivityManager.registerNetworkCallback(request, networkCallback)
        
        // 发送初始状态
        trySend(getCurrentNetworkState())
        
        awaitClose {
            Log.d(TAG, "取消网络状态监听")
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }.distinctUntilChanged()
    
    /**
     * 获取当前网络状态
     */
    private fun getCurrentNetworkState(): NetworkState {
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        
        return if (capabilities != null) {
            NetworkState(
                isConnected = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                isWiFi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
                isCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
                isEthernet = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            )
        } else {
            NetworkState(
                isConnected = false,
                isWiFi = false,
                isCellular = false,
                isEthernet = false
            )
        }
    }
    
    /**
     * 检查当前是否有网络连接
     */
    fun isNetworkConnected(): Boolean {
        return getCurrentNetworkState().isConnected
    }
    
    /**
     * 检查是否为WiFi连接
     */
    fun isWiFiConnected(): Boolean {
        return getCurrentNetworkState().isWiFi
    }
    
    /**
     * 获取网络类型描述
     */
    fun getNetworkTypeDescription(): String {
        val state = getCurrentNetworkState()
        return when {
            !state.isConnected -> "无网络连接"
            state.isWiFi -> "WiFi"
            state.isCellular -> "移动数据"
            state.isEthernet -> "以太网"
            else -> "未知网络"
        }
    }
}
