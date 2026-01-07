package com.palmabrahma.smallmodeltest

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
 * Lightweight tokenizer test that only downloads tokenizer files
 * Skips the large model weights (~1.6GB) for faster testing
 */
@RunWith(AndroidJUnit4::class)
class TokenizerOnlyTest {

    private lateinit var context: android.content.Context
    private lateinit var modelManager: ModelManager
    private lateinit var tokenizer: Phi3Tokenizer
    private lateinit var tokenizerFile: File
    private lateinit var tokenizerModelFile: File

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

        // Define tokenizer file paths
        tokenizerFile = File(modelDir, ModelManager.PHI3_TOKENIZER_NAME)
        tokenizerModelFile = File(modelDir, ModelManager.PHI3_TOKENIZER_MODEL_NAME)

        println("\n=== Tokenizer-Only Test Setup ===")

        // Download only tokenizer files if not present
        if (!tokenizerFile.exists() || !tokenizerModelFile.exists()) {
            println("Downloading tokenizer files (lightweight ~3MB total)...")

            try {
                withContext(Dispatchers.IO) {
                    // Download tokenizer.json
                    if (!tokenizerFile.exists()) {
                        println("Downloading tokenizer.json...")
                        downloadFile(
                            url = ModelManager.PHI3_TOKENIZER_URL,
                            destination = tokenizerFile,
                            description = "tokenizer.json"
                        )
                    }

                    // Download tokenizer.model
                    if (!tokenizerModelFile.exists()) {
                        println("Downloading tokenizer.model...")
                        downloadFile(
                            url = ModelManager.PHI3_TOKENIZER_MODEL_URL,
                            destination = tokenizerModelFile,
                            description = "tokenizer.model"
                        )
                    }
                }
                println("✓ Tokenizer files downloaded successfully!")
            } catch (e: Exception) {
                println("✗ Failed to download tokenizer files: ${e.message}")
                println("Tests will run with fallback tokenizer")
            }
        } else {
            println("✓ Tokenizer files already present")
        }

        // Initialize tokenizer
        tokenizer = Phi3Tokenizer(context, tokenizerFile.absolutePath)

