package com.palmabrahma.smallmodeltest.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.palmabrahma.smallmodeltest.R
import com.palmabrahma.smallmodeltest.benchmark.BenchmarkRunner
import com.palmabrahma.smallmodeltest.metrics.MetricsCollector
import com.palmabrahma.smallmodeltest.models.BaseModelAdapter
import com.palmabrahma.smallmodeltest.models.Phi3MiniAdapter
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.File

/**
 * Foreground service for running long-duration benchmarks
 * Prevents the app from being killed during 30+ minute tests
 */
class BenchmarkService : Service() {

    companion object {
        private const val CHANNEL_ID = "benchmark_channel"
        private const val NOTIFICATION_ID = 1001

        // Intent actions
        const val ACTION_START_BENCHMARK = "com.llmtest.hybrid.START_BENCHMARK"
        const val ACTION_STOP_BENCHMARK = "com.llmtest.hybrid.STOP_BENCHMARK"

        // Intent extras
        const val EXTRA_MODEL_TYPE = "model_type"
        const val EXTRA_TEST_TYPE = "test_type"
        const val EXTRA_DURATION_MINUTES = "duration_minutes"

        // Broadcast actions for UI updates
        const val BROADCAST_PROGRESS = "com.llmtest.hybrid.BENCHMARK_PROGRESS"
        const val BROADCAST_COMPLETE = "com.llmtest.hybrid.BENCHMARK_COMPLETE"
        const val BROADCAST_ERROR = "com.llmtest.hybrid.BENCHMARK_ERROR"

        // Broadcast extras
        const val EXTRA_PROGRESS_MESSAGE = "progress_message"
        const val EXTRA_TOKENS_PER_SECOND = "tokens_per_second"
        const val EXTRA_BATTERY_LEVEL = "battery_level"
        const val EXTRA_MEMORY_MB = "memory_mb"
        const val EXTRA_RESULT_FILE = "result_file"
        const val EXTRA_ERROR_MESSAGE = "error_message"

        fun startBenchmark(
            context: Context,
            modelType: String,
            testType: String,
            durationMinutes: Int = 30
        ) {
            val intent = Intent(context, BenchmarkService::class.java).apply {
                action = ACTION_START_BENCHMARK
                putExtra(EXTRA_MODEL_TYPE, modelType)
                putExtra(EXTRA_TEST_TYPE, testType)
                putExtra(EXTRA_DURATION_MINUTES, durationMinutes)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopBenchmark(context: Context) {
            val intent = Intent(context, BenchmarkService::class.java).apply {
                action = ACTION_STOP_BENCHMARK
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var currentBenchmarkJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var metricsCollector: MetricsCollector
    private lateinit var benchmarkRunner: BenchmarkRunner

    override fun onCreate() {
        super.onCreate()
        Timber.d("BenchmarkService created")

        metricsCollector = MetricsCollector(this)
        benchmarkRunner = BenchmarkRunner(metricsCollector)

        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_BENCHMARK -> {
                val modelType = intent.getStringExtra(EXTRA_MODEL_TYPE) ?: return START_NOT_STICKY
                val testType = intent.getStringExtra(EXTRA_TEST_TYPE) ?: return START_NOT_STICKY
                val duration = intent.getIntExtra(EXTRA_DURATION_MINUTES, 30)

                startForeground(NOTIFICATION_ID, createNotification("Initializing benchmark..."))
                runBenchmark(modelType, testType, duration)
            }
            ACTION_STOP_BENCHMARK -> {
                stopBenchmark()
            }
        }

        return START_STICKY
    }

    private fun runBenchmark(modelType: String, testType: String, durationMinutes: Int) {
        currentBenchmarkJob?.cancel()

        currentBenchmarkJob = serviceScope.launch {
            try {
                Timber.d("Starting benchmark: $modelType, $testType, ${durationMinutes}min")

                updateNotification("Loading model: $modelType")
                broadcastProgress("Loading model...")

                val model = createModelAdapter(modelType)

                // Start metrics collection in background
                val metricsJob = launch {
                    metricsCollector.startCollection(1000).collect { metrics ->
                        broadcastMetrics(
                            batteryLevel = metrics.batteryLevel,
                            memoryMB = metrics.memoryUsedMB,
                            tokensPerSecond = 0f // Will be updated by benchmark
                        )
                    }
                }

                updateNotification("Running $testType test...")

                val results = when (testType) {
                    "BASIC" -> {
                        benchmarkRunner.runBasicPerformanceTest(model)
                    }
                    "SUSTAINED" -> {
                        benchmarkRunner.runSustainedConversationTest(model, durationMinutes)
                    }
                    "ROUTING" -> {
                        benchmarkRunner.runRoutingClassificationTest(model)
                    }
                    else -> {
                        throw IllegalArgumentException("Unknown test type: $testType")
                    }
                }

                metricsJob.cancel()

                // Save results
                val resultFile = saveResults(results)

                updateNotification("Benchmark complete!")
                broadcastComplete(resultFile)

                Timber.d("Benchmark completed successfully")

            } catch (e: Exception) {
                Timber.e(e, "Benchmark failed")
                updateNotification("Benchmark failed: ${e.message}")
                broadcastError(e.message ?: "Unknown error")
            } finally {
                // Keep service alive for a bit to ensure results are saved
                delay(5000)
                stopSelf()
            }
        }
    }

    private fun createModelAdapter(modelType: String): BaseModelAdapter {
        return when (modelType) {
            "PHI3_MINI" -> Phi3MiniAdapter(this)
            "GEMMA_2B" -> {
                // TODO: Implement Gemma adapter
                throw NotImplementedError("Gemma 2B not yet implemented")
            }
            "TINY_LLAMA" -> {
                // TODO: Implement TinyLlama adapter
                throw NotImplementedError("TinyLlama not yet implemented")
            }
            else -> throw IllegalArgumentException("Unknown model type: $modelType")
        }
    }

    private suspend fun saveResults(results: BenchmarkRunner.BenchmarkResults): String {
        return withContext(Dispatchers.IO) {
            val json = resultsToJson(results)
            val filename = "benchmark_${results.modelName.replace(" ", "_")}_${System.currentTimeMillis()}.json"
            val file = File(filesDir, filename)
            file.writeText(json)
            Timber.d("Results saved to: ${file.absolutePath}")
            file.absolutePath
        }
    }

    private fun resultsToJson(results: BenchmarkRunner.BenchmarkResults): String {
        // Simple JSON serialization
        return """
            {
                "modelName": "${results.modelName}",
                "testScenario": "${results.testScenario}",
                "device": {
                    "model": "${results.deviceInfo.model}",
                    "manufacturer": "${results.deviceInfo.manufacturer}",
                    "androidVersion": "${results.deviceInfo.androidVersion}"
                },
                "metrics": {
                    "avgTokensPerSecond": ${results.aggregateMetrics.avgTokensPerSecond},
                    "avgFirstTokenLatency": ${results.aggregateMetrics.avgFirstTokenLatency},
                    "totalBatteryDrain": ${results.aggregateMetrics.totalBatteryDrain},
                    "peakMemoryMB": ${results.aggregateMetrics.peakMemoryMB},
                    "avgCpuPercent": ${results.aggregateMetrics.avgCpuPercent},
                    "maxTemperature": ${results.aggregateMetrics.maxTemperatureC ?: "null"}
                },
                "duration": {
                    "totalMs": ${results.totalDurationMs},
                    "modelLoadMs": ${results.modelLoadTimeMs}
                },
                "prompts": [
                    ${results.promptResults.joinToString(",\n") { prompt ->
            """
                        {
                            "prompt": "${prompt.prompt.replace("\"", "\\\"")}",
                            "tokensGenerated": ${prompt.tokensGenerated},
                            "tokensPerSecond": ${prompt.tokensPerSecond},
                            "firstTokenLatencyMs": ${prompt.firstTokenLatencyMs},
                            "totalTimeMs": ${prompt.totalTimeMs}
                        }
                        """.trimIndent()
        }}
                ]
            }
        """.trimIndent()
    }

    private fun stopBenchmark() {
        Timber.d("Stopping benchmark")
        currentBenchmarkJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Benchmark Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows benchmark progress"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(message: String): Notification {
        val stopIntent = Intent(this, BenchmarkService::class.java).apply {
            action = ACTION_STOP_BENCHMARK
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LLM Benchmark Running")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_delete,
                "Stop",
                stopPendingIntent
            )
            .build()
    }

    private fun updateNotification(message: String) {
        val notification = createNotification(message)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "LLMBenchmark::WakeLock"
        ).apply {
            acquire(60 * 60 * 1000L) // Max 60 minutes
        }
        Timber.d("WakeLock acquired")
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Timber.d("WakeLock released")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error releasing WakeLock")
        }
    }

