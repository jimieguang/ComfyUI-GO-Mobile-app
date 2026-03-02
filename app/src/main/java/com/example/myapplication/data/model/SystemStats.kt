package com.example.myapplication.data.model

import kotlinx.serialization.Serializable

/**
 * 系统资源统计信息（简化版，适合频繁轮询）
 */
@Serializable
data class SystemStatsSimple(
    val cpu_percent: Float = 0f,
    val ram_percent: Float = 0f,
    val ram_used_gb: Float = 0f,
    val gpu_percent: Float = 0f,
    val gpu_used_gb: Float = 0f,
    val gpu_total_gb: Float = 0f
)

/**
 * 完整的系统资源统计信息
 */
@Serializable
data class SystemStatsFull(
    val cpu: CpuStats = CpuStats(),
    val ram: RamStats = RamStats(),
    val gpu: GpuStats = GpuStats(),
    val disk: DiskStats = DiskStats()
)

@Serializable
data class CpuStats(
    val percent: Float = 0f,
    val cores: Int = 0,
    val frequency_mhz: Float = 0f
)

@Serializable
data class RamStats(
    val used_gb: Float = 0f,
    val total_gb: Float = 0f,
    val percent: Float = 0f
)

@Serializable
data class GpuStats(
    val available: Boolean = false,
    val name: String = "N/A",
    val memory_used_gb: Float = 0f,
    val memory_total_gb: Float = 0f,
    val memory_percent: Float = 0f,
    val utilization_percent: Float = 0f,
    val temperature_c: Float = 0f
)

@Serializable
data class DiskStats(
    val used_gb: Float = 0f,
    val total_gb: Float = 0f,
    val percent: Float = 0f
)