        if (tokenizerFile.exists()) {
            println("Using real tokenizer:")
            println("  - tokenizer.json: ${tokenizerFile.length() / 1024}KB")
            println("  - tokenizer.model: ${if (tokenizerModelFile.exists()) "${tokenizerModelFile.length() / 1024}KB" else "not found"}")
        } else {
            println("Using fallback vocabulary")
        }
    }

    private suspend fun downloadFile(url: String, destination: File, description: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to download $description: ${response.code}")
            }

            val totalBytes = response.body?.contentLength() ?: -1
            var downloadedBytes = 0L
            var lastProgress = 0

            response.body?.byteStream()?.use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int

                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read

                        if (totalBytes > 0) {
                            val progress = ((downloadedBytes * 100) / totalBytes).toInt()
                            if (progress - lastProgress >= 10) {
                                println("  $description: $progress% (${downloadedBytes / 1024}KB / ${totalBytes / 1024}KB)")
                                lastProgress = progress
                            }
                        }
                    }
                }
            }

            println("  ✓ $description downloaded (${destination.length() / 1024}KB)")
        }
    }

    @Test
    fun testTokenizerBasics() {
        println("\n=== Basic Tokenizer Test ===\n")

        val testCases = mapOf(
            "😀 emoji test" to "Emoji handling",
            "Hello world" to "Basic greeting",
            "What is 2+2?" to "Simple math question",
            "Machine learning" to "Technical term",
            "CamelCase" to "Camel case",
            "snake_case" to "Snake case",
            "123.456" to "Decimal number",
            "user@example.com" to "Email",
            "https://example.com" to "URL"
        )

        for ((text, description) in testCases) {
            val tokens = tokenizer.encode(text)
            val decoded = tokenizer.decode(tokens)

            println("$description: \"$text\"")
            println("  Tokens: ${tokens.size} - [${tokens.take(5).joinToString(", ")}${if (tokens.size > 5) ", ..." else ""}]")
            println("  Decoded: \"$decoded\"")

            // Basic validation
            assertEquals("Original text should macthc decoded test", text, decoded)
            assertTrue("Should have at least BOS token", tokens.isNotEmpty())
            assertEquals("First token should be BOS", tokenizer.bosTokenId, tokens[0])
            println()
        }
    }

    @Test
    fun testTokenizerEfficiency() {
        println("\n=== Tokenizer Efficiency Test ===\n")

        val texts = listOf(
            "The quick brown fox jumps over the lazy dog",
            "To be or not to be, that is the question",
            "Machine learning models require training data",
            "Artificial intelligence is transforming technology",
            "Write a Python function to calculate fibonacci numbers"
        )

        var totalChars = 0
        var totalTokens = 0

        println("%-50s | Chars | Tokens | Ratio".format("Text"))
        println("-".repeat(75))

        for (text in texts) {
            val tokens = tokenizer.encode(text)
            val ratio = text.length.toFloat() / tokens.size

            totalChars += text.length
            totalTokens += tokens.size

            val displayText = if (text.length > 47) text.take(44) + "..." else text
            println("%-50s | %5d | %6d | %.2f".format(
                displayText,
                text.length,
                tokens.size,
                ratio
            ))
        }

        val avgRatio = totalChars.toFloat() / totalTokens
        println("-".repeat(75))
        println("Average compression ratio: %.2f characters per token".format(avgRatio))

        // With real tokenizer, expect reasonable compression
        if (tokenizerFile.exists()) {
            assertTrue(
                "Real tokenizer should achieve at least 3 chars/token average",
                avgRatio >= 3.0f
            )
        }
    }

    @Test
    fun testSpecialTokens() {
        println("\n=== Special Tokens Test ===\n")

        val specialTokens = mapOf(
            "<s>" to "BOS",
            "</s>" to "EOS",
            "<|endoftext|>" to "End of text",
            "<|system|>" to "System",
            "<|user|>" to "User",
            "<|assistant|>" to "Assistant",
            "<|end|>" to "End marker"
        )

        for ((token, name) in specialTokens) {
            val tokens = tokenizer.encode(token)
            val decoded = tokenizer.decode(tokens)

            val isSpecial = tokens.size == 2 // BOS + single special token
            println("$name token: \"$token\"")
            println("  Encoded as: ${tokens.joinToString(", ")}")
            println("  Recognized as special: ${if (isSpecial) "✓" else "✗"}")
            println()
        }
    }

    @Test
    fun testPromptTemplates() {
        println("\n=== Prompt Template Test ===\n")

        // Test Phi-3 specific prompt format
        val templates = listOf(
            "<|system|>\nYou are a helpful assistant.\n<|end|>\n<|user|>\nHello!\n<|end|>\n<|assistant|>",
            "### Instruction:\nWrite a function\n\n### Response:",
            "Q: What is AI?\nA:",
            "[INST] Explain machine learning [/INST]"
        )

        for ((i, template) in templates.withIndex()) {
            val tokens = tokenizer.encode(template)
            println("Template ${i + 1}:")
            println("  Length: ${template.length} chars → ${tokens.size} tokens")
            println("  Compression: %.2fx".format(template.length.toFloat() / tokens.size))
            println("  First tokens: [${tokens.take(10).joinToString(", ")}...]")
            println()
        }
    }

    @Test
    fun testConsistency() {
        println("\n=== Consistency Test ===\n")

        val text = "The quick brown fox jumps over the lazy dog"
        val results = mutableListOf<List<Int>>()

        // Encode the same text 5 times
        repeat(5) { i ->
            val tokens = tokenizer.encode(text)
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

        val edgeCases = mapOf(
            "" to "Empty string",
            " " to "Single space",
            "\n" to "Newline",
            "\t" to "Tab",
            "   " to "Multiple spaces",
            "!!!" to "Repeated punctuation",
            "..." to "Ellipsis",
            "→" to "Arrow",
            "π" to "Pi symbol",
            "🚀" to "Rocket emoji",
            "a".repeat(100) to "100 chars",
            "word ".repeat(50) to "50 words"
        )

        for ((input, description) in edgeCases) {
            try {
                val tokens = tokenizer.encode(input)
                val decoded = tokenizer.decode(tokens)

                val display = when {
                    input.isEmpty() -> "<empty>"
                    input.isBlank() -> "<blank(${input.length})>"
                    input.length > 20 -> "${input.take(17)}..."
                    else -> input
                }

                println("$description: \"$display\"")
                println("  → ${tokens.size} tokens")

            } catch (e: Exception) {
                println("$description: Error - ${e.message}")
            }
        }
    }

    @Test
    fun testComplexityPrompts() {
        println("\n=== Complexity Classification Prompts ===\n")

        val prompts = listOf(
            "Hi" to ComplexityLevel.TRIVIAL,
            "What is 2+2?" to ComplexityLevel.SIMPLE,
            "Explain photosynthesis" to ComplexityLevel.MEDIUM,
            "Write a Python function to find primes" to ComplexityLevel.COMPLEX
        )

        println("Testing tokenization of classification prompts...")
        println("%-40s | Expected | Tokens".format("Prompt"))
        println("-".repeat(65))

        for ((prompt, level) in prompts) {
            val fullPrompt = """
                Classify the complexity of this prompt on a scale of 0-1:
                "$prompt"

                Respond with only a number between 0 and 1.
            """.trimIndent()

            val tokens = tokenizer.encode(fullPrompt)
            val truncated = if (prompt.length > 37) prompt.take(34) + "..." else prompt

            println("%-40s | %-8s | %d".format(truncated, level, tokens.size))
        }

        println("\nNote: Fewer tokens = more efficient model inference")
    }
}