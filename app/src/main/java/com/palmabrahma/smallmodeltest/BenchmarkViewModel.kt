package com.palmabrahma.smallmodeltest

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.palmabrahma.smallmodeltest.benchmark.BenchmarkRunner
import com.palmabrahma.smallmodeltest.metrics.MetricsCollector
import com.palmabrahma.smallmodeltest.models.BaseModelAdapter
import com.palmabrahma.smallmodeltest.models.Phi3MiniAdapter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.RECEIVER_NOT_EXPORTED
import android.content.Intent
import android.content.IntentFilter
import com.palmabrahma.smallmodeltest.models.ModelType
import com.palmabrahma.smallmodeltest.services.BenchmarkService
import java.util.concurrent.atomic.AtomicBoolean


class BenchmarkViewModel(application: Application) : AndroidViewModel(application) {

    private val metricsCollector = MetricsCollector(application)
    private val benchmarkRunner = BenchmarkRunner(metricsCollector)

    private val _uiState = MutableStateFlow(BenchmarkUiState())
    val uiState: StateFlow<BenchmarkUiState> = _uiState.asStateFlow()

    private var currentBenchmarkJob: Job? = null
    private var metricsCollectionJob: Job? = null

    // Atomic flag to prevent concurrent start/stop operations
    private val isBenchmarkOperationInProgress = AtomicBoolean(false)

    // Track whether receiver is registered to prevent leaks
    private var isReceiverRegistered = false

