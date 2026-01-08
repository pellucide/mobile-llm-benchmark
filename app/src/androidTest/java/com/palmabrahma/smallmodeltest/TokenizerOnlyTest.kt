package com.palmabrahma.smallmodeltest

import ai.onnxruntime.genai.Model
import ai.onnxruntime.genai.Tokenizer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.palmabrahma.smallmodeltest.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Lightweight tokenizer test using ONNX Runtime GenAI Tokenizer
 */
@RunWith(AndroidJUnit4::class)
class TokenizerOnlyTest {

    private lateinit var context: android.content.Context
    private lateinit var modelManager: ModelManager
    private var genaiModel: Model? = null
    private var genaiTokenizer: Tokenizer? = null

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    @Before
    fun setup() = runBlocking {
        context = InstrumentationRegistry.getInstrumentation().targetContext

        // Initialize Timber
        if (Timber.treeCount == 0) {
            Timber.plant(Timber.DebugTree())
        }

        modelManager = ModelManager(context)
        val modelDir = modelManager.getModelDirectory(ModelType.PHI3_MINI)
        val modelFile = modelManager.getModelFilePath(ModelType.PHI3_MINI)

        println("\n=== Tokenizer Test Setup ===")

        // Download model files if not present
        if (!modelFile.exists()) {
            println("Model not found. Downloading...")
            try {
                withContext(Dispatchers.IO) {
                    downloadModelIfNeeded()
                }
                println("✓ Model files downloaded successfully!")
            } catch (e: Exception) {
                println("✗ Failed to download model files: ${e.message}")
                throw e
            }
        } else {
            println("✓ Model files already present")
        }

        // Initialize GenAI Model and Tokenizer
        println("Initializing GenAI Tokenizer...")
        genaiModel = Model(modelFile.parent)
        genaiTokenizer = Tokenizer(genaiModel!!)
        println("✓ Tokenizer initialized")
    }

    private suspend fun downloadModelIfNeeded() = withContext(Dispatchers.IO) {
        val modelDir = modelManager.getModelDirectory(ModelType.PHI3_MINI)
        modelDir.mkdirs()

        val filesToDownload = listOf(
            "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-onnx/resolve/main/cpu_and_mobile/cpu-int4-rtn-block-32-acc-level-4/phi3-mini-4k-instruct-cpu-int4-rtn-block-32-acc-level-4.onnx" to "phi3-mini-4k-instruct-cpu-int4-rtn-block-32-acc-level-4.onnx",
            "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-onnx/resolve/main/cpu_and_mobile/cpu-int4-rtn-block-32-acc-level-4/phi3-mini-4k-instruct-cpu-int4-rtn-block-32-acc-level-4.onnx.data" to "phi3-mini-4k-instruct-cpu-int4-rtn-block-32-acc-level-4.onnx.data",
            "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-onnx/resolve/main/cpu_and_mobile/cpu-int4-rtn-block-32-acc-level-4/tokenizer.json" to "tokenizer.json",
            "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-onnx/resolve/main/cpu_and_mobile/cpu-int4-rtn-block-32-acc-level-4/tokenizer.model" to "tokenizer.model",
            "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-onnx/resolve/main/cpu_and_mobile/cpu-int4-rtn-block-32-acc-level-4/genai_config.json" to "genai_config.json"
        )

        for ((url, fileName) in filesToDownload) {
            val file = File(modelDir, fileName)
            if (!file.exists()) {
                println("  Downloading: $fileName")
                downloadFile(url, file)
            }
        }
    }

