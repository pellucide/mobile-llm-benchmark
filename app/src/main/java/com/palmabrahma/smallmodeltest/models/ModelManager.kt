package com.palmabrahma.smallmodeltest.models

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Manages model files in app-specific storage
 * No permissions required for internal storage
 */
class ModelManager(private val context: Context) {
    
    companion object {
        // Model file names
        const val PHI3_MODEL_NAME = "phi3-mini-4k-instruct-cpu-int4-rtn-block-32-acc-level-4.onnx"
        const val PHI3_MODEL_DATA_NAME = "phi3-mini-4k-instruct-cpu-int4-rtn-block-32-acc-level-4.onnx.data"
        const val PHI3_TOKENIZER_NAME = "tokenizer.json"
        const val PHI3_TOKENIZER_MODEL_NAME = "tokenizer.model"
        const val PHI3_CONFIG_NAME = "tokenizer_config.json"
        const val PHI3_SPECIAL_TOKENS_NAME = "special_tokens_map.json"
        
        const val GEMMA_MODEL_NAME = "gemma-2b-q4.gguf"
        const val TINYLLAMA_MODEL_NAME = "tinyllama-1.1b-q4.gguf"
        
        // HuggingFace repository base URL
        private const val HF_BASE_URL = "https://huggingface.co"
        
        // Download URLs for Phi-3 (using the acc-level-4 version for better mobile performance)
        const val PHI3_MODEL_URL = "$HF_BASE_URL/microsoft/Phi-3-mini-4k-instruct-onnx/resolve/main/cpu_and_mobile/cpu-int4-rtn-block-32-acc-level-4/phi3-mini-4k-instruct-cpu-int4-rtn-block-32-acc-level-4.onnx"
        const val PHI3_MODEL_DATA_URL = "$HF_BASE_URL/microsoft/Phi-3-mini-4k-instruct-onnx/resolve/main/cpu_and_mobile/cpu-int4-rtn-block-32-acc-level-4/phi3-mini-4k-instruct-cpu-int4-rtn-block-32-acc-level-4.onnx.data"
        const val PHI3_TOKENIZER_URL = "$HF_BASE_URL/microsoft/Phi-3-mini-4k-instruct-onnx/resolve/main/cpu_and_mobile/cpu-int4-rtn-block-32-acc-level-4/tokenizer.json"
        const val PHI3_TOKENIZER_MODEL_URL = "$HF_BASE_URL/microsoft/Phi-3-mini-4k-instruct-onnx/resolve/main/cpu_and_mobile/cpu-int4-rtn-block-32-acc-level-4/tokenizer.model"
        const val PHI3_CONFIG_URL = "$HF_BASE_URL/microsoft/Phi-3-mini-4k-instruct-onnx/resolve/main/cpu_and_mobile/cpu-int4-rtn-block-32-acc-level-4/tokenizer_config.json"
        const val PHI3_SPECIAL_TOKENS_URL = "$HF_BASE_URL/microsoft/Phi-3-mini-4k-instruct-onnx/resolve/main/cpu_and_mobile/cpu-int4-rtn-block-32-acc-level-4/special_tokens_map.json"
        
        // Directory names
        private const val MODELS_DIR = "models"
        private const val PHI3_DIR = "phi3"
        private const val GEMMA_DIR = "gemma"
        private const val TINYLLAMA_DIR = "tinyllama"
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    
    data class DownloadProgress(
        val fileName: String,
        val currentFileIndex: Int,
        val totalFiles: Int,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val percentComplete: Float,
        val overallPercentComplete: Float,
        val isComplete: Boolean,
        val currentOperation: String = ""
    )
    
    /**
     * Get the models directory in app-specific storage
     * Path: /data/data/com.llmtest.hybrid/files/models/
     */
    fun getModelsDirectory(): File {
        val modelsDir = File(context.filesDir, MODELS_DIR)
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
        return modelsDir
    }
    
    /**
     * Get directory for a specific model
     */
    fun getModelDirectory(modelType: ModelType): File {
        val modelsDir = getModelsDirectory()
        val modelDir = when (modelType) {
            ModelType.PHI3_MINI -> File(modelsDir, PHI3_DIR)
            ModelType.GEMMA_2B -> File(modelsDir, GEMMA_DIR)
            ModelType.TINY_LLAMA -> File(modelsDir, TINYLLAMA_DIR)
        }
        if (!modelDir.exists()) {
            modelDir.mkdirs()
        }
        return modelDir
    }
    
    /**
     * Check if a model is already downloaded
     */
    fun isModelDownloaded(modelType: ModelType): Boolean {
        return when (modelType) {
            ModelType.PHI3_MINI -> {
                val modelDir = getModelDirectory(modelType)
                val modelFile = File(modelDir, PHI3_MODEL_NAME)
                val modelDataFile = File(modelDir, PHI3_MODEL_DATA_NAME)
                val tokenizerFile = File(modelDir, PHI3_TOKENIZER_NAME)
                val tokenizerModelFile = File(modelDir, PHI3_TOKENIZER_MODEL_NAME)
                
                // Check if all essential files exist
                modelFile.exists() && 
                modelDataFile.exists() && 
                tokenizerFile.exists() && 
                tokenizerModelFile.exists()
            }
            ModelType.GEMMA_2B -> {
                val modelDir = getModelDirectory(modelType)
                File(modelDir, GEMMA_MODEL_NAME).exists()
            }
            ModelType.TINY_LLAMA -> {
                val modelDir = getModelDirectory(modelType)
                File(modelDir, TINYLLAMA_MODEL_NAME).exists()
            }
        }
    }
    
    /**
     * Get path to model file
     */
    fun getModelFilePath(modelType: ModelType): File {
        val modelDir = getModelDirectory(modelType)
        return when (modelType) {
            ModelType.PHI3_MINI -> File(modelDir, PHI3_MODEL_NAME)
            ModelType.GEMMA_2B -> File(modelDir, GEMMA_MODEL_NAME)
            ModelType.TINY_LLAMA -> File(modelDir, TINYLLAMA_MODEL_NAME)
        }
    }
    
    /**
     * Download a model with progress updates
     */
    fun downloadModel(modelType: ModelType): Flow<DownloadProgress> = flow {
        when (modelType) {
            ModelType.PHI3_MINI -> {
                downloadPhi3Model()
            }
            ModelType.GEMMA_2B -> {
                // TODO: Add actual Gemma download URLs
                throw NotImplementedError("Gemma download not yet implemented")
            }
            ModelType.TINY_LLAMA -> {
                // TODO: Add actual TinyLlama download URLs
                throw NotImplementedError("TinyLlama download not yet implemented")
            }
        }.collect { progress ->
            emit(progress)
        }
    }
    
    /**
     * Download Phi-3 model with all required files
     */
    private fun downloadPhi3Model(): Flow<DownloadProgress> = flow {
        val modelDir = getModelDirectory(ModelType.PHI3_MINI)
        
        // Define all files to download with their URLs and sizes (approximate)
        data class FileToDownload(
            val url: String,
            val fileName: String,
            val displayName: String,
            val approximateSizeMB: Long
        )
        
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
                PHI3_SPECIAL_TOKENS_URL, 
                PHI3_SPECIAL_TOKENS_NAME, 
                "Special Tokens",
                1
            )
        )
        
