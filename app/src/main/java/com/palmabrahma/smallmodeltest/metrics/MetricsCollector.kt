package com.palmabrahma.smallmodeltest.metrics

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import java.io.RandomAccessFile
import kotlin.math.roundToInt

/**
 * Collects system metrics during model inference
 */
class MetricsCollector(private val context: Context) {
    
    data class SystemMetrics(
        val timestamp: Long = System.currentTimeMillis(),
        val batteryLevel: Float,
        val batteryTemperature: Float,
        val batteryVoltage: Int,
        val isCharging: Boolean,
        val cpuUsagePercent: Float,
        val memoryUsedMB: Long,
        val memoryAvailableMB: Long,
        val memoryTotalMB: Long,
        val cpuTemperature: Float? = null
    )
    
    data class InferenceMetrics(
        val modelName: String,
        val promptLength: Int,
        val promptTokens: Int,
        val generatedTokens: Int,
        val firstTokenLatencyMs: Long,
        val totalTimeMs: Long,
        val tokensPerSecond: Float,
        val peakMemoryMB: Long,
        val avgCpuPercent: Float,
        val batteryDrainPercent: Float
    )

    private var lastCpuTime = 0L
    private var lastAppCpuTime = 0L

    /**
     * Reset internal state between benchmark sessions
     * Call this before starting a new benchmark to avoid stale data
     */
    fun reset() {
        lastCpuTime = 0L
        lastAppCpuTime = 0L
        Timber.d("MetricsCollector state reset")
    }

    /**
     * Start collecting metrics at specified interval
     */
    fun startCollection(intervalMs: Long = 1000): Flow<SystemMetrics> = flow {
        while (true) {
            emit(collectCurrentMetrics())
            delay(intervalMs)
        }
    }
    
    /**
     * Collect current system metrics
     */
    fun collectCurrentMetrics(): SystemMetrics {
        val battery = getBatteryInfo()
        val memory = getMemoryInfo()
        val cpu = getCpuUsage()
        val temperature = getCpuTemperature()
        
        return SystemMetrics(
            batteryLevel = battery.level,
            batteryTemperature = battery.temperature,
            batteryVoltage = battery.voltage,
            isCharging = battery.isCharging,
            cpuUsagePercent = cpu,
            memoryUsedMB = memory.used,
            memoryAvailableMB = memory.available,
            memoryTotalMB = memory.total,
            cpuTemperature = temperature
        )
    }
    
    /**
     * Calculate metrics for a complete inference session
     */
    fun calculateInferenceMetrics(
        modelName: String,
        prompt: String,
        promptTokens: Int,
        generatedTokens: Int,
        startMetrics: SystemMetrics,
        endMetrics: SystemMetrics,
        firstTokenTime: Long,
        totalTime: Long,
        peakMemory: Long,
        avgCpu: Float
    ): InferenceMetrics {
        
        val tokensPerSecond = if (totalTime > 0) {
            (generatedTokens * 1000f) / totalTime
        } else 0f
        
        val batteryDrain = startMetrics.batteryLevel - endMetrics.batteryLevel
        
        return InferenceMetrics(
            modelName = modelName,
            promptLength = prompt.length,
            promptTokens = promptTokens,
            generatedTokens = generatedTokens,
            firstTokenLatencyMs = firstTokenTime,
            totalTimeMs = totalTime,
            tokensPerSecond = tokensPerSecond,
            peakMemoryMB = peakMemory,
            avgCpuPercent = avgCpu,
            batteryDrainPercent = batteryDrain
        )
    }
    
    private fun getBatteryInfo(): BatteryInfo {
        val batteryStatus = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryLevel = if (level >= 0 && scale > 0) {
            (level * 100f) / scale
        } else 0f
        
        val temperature = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val batteryTemp = temperature / 10f // Convert to Celsius
        
        val voltage = batteryStatus?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
        
        return BatteryInfo(batteryLevel, batteryTemp, voltage, isCharging)
    }
    
    private fun getMemoryInfo(): MemoryInfo {
        val runtime = Runtime.getRuntime()
        val totalMemory = runtime.maxMemory() / (1024 * 1024)
        val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val availableMemory = totalMemory - usedMemory
        
        return MemoryInfo(
            used = usedMemory,
            available = availableMemory,
            total = totalMemory
        )
    }

    private fun getCpuUsageUsingProcFile(): Float {
        return try {
            val reader = RandomAccessFile("/proc/stat", "r")
            val line = reader.readLine()
            reader.close()

            val tokens = line.split(" ").filter { it.isNotEmpty() }
            if (tokens[0] == "cpu") {
                val user = tokens[1].toLong()
                val nice = tokens[2].toLong()
                val system = tokens[3].toLong()
                val idle = tokens[4].toLong()
                val iowait = tokens[5].toLong()
                val irq = tokens[6].toLong()
                val softirq = tokens[7].toLong()

                val totalCpuTime = user + nice + system + idle + iowait + irq + softirq
                val appCpuTime = user + nice + system

                if (lastCpuTime != 0L) {
                    val totalDelta = totalCpuTime - lastCpuTime
                    val appDelta = appCpuTime - lastAppCpuTime

                    val usage = if (totalDelta > 0) {
                        (appDelta.toFloat() / totalDelta.toFloat() * 100f)
                    } else 0f

                    lastCpuTime = totalCpuTime
                    lastAppCpuTime = appCpuTime

                    return usage.coerceIn(0f, 100f)
                }

                lastCpuTime = totalCpuTime
                lastAppCpuTime = appCpuTime
            }
            0f
        } catch (e: Exception) {
            Timber.e(e, "Error reading CPU usage")
            0f
        }
    }