    private fun downloadFile(url: String, destination: File) {
        val request = Request.Builder().url(url).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to download: ${response.code}")
            }
            response.body?.byteStream()?.use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                    }
                }
            }
        }
    }

    @Test
    fun testTokenizerBasics() {
        println("\n=== Basic Tokenizer Test ===\n")

        val tokenizer = genaiTokenizer ?: throw IllegalStateException("Tokenizer not initialized")

        val testCases = mapOf(
            "😀 emoji test" to "Emoji handling",
            "Hello world" to "Basic greeting",
            "What is 2+2?" to "Simple math question",
            "Machine learning" to "Technical term",
            "123.456" to "Decimal number"
        )

        for ((text, description) in testCases) {
            val encoded = tokenizer.encode(text)
            val tokens = encoded.getSequence(0).toList()
            val decoded = tokenizer.decode(tokens.toIntArray())

            println("$description: \"$text\"")
            println("  Tokens: ${tokens.size} - [${tokens.take(5).joinToString(", ")}${if (tokens.size > 5) ", ..." else ""}]")
            println("  Decoded: \"$decoded\"")

            // Note: GenAI tokenizer may add leading space, so we check if decoded contains original
            assertTrue("Decoded text should contain original", decoded.contains(text) || text.contains(decoded))
            assertTrue("Should have at least one token", tokens.isNotEmpty())
            println()
        }
    }

    @Test
    fun testTokenizerEfficiency() {
        println("\n=== Tokenizer Efficiency Test ===\n")

        val tokenizer = genaiTokenizer ?: throw IllegalStateException("Tokenizer not initialized")

        val texts = listOf(
            "The quick brown fox jumps over the lazy dog",
            "To be or not to be, that is the question",
            "Machine learning models require training data",
            "Artificial intelligence is transforming technology"
        )

        println("%-50s | Chars | Tokens | Ratio".format("Text"))
        println("-".repeat(75))

        for (text in texts) {
            val tokens = tokenizer.encode(text).getSequence(0).toList()
            val ratio = text.length.toFloat() / tokens.size

            val displayText = if (text.length > 47) text.take(44) + "..." else text
            println("%-50s | %5d | %6d | %.2f".format(
                displayText,
                text.length,
                tokens.size,
                ratio
            ))
        }
    }

    @Test
    fun testSpecialTokens() {
        println("\n=== Special Tokens Test ===\n")

        val tokenizer = genaiTokenizer ?: throw IllegalStateException("Tokenizer not initialized")

        val specialTokens = listOf(
            "<|end|>",
            "<|endoftext|>",
            "<|assistant|>",
            "<|user|>"
        )

        for (token in specialTokens) {
            val encoded = tokenizer.encode(token)
            val tokens = encoded.getSequence(0).toList()
            val decoded = tokenizer.decode(tokens.toIntArray())

            println("Token: \"$token\"")
            println("  Encoded as: ${tokens.size} tokens")
            println("  Decoded: \"$decoded\"")
            println()
        }
    }

    @Test
    fun testConsistency() {
        println("\n=== Consistency Test ===\n")

        val tokenizer = genaiTokenizer ?: throw IllegalStateException("Tokenizer not initialized")

        val text = "The quick brown fox jumps over the lazy dog"
        val results = mutableListOf<List<Int>>()

        // Encode the same text 5 times
        repeat(5) { i ->
            val tokens = tokenizer.encode(text).getSequence(0).toList()
            results.add(tokens)
            println("Run ${i + 1}: ${tokens.size} tokens")
        }

        // Check all are identical
        val allSame = results.all { it == results[0] }
        println("\nConsistency: ${if (allSame) "✓ All runs produced identical tokenization" else "✗ Inconsistent tokenization"}")
        assertTrue("Tokenization should be deterministic", allSame)
    }

    @Test
    fun testEdgeCases() {
        println("\n=== Edge Cases Test ===\n")

        val tokenizer = genaiTokenizer ?: throw IllegalStateException("Tokenizer not initialized")

        val edgeCases = mapOf(
            "" to "Empty string",
            " " to "Single space",
            "\n" to "Newline",
            "😀" to "Emoji",
            "🚀" to "Rocket emoji",
            "a".repeat(100) to "100 chars"
        )

        for ((input, description) in edgeCases) {
            try {
                val tokens = tokenizer.encode(input).getSequence(0).toList()
                val decoded = tokenizer.decode(tokens.toIntArray())

                val display = when {
                    input.isEmpty() -> "<empty>"
                    input.isBlank() -> "<blank(${input.length})>"
                    input.length > 20 -> "${input.take(17)}..."
                    else -> input
                }

                println("$description: \"$display\"")
                println("  → ${tokens.size} tokens, decoded: \"${decoded.take(50)}\"")

            } catch (e: Exception) {
                println("$description: Error - ${e.message}")
            }
        }
    }

    @Test
    fun testComplexityPrompts() {
        println("\n=== Complexity Classification Prompts ===\n")

        val tokenizer = genaiTokenizer ?: throw IllegalStateException("Tokenizer not initialized")

        val prompts = listOf(
            "Hi" to "TRIVIAL",
            "What is 2+2?" to "SIMPLE",
            "Explain photosynthesis" to "MEDIUM",
            "Write a Python function to find primes" to "COMPLEX"
        )

        println("%-40s | Expected | Tokens".format("Prompt"))
        println("-".repeat(65))

        for ((prompt, level) in prompts) {
            val fullPrompt = """
                Classify the complexity of this prompt on a scale of 0-1:
                "$prompt"

                Respond with only a number between 0 and 1.
            """.trimIndent()

            val tokens = tokenizer.encode(fullPrompt).getSequence(0).toList()
            val truncated = if (prompt.length > 37) prompt.take(34) + "..." else prompt

            println("%-40s | %-8s | %d".format(truncated, level, tokens.size))
        }
    }
}