    // Broadcast methods for UI updates
    private fun broadcastProgress(message: String) {
        val intent = Intent(BROADCAST_PROGRESS).apply {
            putExtra(EXTRA_PROGRESS_MESSAGE, message)
        }
        sendBroadcast(intent)
    }

    private fun broadcastMetrics(
        tokensPerSecond: Float,
        batteryLevel: Float,
        memoryMB: Long
    ) {
        val intent = Intent(BROADCAST_PROGRESS).apply {
            putExtra(EXTRA_TOKENS_PER_SECOND, tokensPerSecond)
            putExtra(EXTRA_BATTERY_LEVEL, batteryLevel)
            putExtra(EXTRA_MEMORY_MB, memoryMB)
        }
        sendBroadcast(intent)
    }

    private fun broadcastComplete(resultFile: String) {
        val intent = Intent(BROADCAST_COMPLETE).apply {
            putExtra(EXTRA_RESULT_FILE, resultFile)
        }
        sendBroadcast(intent)
    }

    private fun broadcastError(error: String) {
        val intent = Intent(BROADCAST_ERROR).apply {
            putExtra(EXTRA_ERROR_MESSAGE, error)
        }
        sendBroadcast(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("BenchmarkService destroyed")
        currentBenchmarkJob?.cancel()
        serviceScope.cancel()
        releaseWakeLock()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}