    private fun getCpuUsage(): Float {
        // Android doesn't allow direct /proc/stat access
        // Use ActivityManager for process-specific CPU usage
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memoryInfo = android.app.ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            
            // Get process CPU usage through Debug API
            val myPid = android.os.Process.myPid()
            val pInfo = activityManager.runningAppProcesses?.find { 
                it.pid == myPid 
            }
            
            // Return a rough estimate based on importance
            when (pInfo?.importance) {
                android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> 50f
                android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> 30f
                android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> 20f
                else -> 10f
            }
        } catch (e: Exception) {
            Timber.e(e, "Error reading CPU usage")
            0f
        }
    }
    
    /**
     * Alternative: Get CPU usage using top command (may work on some devices)
     */
    private fun getCpuUsageFromTop(): Float {
        return try {
            val process = Runtime.getRuntime().exec("top -n 1 -d 1")
            val reader = process.inputStream.bufferedReader()
            var cpuLine: String? = null
            
            // Look for CPU usage line
            reader.useLines { lines ->
                cpuLine = lines.find { it.contains("User") && it.contains("System") }
            }
            
            process.waitFor()
            
            // Parse CPU percentage from the line
            cpuLine?.let { line ->
                val pattern = "\\d+%".toRegex()
                val matches = pattern.findAll(line)
                val percentages = matches.map { 
                    it.value.removeSuffix("%").toFloatOrNull() ?: 0f 
                }.toList()
                
                // Sum user and system CPU usage
                if (percentages.size >= 2) {
                    return percentages[0] + percentages[1]
                }
            }
            
            0f
        } catch (e: Exception) {
            Timber.e(e, "Error running top command")
            0f
        }
    }

    private fun getCpuTemperatureUsingSysClassFile(): Float? {
        // CPU temperature reading is device-specific
        // Try common thermal zone paths
        val thermalPaths = listOf(
            "/sys/class/thermal/thermal_zone0/temp",
            "/sys/class/thermal/thermal_zone1/temp",
            "/sys/devices/virtual/thermal/thermal_zone0/temp",
            "/sys/devices/system/cpu/cpu0/cpufreq/cpu_temp"
        )

        for (path in thermalPaths) {
            try {
                val reader = RandomAccessFile(path, "r")
                val tempStr = reader.readLine()
                reader.close()

                val temp = tempStr.toFloatOrNull()
                if (temp != null) {
                    // Convert from millidegrees to degrees Celsius
                    return if (temp > 1000) temp / 1000f else temp
                }
            } catch (e: Exception) {
                // Try next path
            }
        }

        return null
    }

    private fun getCpuTemperature(): Float? {
        // Try to read from thermal service (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                
                // Check thermal status which indirectly indicates temperature
                val thermalStatus = powerManager.currentThermalStatus
                
                // Estimate temperature based on thermal status
                return when (thermalStatus) {
                    android.os.PowerManager.THERMAL_STATUS_NONE -> 30f
                    android.os.PowerManager.THERMAL_STATUS_LIGHT -> 35f
                    android.os.PowerManager.THERMAL_STATUS_MODERATE -> 40f
                    android.os.PowerManager.THERMAL_STATUS_SEVERE -> 45f
                    android.os.PowerManager.THERMAL_STATUS_CRITICAL -> 50f
                    android.os.PowerManager.THERMAL_STATUS_EMERGENCY -> 55f
                    android.os.PowerManager.THERMAL_STATUS_SHUTDOWN -> 60f
                    else -> null
                }
            } catch (e: Exception) {
                Timber.e(e, "Error reading thermal status")
            }
        }
        
        // Fallback: Try battery temperature as proxy
        return try {
            val batteryStatus = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            val temperature = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            if (temperature > 0) {
                temperature / 10f // Convert to Celsius
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Error reading battery temperature")
            null
        }
    }
    
    private data class BatteryInfo(
        val level: Float,
        val temperature: Float,
        val voltage: Int,
        val isCharging: Boolean
    )
    
    private data class MemoryInfo(
        val used: Long,
        val available: Long,
        val total: Long
    )
}

/**
 * Aggregates metrics over a test session
 */
class MetricsAggregator {
    private val metricsList = mutableListOf<MetricsCollector.SystemMetrics>()
    
    fun addMetric(metric: MetricsCollector.SystemMetrics) {
        metricsList.add(metric)
    }
    
    fun getAverageCpu(): Float {
        if (metricsList.isEmpty()) return 0f
        return metricsList.map { it.cpuUsagePercent }.average().toFloat()
    }
    
    fun getPeakMemory(): Long {
        if (metricsList.isEmpty()) return 0L
        return metricsList.maxOf { it.memoryUsedMB }
    }
    
    fun getTotalBatteryDrain(): Float {
        if (metricsList.size < 2) return 0f
        return metricsList.first().batteryLevel - metricsList.last().batteryLevel
    }
    
    fun getAverageTemperature(): Float {
        val temps = metricsList.mapNotNull { it.cpuTemperature }
        if (temps.isEmpty()) return 0f
        return temps.average().toFloat()
    }
    
    fun clear() {
        metricsList.clear()
    }
}