        val totalFiles = filesToDownload.size
        var completedFiles = 0
        
        // Calculate total approximate size for overall progress
        val totalApproximateSizeMB = filesToDownload.sumOf { it.approximateSizeMB }
        var totalBytesDownloaded = 0L
        
        for ((index, fileInfo) in filesToDownload.withIndex()) {
            val currentFileNumber = index + 1
            
            // Emit starting download for this file
            emit(DownloadProgress(
                fileName = fileInfo.displayName,
                currentFileIndex = currentFileNumber,
                totalFiles = totalFiles,
                bytesDownloaded = 0,
                totalBytes = fileInfo.approximateSizeMB * 1024 * 1024,
                percentComplete = 0f,
                overallPercentComplete = (completedFiles.toFloat() / totalFiles) * 100f,
                isComplete = false,
                currentOperation = "Starting download ${currentFileNumber}/$totalFiles: ${fileInfo.displayName}"
            ))
            
            try {
                // Download the file
                downloadSingleFile(
                    url = fileInfo.url,
                    destination = File(modelDir, fileInfo.fileName)
                ).collect { fileProgress ->
                    // Calculate overall progress
                    val filesProgress = completedFiles.toFloat() / totalFiles
                    val currentFileProgress = (fileProgress.percentComplete / 100f) / totalFiles
                    val overallProgress = (filesProgress + currentFileProgress) * 100f
                    
                    emit(DownloadProgress(
                        fileName = fileInfo.displayName,
                        currentFileIndex = currentFileNumber,
                        totalFiles = totalFiles,
                        bytesDownloaded = fileProgress.bytesDownloaded,
                        totalBytes = fileProgress.totalBytes,
                        percentComplete = fileProgress.percentComplete,
                        overallPercentComplete = overallProgress,
                        isComplete = false,
                        currentOperation = "Downloading ($currentFileNumber/$totalFiles): ${fileInfo.displayName}"
                    ))
                    
                    if (fileProgress.percentComplete >= 100f) {
                        completedFiles++
                        totalBytesDownloaded += fileProgress.totalBytes
                    }
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Error downloading ${fileInfo.displayName}")
                throw IOException("Failed to download ${fileInfo.displayName}: ${e.message}")
            }
        }
        
        // Emit final completion status
        emit(DownloadProgress(
            fileName = "All files",
            currentFileIndex = totalFiles,
            totalFiles = totalFiles,
            bytesDownloaded = totalBytesDownloaded,
            totalBytes = totalBytesDownloaded,
            percentComplete = 100f,
            overallPercentComplete = 100f,
            isComplete = true,
            currentOperation = "Download complete! Model ready to use."
        ))
    }
    
