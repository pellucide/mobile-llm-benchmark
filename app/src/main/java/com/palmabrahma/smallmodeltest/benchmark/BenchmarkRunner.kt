package com.palmabrahma.smallmodeltest.benchmark

import com.palmabrahma.smallmodeltest.metrics.MetricsAggregator
import com.palmabrahma.smallmodeltest.metrics.MetricsCollector
import com.palmabrahma.smallmodeltest.models.BaseModelAdapter
import com.palmabrahma.smallmodeltest.models.ComplexityLevel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import timber.log.Timber
import kotlin.system.measureTimeMillis

/**
 * Runs various benchmark scenarios on LLM models
 */
class BenchmarkRunner(
    private val metricsCollector: MetricsCollector
) {

    /**
     * Test result for a single prompt
     */
    data class PromptResult(
        val prompt: String,
        val responseText: String,
        val firstTokenLatencyMs: Long,
        val totalTimeMs: Long,
        val tokensGenerated: Int,
        val tokensPerSecond: Float,
        val peakMemoryMB: Long,
        val avgCpuPercent: Float,
        val batteryDrainPercent: Float
    )

    /**
     * Complete benchmark results for a model
     */
    data class BenchmarkResults(
        val modelName: String,
        val testScenario: String,
        val startTime: Long,
        val endTime: Long,
        val totalDurationMs: Long,
        val modelLoadTimeMs: Long,
        val promptResults: List<PromptResult>,
        val aggregateMetrics: AggregateMetrics,
        val deviceInfo: DeviceInfo
    )

    data class AggregateMetrics(
        val avgTokensPerSecond: Float,
        val avgFirstTokenLatency: Float,
        val totalTokensGenerated: Int,
        val totalBatteryDrain: Float,
        val peakMemoryMB: Long,
        val avgMemoryMB: Long,
        val avgCpuPercent: Float,
        val maxTemperatureC: Float?
    )

    data class DeviceInfo(
        val model: String = android.os.Build.MODEL,
        val manufacturer: String = android.os.Build.MANUFACTURER,
        val androidVersion: String = android.os.Build.VERSION.RELEASE,
        val sdkVersion: Int = android.os.Build.VERSION.SDK_INT,
        val totalRamMB: Long = 0
    )

    /**
     * Standard test prompts for benchmarking
     */
    object TestPrompts {
        val trivial = listOf(
            "Hi",
            "Hello",
            "Thanks",
            "Yes",
            "No"
        )

        val simple = listOf(
            "What is 2+2?",
            "What's the capital of France?",
            "Tell me a joke",
            "What day is it?",
            "How are you?"
        )

        val medium = listOf(
            "Explain photosynthesis in simple terms",
            "Write a haiku about coding",
            "What are the benefits of exercise?",
            "How does a car engine work?",
            "Summarize the plot of Romeo and Juliet"
        )

        val complex = listOf(
            "Write a Python function to find prime numbers",
            "Explain the theory of relativity",
            "Analyze the causes of World War I",
            "Design a REST API for a todo app",
            "Compare socialism and capitalism"
        )

        val coding = listOf(
            "Write a function to reverse a string",
            "Implement binary search in Python",
            "Create a React component for a button",
            "Write SQL to find duplicate records",
            "Debug this code: def factorial(n): return n * factorial(n)"
        )
    }

    /**
     * Run basic performance test
     */
    suspend fun runBasicPerformanceTest(
        model: BaseModelAdapter,
        prompts: List<String> = TestPrompts.simple
    ): BenchmarkResults = coroutineScope {

        Timber.d("Starting basic performance test for ${model.modelName}")

        val startTime = System.currentTimeMillis()
        val aggregator = MetricsAggregator()
        val promptResults = mutableListOf<PromptResult>()

        // Start metrics collection
        val metricsJob = launch {
            metricsCollector.startCollection(500).collect { metric ->
                aggregator.addMetric(metric)
            }
        }

        // Initialize model and measure load time
        val modelLoadTime = model.initialize()
        Timber.d("Model loaded in ${modelLoadTime}ms")

        // Test each prompt
        for (prompt in prompts) {
            Timber.d("Testing prompt: ${prompt.take(50)}...")

            val startMetrics = metricsCollector.collectCurrentMetrics()
            val promptStartTime = System.currentTimeMillis()
            var firstTokenTime = 0L
            var tokensGenerated = 0
            val responseBuilder = StringBuilder()

            try {
                // Generate response
                model.generateTokens(prompt, maxTokens = 50).collect { result ->
                    if (firstTokenTime == 0L) {
                        firstTokenTime = System.currentTimeMillis() - promptStartTime
                    }
                    responseBuilder.append(result.token)
                    tokensGenerated++

                    if (result.isComplete) {
                        return@collect
                    }
                }

                val totalTime = System.currentTimeMillis() - promptStartTime
                val endMetrics = metricsCollector.collectCurrentMetrics()

                promptResults.add(PromptResult(
                    prompt = prompt,
                    responseText = responseBuilder.toString(),
                    firstTokenLatencyMs = firstTokenTime,
                    totalTimeMs = totalTime,
                    tokensGenerated = tokensGenerated,
                    tokensPerSecond = (tokensGenerated * 1000f) / totalTime,
                    peakMemoryMB = aggregator.getPeakMemory(),
                    avgCpuPercent = aggregator.getAverageCpu(),
                    batteryDrainPercent = startMetrics.batteryLevel - endMetrics.batteryLevel
                ))

            } catch (e: Exception) {
                Timber.e(e, "Error testing prompt: $prompt")
            }

            // Brief pause between prompts
            delay(1000)
        }

        // Stop metrics collection
        metricsJob.cancel()

        // Clean up model
        model.cleanup()

        val endTime = System.currentTimeMillis()

        // Calculate aggregate metrics
        val aggregateMetrics = AggregateMetrics(
            avgTokensPerSecond = promptResults.map { it.tokensPerSecond }.average().toFloat(),
            avgFirstTokenLatency = promptResults.map { it.firstTokenLatencyMs }.average().toFloat(),
            totalTokensGenerated = promptResults.sumOf { it.tokensGenerated },
            totalBatteryDrain = aggregator.getTotalBatteryDrain(),
            peakMemoryMB = aggregator.getPeakMemory(),
            avgMemoryMB = 0L, // TODO: Calculate average
            avgCpuPercent = aggregator.getAverageCpu(),
            maxTemperatureC = aggregator.getAverageTemperature()
        )

        BenchmarkResults(
            modelName = model.modelName,
            testScenario = "Basic Performance",
            startTime = startTime,
            endTime = endTime,
            totalDurationMs = endTime - startTime,
            modelLoadTimeMs = modelLoadTime,
            promptResults = promptResults,
            aggregateMetrics = aggregateMetrics,
            deviceInfo = DeviceInfo()
        )
    }

    /**
     * Run sustained conversation test
     */
    suspend fun runSustainedConversationTest(
        model: BaseModelAdapter,
        durationMinutes: Int = 30,
        onMetricsUpdate: ((tokensPerSecond: Float) -> Unit)? = null
    ): BenchmarkResults = coroutineScope {

        Timber.d("Starting sustained conversation test for ${model.modelName}")

        val conversation = listOf(
            "Tell me about artificial intelligence",
            "What are the main types of machine learning?",
            "Can you explain neural networks?",
            "How does deep learning differ from traditional ML?",
            "What are some real-world applications?",
            "What are the ethical concerns?",
            "How might AI evolve in the future?",
            "What should someone learn to get started with AI?",
            "What programming languages are most important?",
            "Can you recommend some resources?"
        )

        val startTime = System.currentTimeMillis()
        val endTime = startTime + (durationMinutes * 60 * 1000)
        val promptResults = mutableListOf<PromptResult>()
        val aggregator = MetricsAggregator()

        // Start metrics collection
        val metricsJob = launch {
            metricsCollector.startCollection(1000).collect { metric ->
                aggregator.addMetric(metric)
            }
        }

        // Initialize model
        val modelLoadTime = model.initialize()

        var conversationIndex = 0

        // Run conversation for specified duration
        while (System.currentTimeMillis() < endTime) {
            val prompt = conversation[conversationIndex % conversation.size]

            val startMetrics = metricsCollector.collectCurrentMetrics()
            val promptStartTime = System.currentTimeMillis()
            var firstTokenTime = 0L
            var tokensGenerated = 0
            val responseBuilder = StringBuilder()

            // Generate response with longer max tokens for conversation
            model.generateTokens(prompt, maxTokens = 100).collect { result ->
                if (firstTokenTime == 0L) {
                    firstTokenTime = System.currentTimeMillis() - promptStartTime
                }
                responseBuilder.append(result.token)
                tokensGenerated++

                if (result.isComplete || tokensGenerated >= 100) {
                    return@collect
                }
            }

            val totalTime = System.currentTimeMillis() - promptStartTime
            val endMetrics = metricsCollector.collectCurrentMetrics()

            val tokensPerSec = (tokensGenerated * 1000f) / totalTime

            promptResults.add(PromptResult(
                prompt = prompt,
                responseText = responseBuilder.toString(),
                firstTokenLatencyMs = firstTokenTime,
                totalTimeMs = totalTime,
                tokensGenerated = tokensGenerated,
                tokensPerSecond = tokensPerSec,
                peakMemoryMB = aggregator.getPeakMemory(),
                avgCpuPercent = aggregator.getAverageCpu(),
                batteryDrainPercent = startMetrics.batteryLevel - endMetrics.batteryLevel
            ))

            // Report metrics to callback
            onMetricsUpdate?.invoke(tokensPerSec)

            conversationIndex++

            // Pause between messages
            delay(2000)
        }

        metricsJob.cancel()
        model.cleanup()

        val actualEndTime = System.currentTimeMillis()

        val aggregateMetrics = AggregateMetrics(
            avgTokensPerSecond = promptResults.map { it.tokensPerSecond }.average().toFloat(),
            avgFirstTokenLatency = promptResults.map { it.firstTokenLatencyMs }.average().toFloat(),
            totalTokensGenerated = promptResults.sumOf { it.tokensGenerated },
            totalBatteryDrain = aggregator.getTotalBatteryDrain(),
            peakMemoryMB = aggregator.getPeakMemory(),
            avgMemoryMB = 0L,
            avgCpuPercent = aggregator.getAverageCpu(),
            maxTemperatureC = aggregator.getAverageTemperature()
        )

        BenchmarkResults(
            modelName = model.modelName,
            testScenario = "Sustained Conversation",
            startTime = startTime,
            endTime = actualEndTime,
            totalDurationMs = actualEndTime - startTime,
            modelLoadTimeMs = modelLoadTime,
            promptResults = promptResults,
            aggregateMetrics = aggregateMetrics,
            deviceInfo = DeviceInfo()
        )
    }

    /**
     * Run routing classification test
     */
    suspend fun runRoutingClassificationTest(
        model: BaseModelAdapter
    ): BenchmarkResults = coroutineScope {

        Timber.d("Starting routing classification test for ${model.modelName}")

        val testPrompts = mapOf(
            ComplexityLevel.TRIVIAL to TestPrompts.trivial,
            ComplexityLevel.SIMPLE to TestPrompts.simple,
            ComplexityLevel.MEDIUM to TestPrompts.medium,
            ComplexityLevel.COMPLEX to TestPrompts.complex
        )

        val startTime = System.currentTimeMillis()
        val promptResults = mutableListOf<PromptResult>()

        // Initialize model
        val modelLoadTime = model.initialize()

        var correctClassifications = 0
        var totalClassifications = 0

        for ((expectedLevel, prompts) in testPrompts) {
            for (prompt in prompts) {
                val classificationTime = measureTimeMillis {
                    val complexity = model.classifyComplexity(prompt)
                    val predictedLevel = when {
                        complexity < 0.2 -> ComplexityLevel.TRIVIAL
                        complexity < 0.4 -> ComplexityLevel.SIMPLE
                        complexity < 0.6 -> ComplexityLevel.MEDIUM
                        complexity < 0.8 -> ComplexityLevel.COMPLEX
                        else -> ComplexityLevel.EXPERT
                    }

                    if (predictedLevel == expectedLevel) {
                        correctClassifications++
                    }
                    totalClassifications++
                }

                // Record as minimal prompt result
                promptResults.add(PromptResult(
                    prompt = prompt,
                    responseText = expectedLevel.name,
                    firstTokenLatencyMs = classificationTime,
                    totalTimeMs = classificationTime,
                    tokensGenerated = 0,
                    tokensPerSecond = 0f,
                    peakMemoryMB = 0,
                    avgCpuPercent = 0f,
                    batteryDrainPercent = 0f
                ))
            }
        }

        model.cleanup()

        val endTime = System.currentTimeMillis()
        val accuracy = correctClassifications.toFloat() / totalClassifications

        Timber.d("Routing accuracy: ${accuracy * 100}%")

        val aggregateMetrics = AggregateMetrics(
            avgTokensPerSecond = 0f,
            avgFirstTokenLatency = promptResults.map { it.firstTokenLatencyMs }.average().toFloat(),
            totalTokensGenerated = 0,
            totalBatteryDrain = 0f,
            peakMemoryMB = 0,
            avgMemoryMB = 0,
            avgCpuPercent = 0f,
            maxTemperatureC = null
        )

        BenchmarkResults(
            modelName = model.modelName,
            testScenario = "Routing Classification (Accuracy: ${(accuracy * 100).toInt()}%)",
            startTime = startTime,
            endTime = endTime,
            totalDurationMs = endTime - startTime,
            modelLoadTimeMs = modelLoadTime,
            promptResults = promptResults,
            aggregateMetrics = aggregateMetrics,
            deviceInfo = DeviceInfo()
        )
    }
}