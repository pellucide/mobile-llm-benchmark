package com.palmabrahma.smallmodeltest

import com.palmabrahma.smallmodeltest.models.ComplexityClassifier
import com.palmabrahma.smallmodeltest.models.ComplexityLevel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for tokenizer and classification
 * These run on the JVM without needing the Android device
 */
class TokenizerUnitTest {

    @Test
    fun testTokenizationPatterns() {
        println("\n=== Tokenization Patterns Test ===\n")

        // Test the regex patterns used in tokenization
        val pattern = Regex("""'s|'t|'re|'ve|'m|'ll|'d|[\w]+|[^\s\w]""")

        val testCases = mapOf(
            "I'm" to listOf("I", "'m"),
            "don't" to listOf("don", "'t"),
            "they're" to listOf("they", "'re"),
            "we've" to listOf("we", "'ve"),
            "I'll" to listOf("I", "'ll"),
            "she'd" to listOf("she", "'d"),
            "Hello, world!" to listOf("Hello", ",", "world", "!"),
            "test@email.com" to listOf("test", "@", "email", ".", "com"),
            "2+2=4" to listOf("2", "+", "2", "=", "4"),
            "multi-word" to listOf("multi", "-", "word"),
            "CamelCase" to listOf("CamelCase"),
            "under_score" to listOf("under", "_", "score")
        )

        for ((text, expected) in testCases) {
            val matches = pattern.findAll(text).map { it.value }.toList()

            println("Text: \"$text\"")
            println("  Expected: ${expected.joinToString(", ")}")
            println("  Actual:   ${matches.joinToString(", ")}")
            println("  Match: ${if (matches == expected) "✓" else "✗"}")
            println()

            assertEquals("Tokenization pattern should match expected", expected, matches)
        }
    }

    @Test
    fun testComplexityHeuristicsAccuracy() = runBlocking {
        println("\n=== Heuristic Classification Accuracy Test ===\n")

        val classifier = ComplexityClassifier()

        // Test cases with expected complexity levels
        val testCases = listOf(
            // TRIVIAL (0-0.15)
            TestCase("Hi", ComplexityLevel.TRIVIAL, 0.0f..0.15f),
            TestCase("OK", ComplexityLevel.TRIVIAL, 0.0f..0.15f),
            TestCase("Thanks", ComplexityLevel.TRIVIAL, 0.0f..0.15f),
            TestCase("Yes", ComplexityLevel.TRIVIAL, 0.0f..0.15f),
            TestCase("No", ComplexityLevel.TRIVIAL, 0.0f..0.15f),

            // SIMPLE (0.15-0.35)
            TestCase("What time is it?", ComplexityLevel.SIMPLE, 0.15f..0.35f),
            TestCase("What is 2+2?", ComplexityLevel.SIMPLE, 0.15f..0.35f),
            TestCase("Tell me a joke", ComplexityLevel.SIMPLE, 0.15f..0.35f),
            TestCase("How are you?", ComplexityLevel.SIMPLE, 0.15f..0.35f),

            // MEDIUM (0.35-0.55)
            TestCase("Explain how a car works", ComplexityLevel.MEDIUM, 0.35f..0.55f),
            TestCase("What causes rain to fall?", ComplexityLevel.MEDIUM, 0.35f..0.55f),
            TestCase("Describe the water cycle", ComplexityLevel.MEDIUM, 0.35f..0.55f),

            // COMPLEX (0.55-0.75)
            TestCase("Analyze the themes in Shakespeare's Hamlet", ComplexityLevel.COMPLEX, 0.55f..0.75f),
            TestCase("Compare different sorting algorithms", ComplexityLevel.COMPLEX, 0.55f..0.75f),
            TestCase("Explain the theory of relativity", ComplexityLevel.COMPLEX, 0.55f..0.75f),

            // EXPERT (0.75-1.0)
            TestCase("Design a distributed system with ACID guarantees", ComplexityLevel.EXPERT, 0.75f..1.0f),
            TestCase("Implement a compiler for a new programming language", ComplexityLevel.EXPERT, 0.75f..1.0f)
        )

        var correctClassifications = 0
        var correctScoreRanges = 0

        println("%-50s | %-10s | %-10s | %-8s | %-6s | %-6s".format(
            "Prompt", "Expected", "Actual", "Score", "Level✓", "Score✓"
        ))
        println("-".repeat(100))

        for (testCase in testCases) {
            val result = classifier.classifyWithHeuristics(testCase.prompt)

            val levelCorrect = result.level == testCase.expectedLevel
            val scoreCorrect = result.score in testCase.expectedScoreRange

            if (levelCorrect) correctClassifications++
            if (scoreCorrect) correctScoreRanges++

            val truncatedPrompt = if (testCase.prompt.length > 47) {
                testCase.prompt.take(44) + "..."
            } else {
                testCase.prompt
            }

            println("%-50s | %-10s | %-10s | %.3f   | %-6s | %-6s".format(
                truncatedPrompt,
                testCase.expectedLevel,
                result.level,
                result.score,
                if (levelCorrect) "✓" else "✗",
                if (scoreCorrect) "✓" else "✗"
            ))
        }

        val levelAccuracy = (correctClassifications * 100) / testCases.size
        val scoreAccuracy = (correctScoreRanges * 100) / testCases.size

        println("\n" + "=".repeat(100))
        println("Summary:")
        println("  Level Classification Accuracy: $correctClassifications/${testCases.size} ($levelAccuracy%)")
        println("  Score Range Accuracy: $correctScoreRanges/${testCases.size} ($scoreAccuracy%)")

        // Assert minimum accuracy
        assertTrue(
            "Level classification should be at least 60% accurate",
            levelAccuracy >= 60
        )
    }

