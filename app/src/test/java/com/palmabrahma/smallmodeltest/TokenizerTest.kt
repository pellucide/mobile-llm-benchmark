package com.palmabrahma.smallmodeltest

import com.palmabrahma.smallmodeltest.models.ComplexityClassifier
import com.palmabrahma.smallmodeltest.models.ComplexityLevel
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Test tokenizer and classification implementation
 */
class TokenizerTest {

    @Test
    fun testClassificationComparison() = runBlocking {
        val classifier = ComplexityClassifier()

        // Test prompts from different complexity levels
        val testPrompts = mapOf(
            "Hi" to ComplexityLevel.TRIVIAL,
            "Hello" to ComplexityLevel.TRIVIAL,
            "Thanks" to ComplexityLevel.TRIVIAL,
            "What is 2+2?" to ComplexityLevel.SIMPLE,
            "What's the capital of France?" to ComplexityLevel.SIMPLE,
            "Tell me a joke" to ComplexityLevel.SIMPLE,
            "Explain photosynthesis in simple terms" to ComplexityLevel.MEDIUM,
            "Write a haiku about coding" to ComplexityLevel.MEDIUM,
            "How does a car engine work?" to ComplexityLevel.MEDIUM,
            "Write a Python function to find prime numbers" to ComplexityLevel.COMPLEX,
            "Explain the theory of relativity" to ComplexityLevel.COMPLEX,
            "Design a REST API for a todo app" to ComplexityLevel.COMPLEX
        )

        println("\n=== Complexity Classification Comparison ===\n")
        println("%-50s | %-15s | %-8s | %-10s | %-10s".format(
            "Prompt", "Expected", "H-Score", "H-Level", "Confidence"
        ))
        println("-".repeat(100))

        for ((prompt, expectedLevel) in testPrompts) {
            // Test with heuristics
            val heuristicResult = classifier.classifyWithHeuristics(prompt)

            // Print results
            val truncatedPrompt = if (prompt.length > 47) {
                prompt.take(44) + "..."
            } else {
                prompt
            }

            println("%-50s | %-15s | %.3f   | %-10s | %.2f".format(
                truncatedPrompt,
                expectedLevel,
                heuristicResult.score,
                heuristicResult.level,
                heuristicResult.confidence
            ))
        }

        println("\n=== Feature Score Breakdown Example ===\n")
        val examplePrompt = "Write a Python function to find prime numbers"
        val detailedResult = classifier.classifyWithHeuristics(examplePrompt)

        println("Prompt: \"$examplePrompt\"")
        println("Overall Score: %.3f".format(detailedResult.score))
        println("Complexity Level: ${detailedResult.level}")
        println("Confidence: %.2f".format(detailedResult.confidence))
        println("\nFeature Scores:")
        println("  Length Score:        %.3f".format(detailedResult.features.lengthScore))
        println("  Vocabulary Score:    %.3f".format(detailedResult.features.vocabularyScore))
        println("  Structure Score:     %.3f".format(detailedResult.features.structureScore))
        println("  Domain Score:        %.3f".format(detailedResult.features.domainScore))
        println("  Question Type Score: %.3f".format(detailedResult.features.questionTypeScore))
        println("  Technical Score:     %.3f".format(detailedResult.features.technicalScore))
    }

    @Test
    fun testEdgeCases() = runBlocking {
        val classifier = ComplexityClassifier()

        val edgeCases = listOf(
            "",                                    // Empty string
            "a",                                   // Single character
            "Hi, can you explain quantum physics?", // Simple greeting + complex topic
            "2+2=?",                              // Math with symbols
            "E=mc²?",                             // Famous equation
            "Why?",                               // Very short but potentially complex
            "Fix this: print('Hello')",          // Simple code with directive
            "Calculate the derivative of x^3 + 2x and explain each step" // Math + explanation
        )

        println("\n=== Edge Case Testing ===\n")
        println("%-50s | %-8s | %-15s | %-10s".format(
            "Prompt", "Score", "Level", "Confidence"
        ))
        println("-".repeat(85))

        for (prompt in edgeCases) {
            val result = classifier.classifyWithHeuristics(prompt)

            val displayPrompt = when {
                prompt.isEmpty() -> "<empty>"
                prompt.length > 47 -> prompt.take(44) + "..."
                else -> prompt
            }

            println("%-50s | %.3f   | %-15s | %.2f".format(
                displayPrompt,
                result.score,
                result.level,
                result.confidence
            ))
        }
    }
}