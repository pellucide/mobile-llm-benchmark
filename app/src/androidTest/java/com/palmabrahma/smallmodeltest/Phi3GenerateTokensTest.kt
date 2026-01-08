package com.palmabrahma.smallmodeltest

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.palmabrahma.smallmodeltest.models.ModelManager
import com.palmabrahma.smallmodeltest.models.ModelType
import com.palmabrahma.smallmodeltest.models.Phi3MiniAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

/**
 * Instrumented test for Phi3MiniAdapter.generateTokens()
 * Runs on actual device to test ONNX model inference
 */
@RunWith(AndroidJUnit4::class)
class Phi3GenerateTokensTest {

    private lateinit var adapter: Phi3MiniAdapter
    private lateinit var modelManager: ModelManager
    private val testTimeoutSeconds = 300L // 5 minutes max per test

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .build()

    @Before
    fun setUp() = runBlocking {
        // Initialize Timber for logging
        if (Timber.treeCount == 0) {
            Timber.plant(Timber.DebugTree())
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        modelManager = ModelManager(context)
        adapter = Phi3MiniAdapter(context)

        // Check if model is downloaded, if not, download it
        if (!modelManager.isModelDownloaded(ModelType.PHI3_MINI)) {
            Timber.d("Model not downloaded. Starting download...")
            downloadModelIfNeeded()
        }

        // Verify model files exist before testing
        val modelDir = modelManager.getModelDirectory(ModelType.PHI3_MINI)
        val modelFile = modelManager.getModelFilePath(ModelType.PHI3_MINI)

        assertTrue("Model file should exist: ${modelFile.absolutePath}", modelFile.exists())

        Timber.d("Model files verified. Initializing adapter...")
    }

    @After
    fun tearDown() = runBlocking {
        try {
            adapter.cleanup()
        } catch (e: Exception) {
            Timber.e(e, "Error cleaning up adapter")
        }
    }

    @Test
    fun testInitializeAndIsReady() = runBlocking {
        val loadTime = adapter.initialize()

        Timber.d("Model loaded in ${loadTime}ms")
        assertTrue("Model should load in reasonable time (< 60 seconds)", loadTime < 60000)
        assertTrue("Model should be ready after initialization", adapter.isReady())
    }

    @Test
    fun testGenerateTokens_SimplePrompt() = runBlocking {
        adapter.initialize()

        val prompt = "Hello"
        val maxTokens = 10
        val temperature = 0.7f

        val startTime = measureTimeMillis {
            val results = adapter.generateTokens(prompt, maxTokens, temperature).toList()

            assertTrue("Should generate at least one token", results.isNotEmpty())
            // results includes final completion signal, so actual tokens = size - 1
            val actualTokenCount = results.count { !it.isComplete }
            assertTrue("Should not exceed max tokens", actualTokenCount <= maxTokens)

            // Validate result structure
            results.forEach { result ->
                assertNotNull("Token should not be null", result.token)
                assertNotNull("Token ID should not be null", result.tokenId)
                assertTrue("Timestamp should be positive", result.timestamp >= 0)
            }

            val lastResult = results.last()
            assertTrue("Last result should be marked complete", lastResult.isComplete)

            val fullText = results.joinToString("") { it.token }
            Timber.d("Generated $actualTokenCount tokens: '$fullText'")
        }

        Timber.d("Simple prompt test completed in ${startTime}ms")
    }

    @Test
    fun testGenerateTokens_QuestionPrompt() = runBlocking {
        adapter.initialize()

        val prompt = "What is the capital of France?"
        val maxTokens = 20
        val temperature = 0.3f // Lower temperature for more deterministic answers

        val results = adapter.generateTokens(prompt, maxTokens, temperature).toList()

        assertTrue("Should generate tokens for question", results.isNotEmpty())

        val response = results.joinToString("") { it.token }
        Timber.d("Question: $prompt")
        Timber.d("Response: $response")

        // Check that response contains something meaningful
        assertTrue("Response should contain letters", response.any { it.isLetter() })
    }

    @Test
    fun testGenerateTokens_EmptyPrompt() = runBlocking {
        adapter.initialize()

        val prompt = ""
        val maxTokens = 5
        val temperature = 0.7f

        try {
            val results = adapter.generateTokens(prompt, maxTokens, temperature).toList()
            val actualTokenCount = results.count { !it.isComplete }
            Timber.d("Empty prompt generated $actualTokenCount tokens")
        } catch (e: Exception) {
            Timber.d("Empty prompt threw (expected): ${e.message}")
            // Empty prompt might fail, which is acceptable
        }
    }

    @Test
    fun testGenerateTokens_MaxTokensLimit() = runBlocking {
        adapter.initialize()

        val prompt = "Tell me a long story about"
        val maxTokens = 5
        val temperature = 0.7f

        val results = adapter.generateTokens(prompt, maxTokens, temperature).toList()

        // Actual token count excludes the final completion signal
        val actualTokenCount = results.count { !it.isComplete }

        assertTrue("Should not exceed max tokens", actualTokenCount <= maxTokens)

        // Last result should always be marked complete
        val lastResult = results.lastOrNull()
        assertTrue("Last result should be marked complete", lastResult?.isComplete == true)
    }

    @Test
    fun testGenerateTokens_TemperatureVariation() = runBlocking {
        adapter.initialize()

        val prompt = "The weather is"
        val maxTokens = 10

        // Test with low temperature (more deterministic)
        val lowTempResults = adapter.generateTokens(prompt, maxTokens, 0.1f).toList()
        val lowTempResponse = lowTempResults.joinToString("") { it.token }

        // Test with high temperature (more random)
        val highTempResults = adapter.generateTokens(prompt, maxTokens, 1.0f).toList()
        val highTempResponse = highTempResults.joinToString("") { it.token }

        Timber.d("Low temp response: $lowTempResponse")
        Timber.d("High temp response: $highTempResponse")

        assertTrue("Both should generate tokens", lowTempResults.isNotEmpty() && highTempResults.isNotEmpty())
    }

    @Test
    fun testGenerateTokens_MultipleSequentialCalls() = runBlocking {
        adapter.initialize()

        val prompts = listOf(
            "Hello",
            "How are you?",
            "What is 2+2?"
        )

        for (prompt in prompts) {
            val results = adapter.generateTokens(prompt, 5, 0.7f).toList()
            assertTrue("Should generate tokens for: $prompt", results.isNotEmpty())
            Timber.d("Prompt '$prompt' -> '${results.joinToString("") { it.token }}'")
        }
    }

    @Test
    fun testGenerateTokens_TokensPerSecond() = runBlocking {
        adapter.initialize()

        val prompt = "Write a short poem about"
        val maxTokens = 30
        val temperature = 0.7f

        val startTime = System.currentTimeMillis()
        val results = adapter.generateTokens(prompt, maxTokens, temperature).toList()
        val endTime = System.currentTimeMillis()

        val durationMs = endTime - startTime
        // Actual token count excludes the final completion signal
        val actualTokenCount = results.count { !it.isComplete }
        val tokensPerSecond = if (durationMs > 0) {
            (actualTokenCount * 1000.0) / durationMs
        } else {
            0.0
        }

        Timber.d("Generated $actualTokenCount tokens in ${durationMs}ms = ${String.format("%.2f", tokensPerSecond)} tok/s")
        assertTrue("Should generate at least some tokens", results.isNotEmpty())

        // Sanity check: should be faster than 0.1 tokens/second (very generous)
        assertTrue("Generation speed should be reasonable", tokensPerSecond > 0.1)
    }

    @Test
    fun testGenerateTokens_LatencyMeasurement() = runBlocking {
        adapter.initialize()

        val prompt = "Hi"
        val maxTokens = 10
        val temperature = 0.7f

        val results = adapter.generateTokens(prompt, maxTokens, temperature).toList()

        if (results.isNotEmpty()) {
            val firstTokenLatency = results[0].timestamp
            Timber.d("First token latency: ${firstTokenLatency}ms")

            assertTrue("First token latency should be positive", firstTokenLatency > 0)
            assertTrue("First token latency should be reasonable (< 30 seconds)", firstTokenLatency < 30000)
        }
    }

    @Test
    fun testGenerateTokens_SpecialCharacters() = runBlocking {
        adapter.initialize()

        val prompts = listOf(
            "Hello 😊",
            "Test with numbers: 123",
            "Special chars: @#\$%",
            "Newlines\nand\ttabs"
        )

        for (prompt in prompts) {
            try {
                val results = adapter.generateTokens(prompt, 5, 0.7f).toList()
                val response = results.joinToString("") { it.token }
                Timber.d("Prompt '$prompt' -> '$response'")
                assertTrue("Should handle special characters: $prompt", results.isNotEmpty())
            } catch (e: Exception) {
                Timber.w(e, "Failed to process special character prompt: $prompt")
            }
        }
    }

    @Test
    fun testGenerateTokens_NotInitializedThrows() = runBlocking {
        // Don't initialize the adapter
        val prompt = "Hello"

        try {
            adapter.generateTokens(prompt, 5, 0.7f).first()
            fail("Should throw exception when model not initialized")
        } catch (e: IllegalStateException) {
            assertTrue("Error message should mention not initialized",
                e.message?.contains("not initialized", ignoreCase = true) == true
            )
            Timber.d("Correctly threw exception when model not initialized: ${e.message}")
        }
    }

    @Test
    fun testGenerateTokens_LongPrompt() = runBlocking {
        adapter.initialize()

        val longPrompt = "Explain the theory of relativity in simple terms, including both special and general relativity, and their key differences."
        val maxTokens = 50
        val temperature = 0.7f

        val results = adapter.generateTokens(longPrompt, maxTokens, temperature).toList()

        val actualTokenCount = results.count { !it.isComplete }
        assertTrue("Should generate tokens for long prompt", actualTokenCount > 0)
        Timber.d("Long prompt generated $actualTokenCount tokens")
    }

    /**
     * Download model files if not present
     */
    private suspend fun downloadModelIfNeeded() = withContext(Dispatchers.IO) {
        val modelDir = modelManager.getModelDirectory(ModelType.PHI3_MINI)
        modelDir.mkdirs()

        val filesToDownload = listOf(
            "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-onnx/resolve/main/cpu_and_mobile/cpu-int4-rtn-block-32-acc-level-4/phi3-mini-4k-instruct-cpu-int4-rtn-block-32-acc-level-4.onnx" to "phi3-mini-4k-instruct-cpu-int4-rtn-block-32-acc-level-4.onnx",
            "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-onnx/resolve/main/cpu_and_mobile/cpu-int4-rtn-block-32-acc-level-4/phi3-mini-4k-instruct-cpu-int4-rtn-block-32-acc-level-4.onnx.data" to "phi3-mini-4k-instruct-cpu-int4-rtn-block-32-acc-level-4.onnx.data",
            "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-onnx/resolve/main/cpu_and_mobile/cpu-int4-rtn-block-32-acc-level-4/tokenizer.json" to "tokenizer.json",
            "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-onnx/resolve/main/cpu_and_mobile/cpu-int4-rtn-block-32-acc-level-4/tokenizer.model" to "tokenizer.model",
            "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-onnx/resolve/main/cpu_and_mobile/cpu-int4-rtn-block-32-acc-level-4/tokenizer_config.json" to "tokenizer_config.json",
            "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-onnx/resolve/main/cpu_and_mobile/cpu-int4-rtn-block-32-acc-level-4/genai_config.json" to "genai_config.json",
            "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-onnx/resolve/main/cpu_and_mobile/cpu-int4-rtn-block-32-acc-level-4/special_tokens_map.json" to "special_tokens_map.json"
        )

        var downloaded = 0
        var skipped = 0

        for ((url, fileName) in filesToDownload) {
            val file = File(modelDir, fileName)
            if (file.exists()) {
                Timber.d("✓ Already exists: $fileName")
                skipped++
            } else {
                Timber.d("Downloading: $fileName")
                downloadFile(url, file)
                downloaded++
            }
        }

        Timber.d("Download complete: $downloaded new files, $skipped already present")
    }

    private fun downloadFile(url: String, destination: File) {
        val request = Request.Builder().url(url).build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to download: ${response.code}")
            }

            val totalBytes = response.body.contentLength()
            var downloadedBytes = 0L

            response.body.byteStream().use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int

                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                    }
                }
            }

            Timber.d("✓ Downloaded ${destination.name} (${downloadedBytes / (1024 * 1024)}MB)")
        }
    }
}
