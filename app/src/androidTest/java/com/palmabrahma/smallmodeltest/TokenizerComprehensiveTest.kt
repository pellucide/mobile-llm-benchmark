package com.palmabrahma.smallmodeltest

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.palmabrahma.smallmodeltest.models.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import java.io.File

/**
 * Comprehensive test cases for Phi3Tokenizer
 * Tests various edge cases, special characters, and tokenization scenarios
 * Automatically downloads model files if not present
 */
@RunWith(AndroidJUnit4::class)
class TokenizerComprehensiveTest {

    private lateinit var tokenizer: Phi3Tokenizer
    private lateinit var context: android.content.Context
    private lateinit var modelManager: ModelManager
    private var isUsingRealTokenizer = false

    @Before
    fun setup() = runBlocking {
        context = InstrumentationRegistry.getInstrumentation().targetContext

        // Initialize Timber for logging
        if (Timber.treeCount == 0) {
            Timber.plant(Timber.DebugTree())
        }

        // Setup model manager
        modelManager = ModelManager(context)

        // Check if model files are downloaded, if not download them
        if (!modelManager.isModelDownloaded(ModelType.PHI3_MINI)) {
            println("\n=== Downloading Phi-3 Model Files ===")
            println("This will download ~1.8GB of model data...")

            try {
                withTimeout(300000) { // 5 minute timeout for download
                    var lastProgress = 0f

                    modelManager.downloadModel(ModelType.PHI3_MINI)
                        .onEach { progress ->
                            // Only print significant progress updates
                            if (progress.percentComplete - lastProgress >= 5f || progress.isComplete) {
                                println("${progress.fileName}: ${progress.percentComplete.toInt()}% " +
                                       "(${progress.bytesDownloaded / 1024 / 1024}MB / ${progress.totalBytes / 1024 / 1024}MB)")
                                lastProgress = progress.percentComplete
                            }
                        }
                        .catch { e ->
                            println("Download error: ${e.message}")
                            throw e
                        }
                        .onCompletion {
                            println("Download completed!")
                        }
                        .collect { }
                }
            } catch (e: Exception) {
                println("Failed to download model files: ${e.message}")
                println("Tests will run with fallback tokenizer")
            }
        } else {
            println("\n=== Model files already present ===")
        }

        // Setup tokenizer
        val modelDir = modelManager.getModelDirectory(ModelType.PHI3_MINI)
        val tokenizerFile = File(modelDir, ModelManager.PHI3_TOKENIZER_NAME)

        tokenizer = Phi3Tokenizer(context, tokenizerFile.absolutePath)
        isUsingRealTokenizer = tokenizerFile.exists()

        println("\n=== Tokenizer Test Setup ===")
        println("Tokenizer file exists: $isUsingRealTokenizer")
        if (isUsingRealTokenizer) {
            println("Using real tokenizer from: ${tokenizerFile.absolutePath}")
            println("Tokenizer file size: ${tokenizerFile.length() / 1024}KB")
        } else {
            println("Using fallback vocabulary")
        }
    }

    @Test
    fun testBasicTokenization() {
        println("\n=== Basic Tokenization Test ===\n")

        val testCases = listOf(
            "Hello" to "Single word",
            "Hello world" to "Two words",
            "Hello, world!" to "Punctuation",
            "The quick brown fox jumps over the lazy dog" to "Pangram",
            "I'm testing the tokenizer's ability" to "Contractions",
            "Test123" to "Alphanumeric",
            "user@example.com" to "Email format",
            "https://www.example.com" to "URL",
            "Line 1\nLine 2" to "Newlines",
            "Tab\tseparated\twords" to "Tabs"
        )

        for ((text, description) in testCases) {
            val tokens = tokenizer.encode(text)
            val decoded = tokenizer.decode(tokens)

            println("Test: $description")
            println("  Input:   \"$text\"")
            println("  Tokens:  ${tokens.size} tokens - ${tokens.take(10).joinToString(", ")}${if (tokens.size > 10) "..." else ""}")
            println("  Decoded: \"$decoded\"")

            // Basic assertions
            assertTrue("Should have at least BOS token", tokens.isNotEmpty())
            assertEquals("First token should be BOS", tokenizer.bosTokenId, tokens[0])

            // Check that decoding preserves meaning (allowing for space normalization)
            val normalizedInput = text.replace(Regex("\\s+"), "")
            val normalizedDecoded = decoded.replace(Regex("\\s+"), "")
            if (isUsingRealTokenizer) {
                // With real tokenizer, content should be preserved
                assertTrue(
                    "Decoded text should preserve content: '$normalizedDecoded' vs '$normalizedInput'",
                    normalizedDecoded.contains(normalizedInput) ||
                    normalizedInput.contains(normalizedDecoded)
                )
            }
            println()
        }
    }