    /**
     * Download a single file with progress tracking
     */
    private data class SingleFileProgress(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val percentComplete: Float
    )
    
    /**
     * Download a single file
     */
    private fun downloadSingleFile(
        url: String,
        destination: File
    ): Flow<SingleFileProgress> = flow {
        //withContext(Dispatchers.IO) {
            try {
                Timber.d("Downloading from: $url")
                
                val request = Request.Builder()
                    .url(url)
                    .build()
                
                val response = okHttpClient.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    throw IOException("Failed to download: ${response.code}")
                }
                
                val body = response.body ?: throw IOException("Empty response body")
                val contentLength = body.contentLength()
                
                // Create parent directories if needed
                destination.parentFile?.mkdirs()
                
                // Write to file with progress updates
                body.byteStream().use { input ->
                    FileOutputStream(destination).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalBytesRead = 0L
                        
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            
                            val progress = if (contentLength > 0) {
                                (totalBytesRead.toFloat() / contentLength) * 100
                            } else {
                                0f
                            }
                            
                            emit(SingleFileProgress(
                                bytesDownloaded = totalBytesRead,
                                totalBytes = contentLength,
                                percentComplete = progress
                            ))
                        }
                    }
                }
                
                Timber.d("Download complete: ${destination.name}")
                
            } catch (e: Exception) {
                // Clean up partial file on error
                if (destination.exists()) {
                    destination.delete()
                }
                throw e
            }
        //}
    }.flowOn(Dispatchers.IO)
    
    /**
     * Delete a model to free up space
     */
    fun deleteModel(modelType: ModelType): Boolean {
        return try {
            val modelDir = getModelDirectory(modelType)
            modelDir.deleteRecursively()
        } catch (e: Exception) {
            Timber.e(e, "Error deleting model: $modelType")
            false
        }
    }
    
    /**
     * Get size of downloaded model in MB
     */
    fun getModelSize(modelType: ModelType): Long {
        val modelDir = getModelDirectory(modelType)
        return if (modelDir.exists()) {
            modelDir.walkTopDown()
                .filter { it.isFile }
                .map { it.length() }
                .sum() / (1024 * 1024) // Convert to MB
        } else {
            0L
        }
    }
    
    /**
     * Get total storage used by all models
     */
    fun getTotalStorageUsed(): Long {
        val modelsDir = getModelsDirectory()
        return if (modelsDir.exists()) {
            modelsDir.walkTopDown()
                .filter { it.isFile }
                .map { it.length() }
                .sum() / (1024 * 1024) // Convert to MB
        } else {
            0L
        }
    }
    
    /**
     * Get available storage in app's internal storage
     */
    fun getAvailableStorage(): Long {
        return context.filesDir.usableSpace / (1024 * 1024) // Convert to MB
    }
    
    /**
     * Check if there's enough space for a model
     */
    fun hasEnoughSpace(modelType: ModelType): Boolean {
        val requiredSpace = when (modelType) {
            ModelType.PHI3_MINI -> 2000L // 2GB
            ModelType.GEMMA_2B -> 1000L  // 1GB
            ModelType.TINY_LLAMA -> 500L  // 500MB
        }
        return getAvailableStorage() > requiredSpace
    }
}

enum class ModelType(
    val displayName: String,
    val parameters: String,
    val sizeMB: Int
) {
    PHI3_MINI("Phi-3 Mini", "3.8B", 1800),
    GEMMA_2B("Gemma 2B", "2B", 800),
    TINY_LLAMA("TinyLlama", "1.1B", 350)
}
