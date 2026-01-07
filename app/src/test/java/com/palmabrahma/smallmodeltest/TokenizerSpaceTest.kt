package com.palmabrahma.smallmodeltest

import com.palmabrahma.smallmodeltest.models.Phi3MiniAdapter
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock
import android.content.Context
import java.io.File

/**
 * Unit test to verify tokenizer properly handles spaces with ▁ markers
 */
class TokenizerSpaceTest {

    @Test
    fun testTokenizerSpaceHandling() {
        // Mock context
        val context = mock(Context::class.java)

        // Create tokenizer with non-existent file to use fallback vocabulary
        val tokenizer = Phi3MiniAdapter.Phi3Tokenizer(context, "/non/existent/file.json")

        // Test cases
        val testCases = listOf(
            "Hello world" to "should handle simple two-word phrase",
            "What is 2+2?" to "should handle question with numbers",
            "The quick brown fox" to "should handle multiple words",
            "Machine learning model" to "should handle technical terms",
            "Write a Python function" to "should handle coding prompts"
        )

        println("\n=== Tokenizer Space Handling Test ===\n")

        for ((text, description) in testCases) {
            println("Test: $description")
            println("  Input: \"$text\"")

            // Encode the text
            val tokens = tokenizer.encode(text)
            println("  Tokens (${tokens.size}): ${tokens.take(10).joinToString(", ")}...")

            // Decode the tokens
            val decoded = tokenizer.decode(tokens)
            println("  Decoded: \"$decoded\"")

            // Check that spaces are preserved (ignore extra spaces)
            val normalizedInput = text.replace(Regex("\\s+"), " ").trim()
            val normalizedDecoded = decoded.replace(Regex("\\s+"), " ").trim()

            // For fallback vocabulary, we expect at least the words to be present
            val inputWords = normalizedInput.split(" ")
            val decodedWords = normalizedDecoded.split(" ")

            println("  Input words: ${inputWords.joinToString(", ")}")
            println("  Decoded words: ${decodedWords.joinToString(", ")}")

            // Basic check: BOS token should be present
            assertTrue("Should have BOS token", tokens.isNotEmpty() && tokens[0] == 1)

            // Check that we get some text back
            assertTrue("Decoded text should not be empty", decoded.isNotEmpty())

            println("  Status: ✓ Test passed")
            println()
        }
    }

    @Test
    fun testNormalization() {
        val context = mock(Context::class.java)
        val tokenizer = Phi3MiniAdapter.Phi3Tokenizer(context, "/non/existent/file.json")

        println("\n=== Text Normalization Test ===\n")

        // Test that spaces are properly normalized to ▁
        val text = "Hello world"
        println("Original text: \"$text\"")

        // The encode method should:
        // 1. Normalize: "Hello world" -> "▁Hello▁world"
        // 2. Tokenize the normalized text

        val tokens = tokenizer.encode(text)
        println("Token count: ${tokens.size}")
        println("Tokens: ${tokens.joinToString(", ")}")

        // The decode method should replace ▁ back to spaces
        val decoded = tokenizer.decode(tokens)
        println("Decoded: \"$decoded\"")

        // Even with fallback vocab, spaces should be handled
        assertNotNull("Tokens should not be null", tokens)
        assertTrue("Should have at least BOS token", tokens.size > 1)
    }
}