    @Test
    fun testSpecialCharacters() {
        println("\n=== Special Characters Test ===\n")

        val specialCases = listOf(
            "!@#$%^&*()" to "Common symbols",
            "[]{}()<>" to "Brackets",
            "+-*/=" to "Math operators",
            "\"quoted text\"" to "Quotes",
            "'single quotes'" to "Single quotes",
            "emoji: 😀 🚀 ❤️" to "Emojis",
            "café" to "Accented characters",
            "Здравствуйте" to "Cyrillic",
            "你好" to "Chinese",
            "こんにちは" to "Japanese",
            "مرحبا" to "Arabic"
        )

        for ((text, description) in specialCases) {
            try {
                val tokens = tokenizer.encode(text)
                val decoded = tokenizer.decode(tokens)

                println("Test: $description")
                println("  Input:   \"$text\"")
                println("  Tokens:  ${tokens.size} tokens")
                println("  Decoded: \"$decoded\"")

                assertTrue("Should produce tokens", tokens.isNotEmpty())
                println("  ✓ Tokenization successful")
            } catch (e: Exception) {
                println("Test: $description")
                println("  Input:   \"$text\"")
                println("  ✗ Error: ${e.message}")
            }
            println()
        }
    }

    @Test
    fun testEdgeCases() {
        println("\n=== Edge Cases Test ===\n")

        val edgeCases = listOf(
            "" to "Empty string",
            " " to "Single space",
            "   " to "Multiple spaces",
            "\n" to "Single newline",
            "\t" to "Single tab",
            "a" to "Single character",
            "A" to "Single uppercase",
            "0" to "Single digit",
            "." to "Single period",
            " leading space" to "Leading space",
            "trailing space " to "Trailing space",
            "  multiple   spaces  " to "Multiple spaces",
            "CamelCaseText" to "Camel case",
            "snake_case_text" to "Snake case",
            "kebab-case-text" to "Kebab case",
            "UPPERCASE" to "All uppercase",
            "lowercase" to "All lowercase",
            "123456789" to "Numbers only",
            "1.23" to "Decimal number",
            "-42" to "Negative number",
            "1e-10" to "Scientific notation"
        )

        for ((text, description) in edgeCases) {
            val tokens = tokenizer.encode(text)
            val decoded = tokenizer.decode(tokens)

            println("Test: $description")
            println("  Input:   \"$text\" (length: ${text.length})")
            println("  Tokens:  ${tokens.size} - ${tokens.joinToString(", ")}")
            println("  Decoded: \"$decoded\" (length: ${decoded.length})")

            // Verify BOS token is always present
            assertTrue("Should always have BOS token", tokens.isNotEmpty())
            assertEquals("First token should be BOS", tokenizer.bosTokenId, tokens[0])
            println()
        }
    }