    @Test
    fun testFeatureExtraction() = runBlocking {
        println("\n=== Feature Extraction Test ===\n")

        val classifier = ComplexityClassifier()

        val prompts = listOf(
            "hi",  // Minimal features
            "What is machine learning?",  // Domain-specific
            "Write a Python function to calculate fibonacci numbers recursively",  // Code + technical
            "Compare and contrast the economic impacts of inflation versus deflation",  // Analytical
            "If x + 2 = 5, solve for x"  // Mathematical
        )

        for (prompt in prompts) {
            val result = classifier.classifyWithHeuristics(prompt)

            println("Prompt: \"$prompt\"")
            println("  Overall Score: %.3f (%s)".format(result.score, result.level))
            println("  Feature Breakdown:")
            println("    Length:        %.3f (%.1f%%)".format(
                result.features.lengthScore,
                (result.features.lengthScore / result.score * 100)
            ))
            println("    Vocabulary:    %.3f (%.1f%%)".format(
                result.features.vocabularyScore,
                (result.features.vocabularyScore / result.score * 100)
            ))
            println("    Structure:     %.3f (%.1f%%)".format(
                result.features.structureScore,
                (result.features.structureScore / result.score * 100)
            ))
            println("    Domain:        %.3f (%.1f%%)".format(
                result.features.domainScore,
                (result.features.domainScore / result.score * 100)
            ))
            println("    Question Type: %.3f (%.1f%%)".format(
                result.features.questionTypeScore,
                (result.features.questionTypeScore / result.score * 100)
            ))
            println("    Technical:     %.3f (%.1f%%)".format(
                result.features.technicalScore,
                (result.features.technicalScore / result.score * 100)
            ))
            println("  Confidence: %.2f".format(result.confidence))
            println()
        }
    }

    @Test
    fun testBoundaryValues() = runBlocking {
        println("\n=== Boundary Value Test ===\n")

        val classifier = ComplexityClassifier()

        // Test edge cases and boundary values
        val edgeCases = listOf(
            "",  // Empty
            " ",  // Single space
            "a",  // Single character
            "A",  // Single uppercase
            "1",  // Single digit
            "?",  // Single punctuation
            "\n",  // Newline
            "   multiple   spaces   ",  // Multiple spaces
            "ALLCAPS",  // All uppercase
            "123456789",  // All numbers
            "!@#$%^&*()",  // Special characters
            "a".repeat(1000)  // Very long single word
        )

        println("%-30s | %-8s | %-15s | %-10s".format(
            "Input", "Score", "Level", "Confidence"
        ))
        println("-".repeat(70))

        for (input in edgeCases) {
            val result = classifier.classifyWithHeuristics(input)

            val displayInput = when {
                input.isEmpty() -> "<empty>"
                input.isBlank() -> "<blank(${input.length})>"
                input.length > 20 -> "${input.take(17)}..."
                else -> input
            }

            println("%-30s | %.3f   | %-15s | %.2f".format(
                displayInput,
                result.score,
                result.level,
                result.confidence
            ))

            // Assertions
            assertTrue("Score should be in valid range", result.score in 0.0f..1.0f)
            assertTrue("Confidence should be in valid range", result.confidence in 0.0f..1.0f)
            assertNotNull("Level should not be null", result.level)
        }
    }

    @Test
    fun testConsistencyAndDeterminism() = runBlocking {
        println("\n=== Consistency Test ===\n")

        val classifier = ComplexityClassifier()
        val testPrompt = "Write a Python function to find prime numbers"

        // Run classification multiple times
        val results = mutableListOf<Float>()

        for (i in 1..10) {
            val result = classifier.classifyWithHeuristics(testPrompt)
            results.add(result.score)
            println("Run $i: Score = %.3f, Level = ${result.level}".format(result.score))
        }

        // Check consistency
        val uniqueScores = results.toSet()
        assertEquals(
            "Classification should be deterministic (all scores should be identical)",
            1,
            uniqueScores.size
        )

        println("\n✓ All runs produced identical score: %.3f".format(results[0]))
    }

    // Helper class for test cases
    data class TestCase(
        val prompt: String,
        val expectedLevel: ComplexityLevel,
        val expectedScoreRange: ClosedFloatingPointRange<Float>
    )
}