    // Broadcast receiver for service updates
    private val benchmarkReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Timber.d("Received metrics broadcast.. action=${intent?.action}")
            when (intent?.action) {
                BenchmarkService.BROADCAST_PROGRESS -> {
                    val message = intent.getStringExtra(BenchmarkService.EXTRA_PROGRESS_MESSAGE)
                    val tokensPerSecond = intent.getFloatExtra(BenchmarkService.EXTRA_TOKENS_PER_SECOND, 0f)
                    val batteryLevel = intent.getFloatExtra(BenchmarkService.EXTRA_BATTERY_LEVEL, 100f)
                    val memoryMB = intent.getLongExtra(BenchmarkService.EXTRA_MEMORY_MB, 0)
                    val cpuUsage = intent.getFloatExtra(BenchmarkService.EXTRA_CPU_USAGE, 0f)
                    val temperature = intent.getFloatExtra(BenchmarkService.EXTRA_TEMPERATURE, 0f)

                    _uiState.update { state ->
                        state.copy(
                            currentMetrics = RealTimeMetrics(
                                tokensPerSecond = tokensPerSecond,
                                batteryLevel = batteryLevel,
                                memoryUsedMB = memoryMB,
                                cpuUsage = cpuUsage,
                                temperature = temperature
                            )
                        )
                    }
                }
                BenchmarkService.BROADCAST_COMPLETE -> {
                    val resultFile = intent.getStringExtra(BenchmarkService.EXTRA_RESULT_FILE)
                    Timber.d("Benchmark completed. Results: $resultFile")
                    _uiState.update { it.copy(isRunning = false) }
                    // TODO: Load and display results from file
                }
                BenchmarkService.BROADCAST_ERROR -> {
                    val error = intent.getStringExtra(BenchmarkService.EXTRA_ERROR_MESSAGE)
                    _uiState.update {
                        it.copy(
                            isRunning = false,
                            error = error ?: "Unknown error"
                        )
                    }
                }
            }
        }
    }

    init {
        Timber.plant(Timber.DebugTree())

        // Register broadcast receiver for service updates
        val filter = IntentFilter().apply {
            addAction(BenchmarkService.BROADCAST_PROGRESS)
            addAction(BenchmarkService.BROADCAST_COMPLETE)
            addAction(BenchmarkService.BROADCAST_ERROR)
        }

        try {
            getApplication<Application>().registerReceiver(benchmarkReceiver, filter, RECEIVER_NOT_EXPORTED)
            isReceiverRegistered = true
            Timber.d("BroadcastReceiver registered successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to register BroadcastReceiver")
            isReceiverRegistered = false
        }
    }

    fun selectModel(model: ModelType) {
        _uiState.update { it.copy(selectedModel = model) }
    }

    fun selectTest(test: TestType) {
        _uiState.update { it.copy(selectedTest = test) }
    }

    private fun startBenchmarkInService(model: ModelType, test: TestType) {
        _uiState.update { it.copy(isRunning = true, error = null) }

        val durationMinutes = when (test) {
            TestType.SUSTAINED -> 30
            TestType.BURST -> 60
            else -> 10
        }

        BenchmarkService.startBenchmark(
            context = getApplication(),
            modelType = model.name,
            testType = test.name,
            durationMinutes = durationMinutes
        )
    }



    fun startBenchmark() {
        val selectedModel = _uiState.value.selectedModel ?: return
        val selectedTest = _uiState.value.selectedTest

        // Prevent concurrent start operations
        if (!isBenchmarkOperationInProgress.compareAndSet(false, true)) {
            Timber.w("Start benchmark already in progress, ignoring duplicate request")
            return
        }

        // Reset metrics collector state before starting new benchmark
        metricsCollector.reset()

        // For long tests (>10 minutes), use the service to prevent app termination
        if (selectedTest == TestType.SUSTAINED) {
            startBenchmarkInService(selectedModel, selectedTest)
            isBenchmarkOperationInProgress.set(false)
            return
        }

        currentBenchmarkJob = viewModelScope.launch {
            try {
                _uiState.update { it.copy(isRunning = true, error = null) }

                // Start real-time metrics collection
                startMetricsCollection()

                // Create model adapter
                val modelAdapter = createModelAdapter(selectedModel)

                // Run selected test
                val results = when (selectedTest) {
                    TestType.BASIC -> {
                        benchmarkRunner.runBasicPerformanceTest(modelAdapter)
                    }
                    TestType.SUSTAINED -> {
                        benchmarkRunner.runSustainedConversationTest(modelAdapter, durationMinutes = 30)
                    }
                    TestType.ROUTING -> {
                        benchmarkRunner.runRoutingClassificationTest(modelAdapter)
                    }
                    TestType.BURST -> {
                        // TODO: Implement burst test
                        benchmarkRunner.runBasicPerformanceTest(modelAdapter)
                    }
                }

                // Convert results to UI model
                val testResult = TestResult(
                    modelName = results.modelName,
                    testType = results.testScenario,
                    avgTokensPerSecond = results.aggregateMetrics.avgTokensPerSecond,
                    batteryDrain = results.aggregateMetrics.totalBatteryDrain,
                    peakMemoryMB = results.aggregateMetrics.peakMemoryMB
                )

                _uiState.update { state ->
                    state.copy(
                        isRunning = false,
                        results = state.results + testResult,
                        lastCompletedTest = testResult
                    )
                }

                // Save results to file for later analysis
                saveResults(results)

            } catch (e: Exception) {
                Timber.e(e, "Benchmark failed")
                _uiState.update {
                    it.copy(
                        isRunning = false,
                        error = e.message ?: "Unknown error occurred"
                    )
                }
            } finally {
                stopMetricsCollection()
                isBenchmarkOperationInProgress.set(false)
            }
        }
    }

    fun stopBenchmark() {
        // Stop service-based benchmark if running
        if (_uiState.value.selectedTest == TestType.SUSTAINED) {
            BenchmarkService.stopBenchmark(getApplication())
        }

        currentBenchmarkJob?.cancel()
        stopMetricsCollection()
        _uiState.update { it.copy(isRunning = false) }

        // Reset the operation flag to allow new benchmarks to start
        isBenchmarkOperationInProgress.set(false)
    }

    private fun createModelAdapter(modelType: ModelType): BaseModelAdapter {
        return when (modelType) {
            ModelType.PHI3_MINI -> Phi3MiniAdapter(getApplication())
            ModelType.GEMMA_2B -> {
                // TODO: Implement Gemma adapter
                throw NotImplementedError("Gemma 2B adapter not yet implemented")
            }
            ModelType.TINY_LLAMA -> {
                // TODO: Implement TinyLlama adapter
                throw NotImplementedError("TinyLlama adapter not yet implemented")
            }
        }
    }

    private fun startMetricsCollection() {
        metricsCollectionJob = viewModelScope.launch {
            metricsCollector.startCollection(intervalMs = 500)
                .collect { metrics ->
                    _uiState.update { state ->
                        state.copy(
                            currentMetrics = RealTimeMetrics(
                                tokensPerSecond = 0f, // Will be updated from benchmark
                                batteryLevel = metrics.batteryLevel,
                                memoryUsedMB = metrics.memoryUsedMB,
                                cpuUsage = metrics.cpuUsagePercent,
                                temperature = metrics.cpuTemperature ?: 0f
                            )
                        )
                    }
                }
        }
    }

    private fun stopMetricsCollection() {
        metricsCollectionJob?.cancel()
        metricsCollectionJob = null
    }

    private suspend fun saveResults(results: BenchmarkRunner.BenchmarkResults) {
        withContext(Dispatchers.IO) {
            try {
                // Convert results to JSON
                val json = resultsToJson(results)

                // Save to file
                val filename = "benchmark_${results.modelName}_${System.currentTimeMillis()}.json"
                val file = java.io.File(getApplication<Application>().filesDir, filename)
                file.writeText(json)

                Timber.d("Results saved to: ${file.absolutePath}")
            } catch (e: Exception) {
                Timber.e(e, "Failed to save results")
            }
        }
    }

    private fun resultsToJson(results: BenchmarkRunner.BenchmarkResults): String {
        // Simple JSON serialization
        // In production, use proper JSON library like Gson or kotlinx.serialization
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
                    "avgCpuPercent": ${results.aggregateMetrics.avgCpuPercent}
                },
                "duration": {
                    "totalMs": ${results.totalDurationMs},
                    "modelLoadMs": ${results.modelLoadTimeMs}
                }
            }
        """.trimIndent()
    }

    override fun onCleared() {
        super.onCleared()
        stopBenchmark()
        unregisterReceiverIfNeeded()
    }

    private fun unregisterReceiverIfNeeded() {
        if (isReceiverRegistered) {
            try {
                getApplication<Application>().unregisterReceiver(benchmarkReceiver)
                Timber.d("BroadcastReceiver unregistered successfully")
            } catch (e: IllegalArgumentException) {
                // Receiver was not registered - ignore and clear flag
                Timber.w(e, "Receiver was not registered, clearing flag")
            } catch (e: Exception) {
                Timber.e(e, "Error unregistering receiver")
            } finally {
                isReceiverRegistered = false
            }
        }
    }
}

data class BenchmarkUiState(
    val selectedModel: ModelType? = null,
    val selectedTest: TestType = TestType.BASIC,
    val isRunning: Boolean = false,
    val currentMetrics: RealTimeMetrics? = null,
    val results: List<TestResult> = emptyList(),
    val lastCompletedTest: TestResult? = null,
    val error: String? = null
)