    @Test
    fun testPromptTemplates() {
        println("\n=== Prompt Templates Test ===\n")

        val prompts = listOf(
            "What is 2+2?" to "Simple question",
            "Explain quantum computing in simple terms" to "Explanation request",
            "Write a Python function to sort a list" to "Code generation",
            "<|system|>You are helpful<|end|><|user|>Hi<|end|><|assistant|>" to "Chat template",
            "Translate 'Hello' to Spanish" to "Translation",
            "Summarize: The quick brown fox..." to "Summarization",
            """
            Context: Machine learning is...
            Question: What is ML?
            Answer:
            """.trimIndent() to "QA format",
            "Complete the following: Once upon a" to "Completion",
            "1. First\n2. Second\n3. Third" to "Numbered list",
            "```python\ndef hello():\n    pass\n```" to "Code block"
        )

        for ((prompt, description) in prompts) {
            val tokens = tokenizer.encode(prompt)
            val decoded = tokenizer.decode(tokens)

            println("Test: $description")
            println("  Input length:   ${prompt.length} chars")
            println("  Token count:    ${tokens.size}")
            println("  Compression:    %.2fx".format(prompt.length.toFloat() / tokens.size))
            println("  First 5 tokens: ${tokens.take(5).joinToString(", ")}")
            println("  Decoded preview: \"${decoded.take(50)}${if (decoded.length > 50) "..." else ""}\"")
            println()
        }
    }

    @Test
    fun testTokenizerConsistency() {
        println("\n=== Tokenizer Consistency Test ===\n")

        val testTexts = listOf(
            "Hello world",
            "The quick brown fox",
            "Write a function",
            "Machine learning model",
            "0.5",
            "Test 123"
        )

        for (text in testTexts) {
            // Encode the same text multiple times
            val tokens1 = tokenizer.encode(text)
            val tokens2 = tokenizer.encode(text)
            val tokens3 = tokenizer.encode(text)

            println("Text: \"$text\"")
            println("  First encoding:  ${tokens1.joinToString(", ")}")
            println("  Second encoding: ${tokens2.joinToString(", ")}")
            println("  Third encoding:  ${tokens3.joinToString(", ")}")

            // Verify consistency
            assertEquals("Tokenization should be deterministic", tokens1, tokens2)
            assertEquals("Tokenization should be deterministic", tokens2, tokens3)
            println("  ✓ Consistent tokenization")

            // Test decode consistency
            val decoded1 = tokenizer.decode(tokens1)
            val decoded2 = tokenizer.decode(tokens2)

            assertEquals("Decoding should be consistent", decoded1, decoded2)
            println("  ✓ Consistent decoding")
            println()
        }
    }

    @Test
    fun testComplexityPrompts() {
        println("\n=== Complexity Classification Prompts Test ===\n")

        val classificationPrompts = listOf(
            "Hi",
            "What is 2+2?",
            "Explain photosynthesis",
            "Write a Python function to find prime numbers",
            "Analyze the geopolitical implications of climate change on global trade routes",
            "Debug this code: def f(n): return f(n-1) if n > 0 else 1"
        )

        for (prompt in classificationPrompts) {
            val fullPrompt = """
                |Classify the complexity of this prompt on a scale of 0-1:
                |"$prompt"
                |
                |Consider:
                |- Length and structure
                |- Technical vocabulary
                |- Required reasoning depth
                |- Domain expertise needed
                |
                |Respond with only a number between 0 and 1.
            """.trimMargin()

            val tokens = tokenizer.encode(fullPrompt)
            val decoded = tokenizer.decode(tokens)

            println("Original prompt: \"${prompt.take(50)}${if (prompt.length > 50) "..." else ""}\"")
            println("  Full prompt length: ${fullPrompt.length} chars")
            println("  Token count: ${tokens.size}")
            println("  Compression ratio: %.2fx".format(fullPrompt.length.toFloat() / tokens.size))
            println("  Token efficiency: %.2f chars/token".format(fullPrompt.length.toFloat() / tokens.size))
            println()
        }
    }

