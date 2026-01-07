package com.palmabrahma.smallmodeltest

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import ai.onnxruntime.genai.Model
import ai.onnxruntime.genai.Tokenizer
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.palmabrahma.smallmodeltest.models.ModelManager
import com.palmabrahma.smallmodeltest.models.ModelType
import org.junit.runner.RunWith
import timber.log.Timber
import java.io.File
import java.nio.charset.StandardCharsets
import androidx.test.platform.app.InstrumentationRegistry
import com.palmabrahma.smallmodeltest.models.ModelManager.Companion.PHI3_CONFIG_NAME
import com.palmabrahma.smallmodeltest.models.ModelManager.Companion.PHI3_CONFIG_URL
import com.palmabrahma.smallmodeltest.models.ModelManager.Companion.PHI3_GENAI_CONFIG_NAME
import com.palmabrahma.smallmodeltest.models.ModelManager.Companion.PHI3_GENAI_CONFIG_URL
import com.palmabrahma.smallmodeltest.models.ModelManager.Companion.PHI3_MODEL_DATA_NAME
import com.palmabrahma.smallmodeltest.models.ModelManager.Companion.PHI3_MODEL_DATA_URL
import com.palmabrahma.smallmodeltest.models.ModelManager.Companion.PHI3_MODEL_NAME
import com.palmabrahma.smallmodeltest.models.ModelManager.Companion.PHI3_MODEL_URL
import com.palmabrahma.smallmodeltest.models.ModelManager.Companion.PHI3_SPECIAL_TOKENS_NAME
import com.palmabrahma.smallmodeltest.models.ModelManager.Companion.PHI3_SPECIAL_TOKENS_URL
import com.palmabrahma.smallmodeltest.models.ModelManager.Companion.PHI3_TOKENIZER_MODEL_NAME
import com.palmabrahma.smallmodeltest.models.ModelManager.Companion.PHI3_TOKENIZER_MODEL_URL
import com.palmabrahma.smallmodeltest.models.ModelManager.Companion.PHI3_TOKENIZER_NAME
import com.palmabrahma.smallmodeltest.models.ModelManager.Companion.PHI3_TOKENIZER_URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class NativePhi3TokenizerTest {

    // Define all files to download with their URLs and sizes (approximate)
    data class FileToDownload(
        val url: String,
        val fileName: String,
        val displayName: String,
        val approximateSizeMB: Long
    )
    private lateinit var onnxtokenizer : Tokenizer


    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    @Before
    fun setUp() = runBlocking {
        // Assuming tokenizer.json is in the test resources or current directory
        val tokenizerPath = "src/test/resources" // or "." for current directory

        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Initialize Timber
        if (Timber.treeCount == 0) {
            Timber.plant(Timber.DebugTree())
        }

        val modelManager = ModelManager(context)
        val modelDir = modelManager.getModelDirectory(ModelType.PHI3_MINI)

        val tokenizerFile = File(modelDir, ModelManager.PHI3_TOKENIZER_NAME)

        val filesToDownload = listOf(
            FileToDownload(
                PHI3_MODEL_URL,
                PHI3_MODEL_NAME,
                "Model Structure",
                200
            ),
            FileToDownload(
                PHI3_MODEL_DATA_URL,
                PHI3_MODEL_DATA_NAME,
                "Model Weights",
                1600
            ),
            FileToDownload(
                PHI3_TOKENIZER_URL,
                PHI3_TOKENIZER_NAME,
                "Tokenizer",
                2
            ),
            FileToDownload(
                PHI3_TOKENIZER_MODEL_URL,
                PHI3_TOKENIZER_MODEL_NAME,
                "Tokenizer Model",
                1
            ),
            FileToDownload(
                PHI3_CONFIG_URL,
                PHI3_CONFIG_NAME,
                "Configuration",
                1
            ),
            FileToDownload(
                PHI3_GENAI_CONFIG_URL,
                PHI3_GENAI_CONFIG_NAME,
                "GenAi_Configuration",
                1
            ),
            FileToDownload(
                PHI3_SPECIAL_TOKENS_URL,
                PHI3_SPECIAL_TOKENS_NAME,
                "Special Tokens",
                1
            )
        )

        for ((index, fileInfo) in filesToDownload.withIndex()) {
            val currentFileNumber = index + 1
            val fileToDownload = File(modelDir, fileInfo.fileName)

            // Download only tokenizer files if not present
            if (!fileToDownload.exists()) {
                println("Downloading tokenizer files (lightweight ~3MB total)...")
                try {
                    withContext(Dispatchers.IO) {
                            downloadFile(
                                url = fileInfo.url,
                                destination = fileToDownload,
                                description = fileInfo.displayName
                            )
                    }
                    println("✓ file ${fileInfo.fileName} downloaded successfully! tp $fileToDownload")
                } catch (e: Exception) {
                    println("✗ Failed to download files ${fileInfo.fileName}: ${e.message}")
                    println("Tests will run with fallback tokenizer")
                }
            } else {
                println("✓ file ${fileInfo.fileName} already present")
            }
        }

        //nativePhi3Tokenizer = NativePhi3Tokenizer(tokenizerFile.parent)
        val modelFile = modelManager.getModelFilePath(ModelType.PHI3_MINI)
        val model = Model(modelFile.parent)
        onnxtokenizer = Tokenizer(model)
    }

    @Test
    fun testEncodeDecodeRoundTrip() {
        val testCases = listOf(
            "😀",
            "Hello",
            "Hello world!",
            "Kotlin programming",
            "12345",
            "Test with spaces"
        )

        testCases.forEach { text ->
            //val tokensUsingNative = nativePhi3Tokenizer.encode(text)
            val tokensUsingOnnx = onnxtokenizer.encode(text).getSequence(0)
            //val reconstructed = nativePhi3Tokenizer.decode(tokensUsingNative)
            val decodedUsingOnnx = onnxtokenizer.decode(tokensUsingOnnx)
            //assertEquals( "Comparing onnx with native failed for: '$text'",tokensUsingOnnx.toList(), tokensUsingNative)
            //assertEquals( "Comparing decoded with onnx with that of native failed for: '$text'",decodedUsingOnnx, reconstructed)
            //assertEquals( "Round-trip failed for: '$text'",text, reconstructed)
            assertEquals( "Round-trip failed for: '$text'", " $text", decodedUsingOnnx)
        }
    }

    @Test
    fun testEmojiTokenization() {
        val emojiTestCases = mapOf(
            "😀" to 4, // Grinning Face - 4 bytes in UTF-8
            "👋" to 4, // Waving Hand - 4 bytes
            "❤️" to 2, // Red Heart + variation selector
            "🐍" to 4, // Snake
            "☕" to 3  // Hot Beverage - 3 bytes
        )
        // Test with some potentially unknown or rare characters
        val rareCharacters = listOf(
            "⚡", // High voltage emoji
            "✅", // Check mark
            "🔄", // Refresh symbol
            "💯"  // Hundred points
        )


        emojiTestCases.forEach { (emoji, expectedMinTokens) ->
            val tokens = onnxtokenizer.encode(emoji).getSequence(0)
            assertTrue("Emoji '$emoji' should have at least $expectedMinTokens tokens, but got ${tokens.size}",
            tokens.size >= expectedMinTokens)

            val reconstructed = onnxtokenizer.decode(tokens)
            assertEquals("Emoji round-trip failed for: '$emoji'", emoji, reconstructed)
        }

        rareCharacters.forEach { emoji ->
            val tokens = onnxtokenizer.encode(emoji).getSequence(0)
            val reconstructed = onnxtokenizer.decode(tokens)
            assertEquals("Emoji round-trip failed for: '$emoji'", emoji, reconstructed)
        }
    }

    @Test
    fun testComplexEmojiSequence() {
        val testCases = listOf(
            "👋🏿", // Waving Hand with dark skin tone
            "❤️🔥", // Heart on Fire
            "😀👋❤️", // Multiple emojis
            "Hi 😊 there!" // Mixed text and emoji
        )

        testCases.forEach { text ->
            val tokens = onnxtokenizer.encode(text).getSequence(0)
            assertTrue("Tokens should not be empty for: '$text'",tokens.isNotEmpty())

            val reconstructed = onnxtokenizer.decode(tokens)
            assertEquals( "Complex emoji round-trip failed for: '$text'",text, reconstructed)
        }
    }

    @Test
    fun testTokenCountConsistency() {
        val testCases = mapOf(
            "Hello" to 1, // Should be a single token if in vocabulary
            "Hello world!" to 3, // "Hello", " world", "!"
            "A" to 1, // Single character
            " " to 1  // Space
        )

        testCases.forEach { (text, expectedMaxTokens) ->
            val tokens = onnxtokenizer.encode(text).getSequence(0)
            assertTrue("'$text' should have at most $expectedMaxTokens tokens, but got ${tokens.size}",tokens.size <= expectedMaxTokens)

        }
    }

    @Test
    fun testSpecialCharacters() {
        val specialChars = listOf(
            "!", "@", "#", "$", "%", "^", "&", "*", "(", ")",
            "-", "_", "=", "+", "[", "]", "{", "}", ";", ":",
            "'", "\"", ",", "<", ">", ".", "?", "/", "\\", "|"
        )

        specialChars.forEach { char ->
            val tokens = onnxtokenizer.encode(char).getSequence(0)
            assertTrue("Should tokenize special character: '$char'",tokens.isNotEmpty())

            val reconstructed = onnxtokenizer.decode(tokens)
            assertEquals("Special character round-trip failed: '$char'",char, reconstructed)
        }
    }

    @Test
    fun testEmptyString() {
        val tokens = onnxtokenizer.encode("").getSequence(0)
        assertTrue( "Empty string should produce no tokens",tokens.isEmpty())

        val emptyArray = IntArray(0)
        val reconstructed = onnxtokenizer.decode(emptyArray)
        assertEquals("Empty token list should decode to empty string","", reconstructed)
    }

    @Test
    fun testWhitespaceHandling() {
        val whitespaceCases = listOf(
            " ",
            "  ",
            "\t",
            "\n",
            " \t\n"
        )

        whitespaceCases.forEach { whitespace ->
            val tokens = onnxtokenizer.encode(whitespace).getSequence(0)
            val reconstructed = onnxtokenizer.decode(tokens)
            assertEquals("Whitespace handling failed for: '${whitespace.replace("\n", "\\n").replace("\t", "\\t")}'",whitespace, reconstructed)
        }
    }

    @Test
    fun testMixedContent() {
        val mixedCases = listOf(
            "Hello 😊 World!",
            "Python 🐍 + Kotlin ❤️ = Awesome!",
            "Price: $100 💵",
            "Email: test@example.com 📧",
            "Multiple\nlines\tand emojis 😀👋"
        )

        mixedCases.forEach { text ->
            val tokens = onnxtokenizer.encode(text).getSequence(0)
            assertTrue( "Should tokenize mixed content: '$text'",tokens.isNotEmpty())

            val reconstructed = onnxtokenizer.decode(tokens)
            assertEquals( "Mixed content round-trip failed for: '$text'",text, reconstructed)
        }
    }

    @Test
    fun testTokenizationStability() {
        val text = "Hello world 😊"
        val tokens1 = onnxtokenizer.encode(text).getSequence(0)
        val tokens2 = onnxtokenizer.encode(text).getSequence(0)

        assertEquals( "Multiple encodings of same text should produce identical tokens",tokens1, tokens2)
    }

    private suspend fun downloadFile(url: String, destination: File, description: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to download $description: ${response.code}")
            }

            val totalBytes = response.body.contentLength()
            var downloadedBytes = 0L
            var lastProgress = 0

            response.body.byteStream().use { input ->
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

}

// Fallback test class for when tokenizer.json is not available
class SimulatedTokenizerTest {

    @Test
    fun testSimulatedEmojiBehavior() {
        // Test our understanding of how emojis should be tokenized
        val emojiByteLengths = mapOf(
            "😀" to 4, // F0 9F 98 80
            "👋" to 4, // F0 9F 91 8B
            "❤" to 3,  // E2 9D A4
            "☕" to 3   // E2 98 95
        )

        emojiByteLengths.forEach { (emoji, expectedBytes) ->
            val actualBytes = emoji.toByteArray(StandardCharsets.UTF_8).size
            assertEquals("Emoji '$emoji' should be $expectedBytes bytes in UTF-8",expectedBytes, actualBytes)

        }
    }

    @Test
    fun testUTF8EncodingPrinciples() {
        // Verify our understanding of UTF-8 encoding
        val testCases = mapOf(
            "A" to 1, // ASCII - 1 byte
            "é" to 2, // Latin-1 Supplement - 2 bytes
            "🐍" to 4, // Emoji - 4 bytes
            "😊" to 4  // Emoji - 4 bytes
        )

        testCases.forEach { (char, expectedBytes) ->
            val bytes = char.toByteArray(StandardCharsets.UTF_8)
            assertEquals("Character '$char' should be $expectedBytes bytes in UTF-8",expectedBytes, bytes.size)
        }
    }
}

fun runSimulatedTests() {
    println("\n=== Running Simulated Tests ===")

    // Test UTF-8 byte counting
    val testChars = listOf("A", "é", "😀", "❤️", "👋🏿")
    testChars.forEach { char ->
        val bytes = char.toByteArray(StandardCharsets.UTF_8)
        println("'$char' -> ${bytes.size} bytes: ${bytes.joinToString(" ") { "%02X".format(it) }}")
    }
}