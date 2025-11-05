package com.palmabrahma.smallmodeltest.models

import ai.onnxruntime.*
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.system.measureTimeMillis

/**
 * Phi-3 Mini model adapter using ONNX Runtime
 */
class Phi3MiniAdapter(
    private val context: Context
) : BaseModelAdapter {
    
    override val modelName = "Phi-3 Mini 4K Instruct"
    override val modelSizeMB = 1800
    override val parametersCount = "3.8B"
    override val quantizationType = "INT4"
    
    private var ortEnvironment: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var tokenizer: Phi3Tokenizer? = null
    
    private val maxContextLength = 4096
    private var isModelReady = false
    
    private val modelManager = ModelManager(context)
    
    override suspend fun initialize(): Long = withContext(Dispatchers.IO) {
        val loadTime = measureTimeMillis {
            try {
                Timber.d("Initializing Phi-3 Mini model...")
                
                // Check if model is downloaded
                if (!modelManager.isModelDownloaded(ModelType.PHI3_MINI)) {
                    throw IllegalStateException("Phi-3 model not downloaded. Please download first.")
                }
                
                // Initialize ONNX Runtime environment
                ortEnvironment = OrtEnvironment.getEnvironment()
                
                // Create session options with optimizations
                val sessionOptions = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(4)
                    addConfigEntry("session.intra_op.allow_spinning", "1")
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    
                    // Enable mobile-specific optimizations
                    addConfigEntry("session.use_env_allocators", "1")
                }
                
                // Get model file from ModelManager
                val modelFile = modelManager.getModelFilePath(ModelType.PHI3_MINI)
                if (!modelFile.exists()) {
                    throw IllegalStateException("Model file not found: ${modelFile.absolutePath}")
                }
                
                ortSession = ortEnvironment?.createSession(
                    modelFile.absolutePath,
                    sessionOptions
                )
                
                // Log model input requirements for debugging
                ortSession?.inputInfo?.forEach { (name, nodeInfo) ->
                    when (nodeInfo.info) {
                        is MapInfo -> {
                            val mapInfo = nodeInfo.info as MapInfo
                            Timber.d("Model expects input: $name with [ ${mapInfo.keyType}, ${mapInfo.valueType}]")
                        }
                        is SequenceInfo -> {
                            val sequenceInfo = nodeInfo.info as SequenceInfo
                            Timber.d("Model expects input: $name with sequenceType: ${sequenceInfo.sequenceType}")
                        }
                        is TensorInfo -> {
                            val tensorInfo = nodeInfo.info as TensorInfo
                            Timber.d("Model expects input: $name with shape: ${tensorInfo.shape?.contentToString()}")
                        }
                    }
                }
                
                // Initialize tokenizer
                val modelDir = modelManager.getModelDirectory(ModelType.PHI3_MINI)
                val tokenizerFile = File(modelDir, ModelManager.PHI3_TOKENIZER_NAME)
                tokenizer = Phi3Tokenizer(context, tokenizerFile.absolutePath)
                
                isModelReady = true
                Timber.d("Phi-3 Mini model initialized successfully")
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize Phi-3 Mini model")
                throw e
            }
        }
        loadTime
    }
    
    override fun generateTokens(
        prompt: String,
        maxTokens: Int,
        temperature: Float
    ): Flow<GenerationResult> = flow {
        require(isModelReady) { "Model not initialized" }

        var pastKVCache: Map<String, OnnxTensor>? = null

        try {
            // Tokenize input
            val inputTokens = tokenizer?.encode(prompt)
                ?: throw IllegalStateException("Tokenizer not initialized")

            // Initialize generation state
            var generatedTokens = 0
            var isComplete = false
            val startTime = System.currentTimeMillis()
            var currentTokens = inputTokens.toMutableList()

            // Generation loop
            while (generatedTokens < maxTokens && !isComplete) {
                // Prepare input tensors (first iteration has empty KV cache)
                val inputTensors = createInputTensors(currentTokens, pastKVCache)

                // Run inference
                val outputs = ortSession?.run(inputTensors)
                    ?: throw IllegalStateException("Session not initialized")

                // Clean up input tensors
                // On first iteration (pastKVCache == null), we created new empty KV cache tensors - close them
                // On subsequent iterations, we reused KV cache tensors - don't close them (they're in pastKVCache)
                if (pastKVCache == null) {
                    // First iteration: close all input tensors including the empty KV cache
                    inputTensors.values.forEach { it.close() }
                } else {
                    // Subsequent iterations: only close the newly created tensors
                    inputTensors["input_ids"]?.close()
                    inputTensors["attention_mask"]?.close()
                    // Don't close KV cache - they're reused from pastKVCache
                }

                // Extract new KV cache for next iteration
                // Close old KV cache before replacing (will be null on first iteration)
                pastKVCache?.values?.forEach { it.close() }
                pastKVCache = extractKVCache(outputs)

                // Process output logits
                val logitsTensor = outputs["logits"]?.get() as OnnxTensor
                val logitsBuffer = logitsTensor.floatBuffer
                val logitsShape = logitsTensor.info.shape // [batch, sequence, vocab_size]

                // Extract logits for the last token only
                val vocabSize = logitsShape[2].toInt()  // 32064 for Phi-3
                val sequenceLength = logitsShape[1].toInt()

                // Get the last token's logits (last vocab_size elements)
                val lastTokenLogits = FloatArray(vocabSize)
                val startIdx = (sequenceLength - 1) * vocabSize
                logitsBuffer.position(startIdx)
                logitsBuffer.get(lastTokenLogits)

                val nextTokenId = sampleToken(lastTokenLogits, temperature)

                // Decode token
                val token = tokenizer?.decode(listOf(nextTokenId)) ?: ""

                // Check for end of sequence
                if (nextTokenId == tokenizer?.eosTokenId) {
                    isComplete = true
                }

                // Log generated token
                Timber.d("Token #$generatedTokens: ID=$nextTokenId, Text='$token', Complete=$isComplete")

                // Emit result
                emit(GenerationResult(
                    token = token,
                    tokenId = nextTokenId,
                    logProb = 0f, // TODO: Calculate actual log probability
                    timestamp = System.currentTimeMillis() - startTime,
                    isComplete = isComplete
                ))

                generatedTokens++

                // For next iteration, only process the new token
                currentTokens = mutableListOf(nextTokenId)
            }

        } catch (e: Exception) {
            Timber.e(e, "Error during token generation")
            throw e
        } finally {
            // Clean up KV cache tensors
            pastKVCache?.values?.forEach { it.close() }
        }
    }
    
    override suspend fun classifyComplexity(prompt: String): Float = withContext(Dispatchers.IO) {
        // Simple heuristic for complexity classification
        // In production, this would use the model itself
        val wordCount = prompt.split(" ").size
        val hasCode = prompt.contains("```") || prompt.contains("function") || prompt.contains("def ")
        val hasMath = prompt.contains("solve") || prompt.contains("calculate") || prompt.contains("equation")
        val hasAnalysis = prompt.contains("analyze") || prompt.contains("explain") || prompt.contains("compare")
        
        var score = 0.1f
        
        // Word count factor
        score += minOf(wordCount / 100f, 0.3f)
        
        // Content type factors
        if (hasCode) score += 0.3f
        if (hasMath) score += 0.2f
        if (hasAnalysis) score += 0.2f
        
        // Length factor for very short prompts
        if (wordCount < 5) score = 0.1f
        if (prompt.length < 10) score = 0.05f
        
        minOf(score, 1.0f)
    }
    
    override suspend fun cleanup() = withContext(Dispatchers.IO) {
        try {
            ortSession?.close()
            ortSession = null
            ortEnvironment?.close()
            ortEnvironment = null
            tokenizer = null
            isModelReady = false
            Timber.d("Phi-3 Mini model cleaned up")
        } catch (e: Exception) {
            Timber.e(e, "Error during cleanup")
        }
    }
    
    override fun getMemoryUsage(): MemoryInfo {
        val runtime = Runtime.getRuntime()
        val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val maxMemory = runtime.maxMemory() / (1024 * 1024)
        val availableMemory = maxMemory - usedMemory
        
        return MemoryInfo(
            usedMemoryMB = usedMemory,
            maxMemoryMB = maxMemory,
            availableMemoryMB = availableMemory
        )
    }
    
    override fun isReady(): Boolean = isModelReady
    
    /**
     * Create input tensors based on what the model expects
     * Different ONNX exports may have different input requirements
     */
    private fun createInputTensorsFlexible(tokens: List<Int>): MutableMap<String, OnnxTensor> {
        val batchSize = 1L
        val sequenceLength = tokens.size.toLong()
        
        val tensors = mutableMapOf<String, OnnxTensor>()
        val modelInputs = ortSession?.inputInfo ?: throw IllegalStateException("Session not initialized")
        
        try {
            // Check what inputs the model expects and provide them
            modelInputs.forEach { (inputName, inputInfo) ->
                Timber.d("Creating tensor for input: $inputName")
                
                when (inputName) {
                    "input_ids" -> {
                        val shape = longArrayOf(batchSize, sequenceLength)
                        val buffer = LongBuffer.allocate(tokens.size)
                        tokens.forEach { buffer.put(it.toLong()) }
                        buffer.flip()
                        tensors[inputName] = OnnxTensor.createTensor(ortEnvironment, buffer, shape)
                    }
                    
                    "attention_mask" -> {
                        val shape = longArrayOf(batchSize, sequenceLength)
                        val buffer = LongBuffer.allocate(tokens.size)
                        repeat(tokens.size) { buffer.put(1L) } // All 1s for real tokens
                        buffer.flip()
                        tensors[inputName] = OnnxTensor.createTensor(ortEnvironment, buffer, shape)
                    }
                    
                    "position_ids" -> {
                        val shape = longArrayOf(batchSize, sequenceLength)
                        val buffer = LongBuffer.allocate(tokens.size)
                        for (i in tokens.indices) {
                            buffer.put(i.toLong())
                        }
                        buffer.flip()
                        tensors[inputName] = OnnxTensor.createTensor(ortEnvironment, buffer, shape)
                    }
                    
                    "token_type_ids" -> {
                        val shape = longArrayOf(batchSize, sequenceLength)
                        val buffer = LongBuffer.allocate(tokens.size)
                        repeat(tokens.size) { buffer.put(0L) } // All 0s for single sequence
                        buffer.flip()
                        tensors[inputName] = OnnxTensor.createTensor(ortEnvironment, buffer, shape)
                    }
                    
                    else -> {
                        Timber.w("Unknown input required by model: $inputName")
                        // For any other inputs, try to create a tensor with zeros
                        // This is a fallback and may not work for all models
                        val shape = longArrayOf(batchSize, sequenceLength)
                        val buffer = LongBuffer.allocate(tokens.size)
                        repeat(tokens.size) { buffer.put(0L) }
                        buffer.flip()
                        tensors[inputName] = OnnxTensor.createTensor(ortEnvironment, buffer, shape)
                    }
                }
            }
            
            Timber.d("Created ${tensors.size} input tensors for model")
            
        } catch (e: Exception) {
            // Clean up any created tensors if there's an error
            tensors.values.forEach { it.close() }
            throw Exception("Failed to create input tensors: ${e.message}", e)
        }
        
        return tensors
    }
    
    private fun createInputTensors(
        tokens: List<Int>,
        pastKVCache: Map<String, OnnxTensor>? = null
    ): MutableMap<String, OnnxTensor> {
        val batchSize = 1L
        val sequenceLength = tokens.size.toLong()

        val tensors = mutableMapOf<String, OnnxTensor>()

        try {
            // 1. Input IDs tensor
            val inputIdsShape = longArrayOf(batchSize, sequenceLength)
            val inputIdsBuffer = LongBuffer.allocate(tokens.size)
            tokens.forEach { inputIdsBuffer.put(it.toLong()) }
            inputIdsBuffer.flip()
            tensors["input_ids"] = OnnxTensor.createTensor(ortEnvironment, inputIdsBuffer, inputIdsShape)

            // 2. Attention mask (1 for all real tokens, 0 for padding)
            val attentionMaskShape = longArrayOf(batchSize, sequenceLength)
            val attentionMaskBuffer = LongBuffer.allocate(tokens.size)
            repeat(tokens.size) { attentionMaskBuffer.put(1L) }
            attentionMaskBuffer.flip()
            tensors["attention_mask"] = OnnxTensor.createTensor(ortEnvironment, attentionMaskBuffer, attentionMaskShape)

            // 3. Add KV cache for all 32 layers
            // Phi-3 Mini has 32 transformer layers
            val numLayers = 32
            val numKeyValueHeads = 32  // Phi-3 Mini uses 32 KV heads
            val headDim = 96  // Phi-3 Mini head dimension

            for (layer in 0 until numLayers) {
                if (pastKVCache != null) {
                    // Reuse previous KV cache
                    val keyName = "past_key_values.$layer.key"
                    val valueName = "past_key_values.$layer.value"

                    pastKVCache[keyName]?.let { tensors[keyName] = it }
                    pastKVCache[valueName]?.let { tensors[valueName] = it }
                } else {
                    // First iteration: create empty KV cache
                    // Shape: [batch_size, num_kv_heads, 0, head_dim]
                    val emptyKVShape = longArrayOf(batchSize, numKeyValueHeads.toLong(), 0L, headDim.toLong())
                    val emptyBuffer = FloatBuffer.allocate(0)

                    tensors["past_key_values.$layer.key"] =
                        OnnxTensor.createTensor(ortEnvironment, emptyBuffer, emptyKVShape)
                    tensors["past_key_values.$layer.value"] =
                        OnnxTensor.createTensor(ortEnvironment, emptyBuffer, emptyKVShape)
                }
            }

        } catch (e: Exception) {
            // Clean up any created tensors if there's an error
            tensors.values.forEach { it.close() }
            throw e
        }

        return tensors
    }

    /**
     * Extract KV cache from model outputs for next iteration
     */
    private fun extractKVCache(outputs: OrtSession.Result): Map<String, OnnxTensor> {
        val kvCache = mutableMapOf<String, OnnxTensor>()

        val numLayers = 32
        for (layer in 0 until numLayers) {
            val keyName = "present.$layer.key"
            val valueName = "present.$layer.value"

            // Get the output tensor and convert to new input tensor
            outputs[keyName]?.let { onnxValue ->
                val tensor = onnxValue.get() as OnnxTensor
                // Create new tensor from the data to use as input in next iteration
                val floatData = tensor.floatBuffer
                val shape = tensor.info.shape
                kvCache["past_key_values.$layer.key"] = OnnxTensor.createTensor(ortEnvironment, floatData, shape)
            }

            outputs[valueName]?.let { onnxValue ->
                val tensor = onnxValue.get() as OnnxTensor
                val floatData = tensor.floatBuffer
                val shape = tensor.info.shape
                kvCache["past_key_values.$layer.value"] = OnnxTensor.createTensor(ortEnvironment, floatData, shape)
            }
        }

        return kvCache
    }
    
    private fun sampleToken(logits: FloatArray, temperature: Float): Int {
        // Simple temperature-based sampling
        // In production, implement proper sampling strategies
        val vocabSize = logits.size
        val probabilities = FloatArray(vocabSize)

        // Apply temperature and softmax
        var maxLogit = Float.NEGATIVE_INFINITY
        for (i in 0 until vocabSize) {
            val logit = logits[i] / temperature
            probabilities[i] = logit
            if (logit > maxLogit) maxLogit = logit
        }

        // Compute softmax
        var sumExp = 0f
        for (i in probabilities.indices) {
            probabilities[i] = kotlin.math.exp(probabilities[i] - maxLogit)
            sumExp += probabilities[i]
        }

        for (i in probabilities.indices) {
            probabilities[i] /= sumExp
        }

        // Sample from distribution
        val random = kotlin.random.Random.nextFloat()
        var cumulativeProb = 0f
        for (i in probabilities.indices) {
            cumulativeProb += probabilities[i]
            if (random < cumulativeProb) {
                return i
            }
        }

        return vocabSize - 1
    }
}

/**
 * Simple tokenizer for Phi-3
 * In production, use a proper tokenizer implementation
 */
class Phi3Tokenizer(
    private val context: Context,
    private val tokenizerPath: String
) {
    val eosTokenId = 2
    private val bosTokenId = 1
    private val padTokenId = 0
    
    fun encode(text: String): MutableList<Int> {
        // TODO: Implement proper tokenization
        // For now, return dummy tokens
        return mutableListOf(bosTokenId, 100, 101, 102)
    }
    
    fun decode(tokens: List<Int>): String {
        // TODO: Implement proper decoding
        return " token"
    }
}
