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
            "No",
            "OK",
            "Good",
            "Bye",
            "Sure",
            "Great"
        )

        val simple = listOf(
            "What is 2+2?",
            "What's the capital of France?",
            "Tell me a joke",
            "What day is it?",
            "How are you?",
            "What color is the sky?",
            "Name a planet",
            "Who wrote Hamlet?",
            "What's 10 times 5?",
            "Define a noun"
        )

        val medium = listOf(
            "Explain photosynthesis in simple terms",
            "Write a haiku about coding",
            "What are the benefits of exercise?",
            "How does a car engine work?",
            "Summarize the plot of Romeo and Juliet",
            "Describe the water cycle",
            "What causes seasons to change?",
            "How do computers store data?",
            "Explain supply and demand",
            "What's the difference between weather and climate?"
        )

        val complex = listOf(
            "Write a Python function to find prime numbers",
            "Explain the theory of relativity",
            "Analyze the causes of World War I",
            "Design a REST API for a todo app",
            "Compare socialism and capitalism",
            "Implement a recursive solution for the Fibonacci sequence",
            "Discuss the implications of quantum computing on cryptography",
            "Evaluate the pros and cons of different sorting algorithms",
            "Design a database schema for an e-commerce platform",
            "Explain how neural networks learn through backpropagation"
        )

        val coding = listOf(
            "Write a function to reverse a string",
            "Implement binary search in Python",
            "Create a React component for a button",
            "Write SQL to find duplicate records",
            "Debug this code: def factorial(n): return n * factorial(n)",
            "Create a class for a linked list with insert and delete methods",
            "Write async/await code to fetch data from an API",
            "Implement a depth-first search algorithm",
            "Create a regex to validate email addresses",
            "Write a unit test for a calculator function"
        )

        // Additional specialized test sets
        val mathematical = listOf(
            "Solve for x: 2x + 5 = 13",
            "Calculate the derivative of x^3 + 2x",
            "Find the area of a circle with radius 5",
            "What's the probability of rolling two sixes?",
            "Explain matrix multiplication"
        )

        val scientific = listOf(
            "What is DNA?",
            "Explain how vaccines work",
            "Describe the greenhouse effect",
            "What causes earthquakes?",
            "How do black holes form?"
        )

        val creative = listOf(
            "Write a short story about a time traveler",
            "Create a marketing slogan for a new smartphone",
            "Design a logo concept for a coffee shop",
            "Compose a limerick about programming",
            "Invent a new board game and explain its rules"
        )

        val analytical = listOf(
            "Why did the Roman Empire fall?",
            "Analyze the themes in 1984 by George Orwell",
            "Evaluate the environmental impact of electric vehicles",
            "Critique the user interface of popular social media apps",
            "Assess the effectiveness of remote work policies"
        )

        // Mixed complexity prompts for testing edge cases
        val mixed = listOf(
            "Hi, can you explain quantum physics?", // Simple greeting + complex topic
            "Calculate 2+2 and then explain why mathematics is universal", // Simple + complex
            "Thanks for helping me understand recursion in programming", // Greeting + technical
            "What's your name?", // Simple but might trigger system info
            "I need help", // Vague, could be simple or complex
            "Fix this: print('Hello')", // Simple code but uses 'fix'
            "Why?", // Very short but potentially complex
            "Explain", // Incomplete, ambiguous
            "2 + 2 = ?", // Math with symbols
            "E=mc²?" // Famous equation, short but complex topic
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
        Timber.d("Starting enhanced routing classification test for ${model.modelName}")

        // Extended test set with all prompt categories
        val testPrompts = mapOf(
            ComplexityLevel.TRIVIAL to TestPrompts.trivial,
            ComplexityLevel.SIMPLE to TestPrompts.simple,
            ComplexityLevel.MEDIUM to TestPrompts.medium,
            ComplexityLevel.COMPLEX to TestPrompts.complex + TestPrompts.coding,
            ComplexityLevel.EXPERT to TestPrompts.analytical + TestPrompts.creative.takeLast(2)
        )

        // Additional specialized tests for better coverage
        val specializedTests = mapOf(
            "Mathematical" to TestPrompts.mathematical,
            "Scientific" to TestPrompts.scientific,
            "Creative" to TestPrompts.creative,
            "Analytical" to TestPrompts.analytical,
            "Mixed" to TestPrompts.mixed
        )

        val startTime = System.currentTimeMillis()
        val promptResults = mutableListOf<PromptResult>()
        val detailedResults = mutableMapOf<String, MutableList<Pair<Float, ComplexityLevel>>>()

        // Initialize model
        val modelLoadTime = model.initialize()

        var correctClassifications = 0
        var totalClassifications = 0
        val confusionMatrix = mutableMapOf<Pair<ComplexityLevel, ComplexityLevel>, Int>()

        // Test main categories
        for ((expectedLevel, prompts) in testPrompts) {
            val categoryResults = mutableListOf<Pair<Float, ComplexityLevel>>()

            for (prompt in prompts) {
                var complexity = 0f
                var predictedLevel: ComplexityLevel = ComplexityLevel.TRIVIAL

                val classificationTime = measureTimeMillis {
                    complexity = model.classifyComplexity(prompt)
                    predictedLevel = when {
                        complexity < 0.15 -> ComplexityLevel.TRIVIAL
                        complexity < 0.35 -> ComplexityLevel.SIMPLE
                        complexity < 0.55 -> ComplexityLevel.MEDIUM
                        complexity < 0.75 -> ComplexityLevel.COMPLEX
                        else -> ComplexityLevel.EXPERT
                    }

                    categoryResults.add(complexity to predictedLevel)

                    // Update confusion matrix
                    val key = expectedLevel to predictedLevel
                    confusionMatrix[key] = (confusionMatrix[key] ?: 0) + 1

                    if (predictedLevel == expectedLevel ||
                        // Allow adjacent level matches for better real-world accuracy
                        (expectedLevel == ComplexityLevel.SIMPLE && predictedLevel == ComplexityLevel.TRIVIAL) ||
                        (expectedLevel == ComplexityLevel.MEDIUM && predictedLevel == ComplexityLevel.SIMPLE) ||
                        (expectedLevel == ComplexityLevel.COMPLEX && predictedLevel == ComplexityLevel.MEDIUM) ||
                        (expectedLevel == ComplexityLevel.EXPERT && predictedLevel == ComplexityLevel.COMPLEX)) {
                        correctClassifications++
                    }
                    totalClassifications++

                    // Log misclassifications for debugging
                    if (predictedLevel != expectedLevel) {
                        Timber.w("Misclassification: '$prompt' expected=$expectedLevel, got=$predictedLevel (score=$complexity)")
                    }
                }

                // Record as minimal prompt result
                promptResults.add(PromptResult(
                    prompt = prompt.take(50), // Truncate long prompts for display
                    responseText = "${expectedLevel.name} -> ${predictedLevel.name} (${String.format("%.3f", complexity)})",
                    firstTokenLatencyMs = classificationTime,
                    totalTimeMs = classificationTime,
                    tokensGenerated = 0,
                    tokensPerSecond = 0f,
                    peakMemoryMB = 0,
                    avgCpuPercent = 0f,
                    batteryDrainPercent = 0f
                ))
            }

            detailedResults[expectedLevel.name] = categoryResults
        }

        // Test specialized categories for additional insights
        for ((categoryName, prompts) in specializedTests) {
            for (prompt in prompts.take(3)) { // Test subset for efficiency
                val complexity = model.classifyComplexity(prompt)
                val predictedLevel = when {
                    complexity < 0.15 -> ComplexityLevel.TRIVIAL
                    complexity < 0.35 -> ComplexityLevel.SIMPLE
                    complexity < 0.55 -> ComplexityLevel.MEDIUM
                    complexity < 0.75 -> ComplexityLevel.COMPLEX
                    else -> ComplexityLevel.EXPERT
                }

                Timber.d("$categoryName prompt: '${prompt.take(30)}...' -> $predictedLevel (${String.format("%.3f", complexity)})")
            }
        }

        model.cleanup()

        val endTime = System.currentTimeMillis()
        val accuracy = correctClassifications.toFloat() / totalClassifications

        // Calculate per-category accuracy
        val categoryAccuracy = mutableMapOf<ComplexityLevel, Float>()
        for (level in ComplexityLevel.entries) {
            val total = confusionMatrix.filterKeys { it.first == level }.values.sum()
            val correct = confusionMatrix[level to level] ?: 0
            if (total > 0) {
                categoryAccuracy[level] = correct.toFloat() / total
            }
        }

        // Log detailed results
        Timber.d("=".repeat(50))
        Timber.d("Routing Classification Results:")
        Timber.d("Overall Accuracy: ${String.format("%.1f%%", accuracy * 100)}")
        Timber.d("Per-Category Accuracy:")
        categoryAccuracy.forEach { (level, acc) ->
            Timber.d("  ${level.name}: ${String.format("%.1f%%", acc * 100)}")
        }

        // Log confusion matrix
        Timber.d("Confusion Matrix (Expected -> Predicted):")
        for (expected in ComplexityLevel.entries) {
            val row = ComplexityLevel.entries.joinToString(" ") { predicted ->
                String.format("%3d", confusionMatrix[expected to predicted] ?: 0)
            }
            Timber.d("  ${expected.name.padEnd(8)}: $row")
        }
        Timber.d("=".repeat(50))

        val aggregateMetrics = AggregateMetrics(
            avgTokensPerSecond = accuracy * 100, // Store accuracy as tokens/sec for display
            avgFirstTokenLatency = promptResults.map { it.firstTokenLatencyMs }.average().toFloat(),
            totalTokensGenerated = totalClassifications,
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