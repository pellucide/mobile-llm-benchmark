package com.palmabrahma.smallmodeltest.models

import kotlinx.coroutines.flow.Flow

/**
 * Base interface for all LLM model adapters
 */
interface BaseModelAdapter {

    val modelName: String
    val modelSizeMB: Int
    val parametersCount: String
    val quantizationType: String

    /**
     * Initialize the model and load into memory
     * @return Time taken to load in milliseconds
     */
    suspend fun initialize(): Long

    /**
     * Generate tokens for the given prompt
     * @param prompt Input text
     * @param maxTokens Maximum tokens to generate
     * @param temperature Temperature for sampling
     * @return Flow of generated tokens
     */
    fun generateTokens(
        prompt: String,
        maxTokens: Int = 100,
        temperature: Float = 0.7f
    ): Flow<GenerationResult>

    /**
     * Classify the complexity of a prompt (for routing decisions)
     * @param prompt Input text to classify
     * @return Complexity score from 0.0 (simple) to 1.0 (complex)
     */
    suspend fun classifyComplexity(prompt: String): Float

    /**
     * Release model from memory
     */
    suspend fun cleanup()

    /**
     * Get current memory usage
     */
    fun getMemoryUsage(): MemoryInfo

    /**
     * Check if model is loaded and ready
     */
    fun isReady(): Boolean
}

/**
 * Result of token generation including metrics
 */
data class GenerationResult(
    val token: String,
    val tokenId: Int,
    val logProb: Float,
    val timestamp: Long,
    val isComplete: Boolean
)

/**
 * Memory usage information
 */
data class MemoryInfo(
    val usedMemoryMB: Long,
    val maxMemoryMB: Long,
    val availableMemoryMB: Long
)

/**
 * Complexity levels for routing
 */
enum class ComplexityLevel {
    TRIVIAL,    // Hi, thanks, yes/no
    SIMPLE,     // What's 2+2, basic facts
    MEDIUM,     // Explanations, summaries
    COMPLEX,    // Analysis, creative writing
    EXPERT      // Specialized knowledge, complex reasoning
}

/**
 * Model capabilities assessment
 */
data class ModelCapabilities(
    val reasoning: Float,      // 0.0 - 1.0
    val coding: Float,
    val creative: Float,
    val factual: Float,
    val mathematical: Float,
    val conversational: Float
)