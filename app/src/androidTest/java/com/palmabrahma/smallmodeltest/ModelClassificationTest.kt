package com.palmabrahma.smallmodeltest

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.palmabrahma.smallmodeltest.models.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import java.io.File

/**
 * Instrumented test to compare model-based vs heuristic classification
 * This runs on an Android device/emulator
 */
@RunWith(AndroidJUnit4::class)
class ModelClassificationTest {

    @Test
    fun testTokenizerAndClassification() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Initialize Timber for logging
        if (Timber.treeCount == 0) {
            Timber.plant(Timber.DebugTree())
        }

        println("\n=== Testing Tokenizer and Classification ===\n")

        // Setup model manager
        val modelManager = ModelManager(context)

        // Download model files if not present
        if (!modelManager.isModelDownloaded(ModelType.PHI3_MINI)) {
            println("Downloading Phi-3 model files...")
            try {
                kotlinx.coroutines.withTimeout(300000) { // 5 minute timeout
                    modelManager.downloadModel(ModelType.PHI3_MINI)
                        .collect { progress ->
                            if (progress.isComplete || progress.percentComplete.toInt() % 10 == 0) {
                                println("${progress.currentOperation}: ${progress.percentComplete.toInt()}%")
                            }
                        }
                }
                println("Model files downloaded successfully!")
            } catch (e: Exception) {
                println("Failed to download model files: ${e.message}")
                println("Some tests will be skipped.")
            }
        }

        // Check if tokenizer file exists
        val modelDir = modelManager.getModelDirectory(ModelType.PHI3_MINI)
        val tokenizerFile = File(modelDir, ModelManager.PHI3_TOKENIZER_NAME)

        println("Tokenizer file path: ${tokenizerFile.absolutePath}")
        println("Tokenizer file exists: ${tokenizerFile.exists()}")

        if (tokenizerFile.exists()) {
            println("Tokenizer file size: ${tokenizerFile.length()} bytes")
        }

        // Test tokenizer with sample text
        val tokenizer = Phi3Tokenizer(context, tokenizerFile.absolutePath)

        val testTexts = listOf(
            "Hello world",
            "What is 2+2?",
            "Classify the complexity of this prompt on a scale of 0-1:",
            "0.5",
            "Write a Python function"
        )

        println("\n=== Tokenization Test ===")
        for (text in testTexts) {
            val tokens = tokenizer.encode(text)
            val decoded = tokenizer.decode(tokens)
            println("Text: \"$text\"")
            println("  Tokens (${tokens.size}): ${tokens.take(10).joinToString(", ")}")
            println("  Decoded: \"$decoded\"")
            println()
        }

        // Test complexity classification
        val classifier = ComplexityClassifier()

        val testPrompts = mapOf(
            "Hi" to ComplexityLevel.TRIVIAL,
            "What is 2+2?" to ComplexityLevel.SIMPLE,
            "Explain photosynthesis" to ComplexityLevel.MEDIUM,
            "Write a Python function to find prime numbers" to ComplexityLevel.COMPLEX
        )

        println("\n=== Classification Comparison ===")
        println("%-40s | %-12s | %-12s | %-8s | %-8s".format(
            "Prompt", "Expected", "Heuristic", "H-Score", "H-Conf"
        ))
        println("-".repeat(85))

        for ((prompt, expected) in testPrompts) {
            val heuristicResult = classifier.classifyWithHeuristics(prompt)

            val truncatedPrompt = if (prompt.length > 37) {
                prompt.take(34) + "..."
            } else {
                prompt
            }

            println("%-40s | %-12s | %-12s | %.3f   | %.2f".format(
                truncatedPrompt,
                expected,
                heuristicResult.level,
                heuristicResult.score,
                heuristicResult.confidence
            ))
        }

        // If model is available, test model-based classification
        if (modelManager.isModelDownloaded(ModelType.PHI3_MINI)) {
            println("\n=== Model-Based Classification Test ===")

            try {
                val model = Phi3MiniAdapter(context)
                val initTime = model.initialize()
                println("Model initialized in ${initTime}ms")

                val testPrompt = "Write a Python function to find prime numbers"
                println("\nTesting prompt: \"$testPrompt\"")

                val modelResult = classifier.classifyWithModel(testPrompt, model)
                val heuristicResult = classifier.classifyWithHeuristics(testPrompt)

                println("\nResults Comparison:")
                println("  Heuristic Classification:")
                println("    Score: %.3f".format(heuristicResult.score))
                println("    Level: ${heuristicResult.level}")
                println("    Confidence: %.2f".format(heuristicResult.confidence))

                println("\n  Model-Based Classification:")
                println("    Score: %.3f".format(modelResult.score))
                println("    Level: ${modelResult.level}")
                println("    Confidence: %.2f".format(modelResult.confidence))

                model.cleanup()

            } catch (e: Exception) {
                println("Error testing model: ${e.message}")
                e.printStackTrace()
            }
        } else {
            println("\n⚠️  Model files not downloaded. Please download the Phi-3 model first.")
            println("   Download status:")
            println("   - Model file: ${File(modelDir, ModelManager.PHI3_MODEL_NAME).exists()}")
            println("   - Model data: ${File(modelDir, ModelManager.PHI3_MODEL_DATA_NAME).exists()}")
            println("   - Tokenizer: ${tokenizerFile.exists()}")
            println("   - Tokenizer model: ${File(modelDir, ModelManager.PHI3_TOKENIZER_MODEL_NAME).exists()}")
        }
    }
}