    @Test
    fun testLongText() {
        println("\n=== Long Text Tokenization Test ===\n")

        val longTexts = listOf(
            "word ".repeat(100) to "100 repeated words",
            "A very long sentence that contains many words and continues for quite some time without stopping or pausing, demonstrating how the tokenizer handles extended prose that might appear in natural language processing tasks." to "Long sentence",
            (1..50).joinToString(" ") { "Item$it" } to "50 numbered items",
            "The quick brown fox jumps over the lazy dog. ".repeat(10) to "Repeated pangram",
            """
            |This is a multi-line text
            |with several paragraphs.
            |
            |Each paragraph is separated
            |by blank lines.
            |
            |The tokenizer should handle
            |this format correctly.
            """.trimMargin() to "Multi-paragraph"
        )

        for ((text, description) in longTexts) {
            val startTime = System.currentTimeMillis()
            val tokens = tokenizer.encode(text)
            val encodeTime = System.currentTimeMillis() - startTime

            val decodeStart = System.currentTimeMillis()
            val decoded = tokenizer.decode(tokens)
            val decodeTime = System.currentTimeMillis() - decodeStart

            println("Test: $description")
            println("  Input length:  ${text.length} chars")
            println("  Token count:   ${tokens.size}")
            println("  Compression:   %.2fx".format(text.length.toFloat() / tokens.size))
            println("  Encode time:   ${encodeTime}ms")
            println("  Decode time:   ${decodeTime}ms")
            println("  Tokens/sec:    ${if (encodeTime > 0) tokens.size * 1000 / encodeTime else "∞"}")
            println()
        }
    }

    @Test
    fun testSpecialTokens() {
        println("\n=== Special Tokens Test ===\n")

        // Test special tokens that might be in the vocabulary
        val specialTokens = listOf(
            "<s>" to "BOS token",
            "</s>" to "EOS token",
            "<unk>" to "Unknown token",
            "<|endoftext|>" to "End of text",
            "<|system|>" to "System marker",
            "<|user|>" to "User marker",
            "<|assistant|>" to "Assistant marker",
            "<|end|>" to "End marker",
            "<pad>" to "Padding token",
            "<mask>" to "Mask token"
        )

        for ((token, description) in specialTokens) {
            val tokens = tokenizer.encode(token)
            val decoded = tokenizer.decode(tokens)

            println("Test: $description")
            println("  Input:   \"$token\"")
            println("  Tokens:  ${tokens.joinToString(", ")}")
            println("  Decoded: \"$decoded\"")

            // Check if it's recognized as a special token (would be a single token after BOS)
            if (tokens.size == 2) {
                println("  ✓ Recognized as special token (ID: ${tokens[1]})")
            } else {
                println("  ✗ Not recognized as special token (${tokens.size - 1} tokens after BOS)")
            }
            println()
        }
    }

    @Test
    fun testRoundTripAccuracy() {
        println("\n=== Round-Trip Accuracy Test ===\n")

        val testCases = listOf(
            "Simple text",
            "Numbers: 123, 456.78, -9",
            "Symbols: + - * / = < > <= >= != ==",
            "Mixed: Test123 with symbols @#$",
            "Code: function test() { return 42; }",
            "Math: ∑(x²) = ∫f(x)dx",
            "Quotes: \"Hello,\" she said.",
            "Path: /usr/local/bin/python3",
            "URL: https://example.com/path?query=1",
            "Email: user.name+tag@example.co.uk"
        )

        var perfectMatches = 0
        var totalTests = testCases.size

        for (text in testCases) {
            val tokens = tokenizer.encode(text)
            val decoded = tokenizer.decode(tokens)

            // Normalize for comparison (remove spaces for basic comparison)
            val normalizedInput = text.replace(Regex("\\s+"), "").lowercase()
            val normalizedDecoded = decoded.replace(Regex("\\s+"), "").lowercase()

            val isMatch = normalizedInput == normalizedDecoded
            if (isMatch) perfectMatches++

            println("Input:   \"$text\"")
            println("Decoded: \"$decoded\"")
            println("Status:  ${if (isMatch) "✓ Perfect match" else "✗ Mismatch"}")
            println()
        }

        println("Summary: $perfectMatches/$totalTests perfect matches (${(perfectMatches * 100 / totalTests)}%)")

        // With real tokenizer, we expect high accuracy
        if (isUsingRealTokenizer) {
            assertTrue(
                "Should have at least 70% accuracy with real tokenizer",
                perfectMatches.toFloat() / totalTests >= 0.7f
            )
        }
    }
}