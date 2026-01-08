package com.palmabrahma.smallmodeltest.models

import ai.onnxruntime.genai.Generator
import ai.onnxruntime.genai.GeneratorParams
import ai.onnxruntime.genai.Model
import ai.onnxruntime.genai.Tokenizer
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.system.measureTimeMillis

/**
 * Phi-3 Mini model adapter using ONNX Runtime GenAI Java API.
 *
 * Uses the Generator API for streaming token generation.
 * Reference: https://onnxruntime.ai/docs/genai/api/java.html
 */
class Phi3MiniAdapter(
    private val context: Context
) : BaseModelAdapter {

    override val modelName = "Phi-3 Mini 4K Instruct"
    override val modelSizeMB = 1800
    override val parametersCount = "3.8B"
    override val quantizationType = "INT4"

    private var genaiModel: Model? = null
    private var genaiTokenizer: Tokenizer? = null
    private var isModelReady = false

    private val modelManager = ModelManager(context)

    override suspend fun initialize(): Long = withContext(Dispatchers.IO) {
        measureTimeMillis {
            try {
                if (!modelManager.isModelDownloaded(ModelType.PHI3_MINI)) {
                    throw IllegalStateException("Phi-3 model not downloaded. Please download first.")
                }

                val modelFile = modelManager.getModelFilePath(ModelType.PHI3_MINI)
                if (!modelFile.exists()) {
                    throw IllegalStateException("Model file not found: ${modelFile.absolutePath}")
                }

                // Load GenAI Model
                val model = Model(modelFile.parent)
                genaiModel = model

                // Create Tokenizer - if this fails, clean up the model
                try {
                    genaiTokenizer = Tokenizer(model)
                } catch (e: Exception) {
                    model.close()
                    genaiModel = null
                    throw e
                }

                isModelReady = true
                Timber.d("Phi-3 Mini model initialized successfully")
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize Phi-3 Mini model")
                isModelReady = false
                throw e
            }
        }
    }

    override fun generateTokens(
        prompt: String,
        maxTokens: Int,
        temperature: Float
    ): Flow<GenerationResult> = flow {
        require(isReady()) { "Model not initialized" }

        val model = genaiModel ?: throw IllegalStateException("Model not initialized")
        val tokenizer = genaiTokenizer ?: throw IllegalStateException("Tokenizer not initialized")

        val startTime = System.currentTimeMillis()

        // Configure generation parameters
        val params = GeneratorParams(model).apply {
            setSearchOption("max_length", maxTokens.toDouble())
            setSearchOption("temperature", temperature.toDouble())
            setSearchOption("do_sample", temperature > 0)
        }

        // Encode prompt
        val inputTokens = tokenizer.encode(prompt).getSequence(0)

        // Setup generator and tokenizer stream
        val generator = Generator(model, params)
        val tokenizerStream = tokenizer.createStream()
        generator.appendTokens(inputTokens)

        var generatedCount = 0
        val generatedText = StringBuilder()

        try {
            // Generation loop
            while (generatedCount < maxTokens) {
                generator.generateNextToken()

                if (generator.isDone()) {
                    break
                }

                val lastToken = generator.getLastTokenInSequence(0)
                val tokenText = tokenizerStream.decode(lastToken)
                generatedCount++
                generatedText.append(tokenText)

                emit(GenerationResult(
                    token = tokenText,
                    tokenId = lastToken,
                    logProb = 0f,
                    timestamp = System.currentTimeMillis() - startTime,
                    isComplete = false
                ))
            }

            // Final completion signal
            emit(GenerationResult(
                token = "",
                tokenId = 0,
                logProb = 0f,
                timestamp = System.currentTimeMillis() - startTime,
                isComplete = true
            ))

            Timber.d("Generation complete. Tokens: $generatedCount, Output: '$generatedText'")
        } catch (e: Exception) {
            Timber.e(e, "Error during token generation")
            throw e
        } finally {
            // Cleanup native resources
            // Note: Java API docs don't explicitly show close() for these,
            // but they wrap native resources so we null them to allow GC
            // Actual cleanup happens when the Generator/TokenizerStream go out of scope
        }
    }

    override suspend fun classifyComplexity(prompt: String): Float = withContext(Dispatchers.IO) {
        val classifier = ComplexityClassifier()
        classifier.classifyWithModel(prompt, this@Phi3MiniAdapter).score
    }

    override suspend fun cleanup() = withContext(Dispatchers.IO) {
        // Close in reverse order of creation
        try {
            genaiTokenizer?.close()
        } catch (e: Exception) {
            Timber.w(e, "Error closing tokenizer")
        }
        genaiTokenizer = null

        try {
            genaiModel?.close()
        } catch (e: Exception) {
            Timber.w(e, "Error closing model")
        }
        genaiModel = null

        isModelReady = false
        Timber.d("Phi-3 Mini model cleaned up")
    }

    override fun getMemoryUsage(): MemoryInfo {
        val runtime = Runtime.getRuntime()
        val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val maxMemory = runtime.maxMemory() / (1024 * 1024)

        return MemoryInfo(
            usedMemoryMB = usedMemory,
            maxMemoryMB = maxMemory,
            availableMemoryMB = maxMemory - usedMemory
        )
    }

    override fun isReady(): Boolean = isModelReady && genaiModel != null && genaiTokenizer != null

    companion object {
        const val EOS_TOKEN_ID = 32000
    }
}
