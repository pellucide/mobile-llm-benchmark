package com.palmabrahma.smallmodeltest.models

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Advanced complexity classifier for routing decisions
 * Can use either heuristics or LLM-based classification
 */
class ComplexityClassifier {

    /**
     * Classification result with detailed scoring
     */
    data class ClassificationResult(
        val score: Float,
        val level: ComplexityLevel,
        val confidence: Float,
        val features: FeatureScores
    )

    /**
     * Individual feature scores for transparency
     */
    data class FeatureScores(
        val lengthScore: Float,
        val vocabularyScore: Float,
        val structureScore: Float,
        val domainScore: Float,
        val questionTypeScore: Float,
        val technicalScore: Float
    )

    /**
     * Classify using enhanced heuristics (fast, no model needed)
     */
    suspend fun classifyWithHeuristics(prompt: String): ClassificationResult = withContext(Dispatchers.Default) {
        val lowerPrompt = prompt.lowercase()
        val words = prompt.split(Regex("\\s+"))
        val wordCount = words.size
        val uniqueWords = words.map { it.lowercase().trim() }.toSet().size

        val features = extractFeatures(prompt, lowerPrompt, words, wordCount, uniqueWords)
        val finalScore = calculateWeightedScore(features, wordCount, lowerPrompt)

        val level = scoreToLevel(finalScore)
        val confidence = calculateConfidence(finalScore, level)

        ClassificationResult(
            score = finalScore,
            level = level,
            confidence = confidence,
            features = features
        )
    }

    /**
     * Classify using the LLM itself (more accurate but slower)
     * This would be implemented when the model is fully functional
     */
    suspend fun classifyWithModel(
        prompt: String,
        model: BaseModelAdapter
    ): ClassificationResult = withContext(Dispatchers.IO) {
        // Create a meta-prompt for complexity classification
        val classificationPrompt = """
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

        try {
            // This would use the model to generate a complexity score
            // For now, fall back to heuristics
            Timber.d("Model-based classification not yet implemented, using heuristics")
            return@withContext classifyWithHeuristics(prompt)
        } catch (e: Exception) {
            Timber.e(e, "Model classification failed, falling back to heuristics")
            return@withContext classifyWithHeuristics(prompt)
        }
    }

    /**
     * Hybrid approach: Use heuristics first, then refine with model if needed
     */
    suspend fun classifyHybrid(
        prompt: String,
        model: BaseModelAdapter? = null,
        useModelThreshold: Float = 0.5f
    ): ClassificationResult = withContext(Dispatchers.IO) {
        // First pass with heuristics
        val heuristicResult = classifyWithHeuristics(prompt)

        Timber.d("heuristics = $heuristicResult")
        // If confidence is low and model is available, use model for refinement
        if (model != null && heuristicResult.confidence < useModelThreshold) {
            try {
                Timber.d("Using model $model to classify because confidence from heuristics is ${heuristicResult.confidence} < $useModelThreshold(useModelThreshold)")
                return@withContext classifyWithModel(prompt, model)
            } catch (e: Exception) {
                Timber.w("Model refinement failed, using heuristic result")
            }
        }

        heuristicResult
    }

    private fun extractFeatures(
        prompt: String,
        lowerPrompt: String,
        words: List<String>,
        wordCount: Int,
        uniqueWords: Int
    ): FeatureScores {
        var lengthScore = 0f
        var vocabularyScore = 0f
        var structureScore = 0f
        var domainScore = 0f
        var questionTypeScore = 0f
        var technicalScore = 0f

        // 1. Length-based complexity
        lengthScore = when {
            wordCount < 3 -> 0.02f
            wordCount < 5 -> 0.05f
            wordCount < 10 -> 0.1f
            wordCount < 20 -> 0.15f
            wordCount < 50 -> 0.18f
            else -> 0.2f
        }

        // 2. Vocabulary diversity
        val uniquenessRatio = if (wordCount > 0) uniqueWords.toFloat() / wordCount else 0f
        vocabularyScore = minOf(uniquenessRatio * 0.2f, 0.15f)

        // Check for advanced vocabulary
        val advancedWords = ADVANCED_VOCABULARY.count { lowerPrompt.contains(it) }
        vocabularyScore += minOf(advancedWords * 0.02f, 0.05f)

        // 3. Structural complexity
        val sentenceCount = prompt.split(Regex("[.!?]+")).filter { it.isNotBlank() }.size
        val hasSubClauses = prompt.contains(",") || prompt.contains(";") || prompt.contains(" - ")
        val hasConditionals = CONDITIONAL_INDICATORS.any { lowerPrompt.contains(it) }
        val hasComparisons = COMPARISON_INDICATORS.any { lowerPrompt.contains(it) }

        structureScore = when (sentenceCount) {
            1 -> 0.02f
            2 -> 0.05f
            else -> 0.08f
        }
        if (hasSubClauses) structureScore += 0.03f
        if (hasConditionals) structureScore += 0.02f
        if (hasComparisons) structureScore += 0.02f

        // 4. Domain-specific complexity
        if (CODE_INDICATORS.any { lowerPrompt.contains(it) }) domainScore += 0.15f
        if (MATH_INDICATORS.any { lowerPrompt.contains(it) }) domainScore += 0.12f
        if (SCIENCE_INDICATORS.any { lowerPrompt.contains(it) }) domainScore += 0.1f
        if (BUSINESS_INDICATORS.any { lowerPrompt.contains(it) }) domainScore += 0.08f
        domainScore = minOf(domainScore, 0.25f)

        // 5. Question type complexity
        questionTypeScore = detectQuestionType(lowerPrompt)

        // 6. Technical indicators
        if (prompt.contains(Regex("\\d+"))) technicalScore += 0.02f
        if (prompt.contains(Regex("[()\\[\\]{}@#$%^&*+=|\\\\/<>]"))) technicalScore += 0.02f
        if (words.count { it.length > 1 && it == it.uppercase() } > 0) technicalScore += 0.01f

        return FeatureScores(
            lengthScore = lengthScore,
            vocabularyScore = vocabularyScore,
            structureScore = structureScore,
            domainScore = domainScore,
            questionTypeScore = questionTypeScore,
            technicalScore = technicalScore
        )
    }

    private fun calculateWeightedScore(
        features: FeatureScores,
        wordCount: Int,
        lowerPrompt: String
    ): Float {
        // Weighted combination of features
        var finalScore = features.lengthScore * 1.0f +
                        features.vocabularyScore * 1.2f +
                        features.structureScore * 0.8f +
                        features.domainScore * 1.5f +
                        features.questionTypeScore * 1.3f +
                        features.technicalScore * 0.7f

        // Apply edge case adjustments
        if (wordCount < 3) {
            finalScore = minOf(finalScore, 0.15f)
        }
        if (wordCount == 1) {
            finalScore = 0.05f
        }
        if (GREETINGS.contains(lowerPrompt.trim())) {
            finalScore = 0.05f
        }

        return minOf(finalScore, 1.0f)
    }

    private fun scoreToLevel(score: Float): ComplexityLevel {
        return when {
            score < 0.15 -> ComplexityLevel.TRIVIAL
            score < 0.35 -> ComplexityLevel.SIMPLE
            score < 0.55 -> ComplexityLevel.MEDIUM
            score < 0.75 -> ComplexityLevel.COMPLEX
            else -> ComplexityLevel.EXPERT
        }
    }

    private fun calculateConfidence(score: Float, level: ComplexityLevel): Float {
        // Calculate confidence based on distance from threshold boundaries
        val thresholds = listOf(0.0f, 0.15f, 0.35f, 0.55f, 0.75f, 1.0f)
        val levelIndex = level.ordinal

        val lowerBound = thresholds[levelIndex]
        val upperBound = thresholds[levelIndex + 1]
        val midpoint = (lowerBound + upperBound) / 2

        // Confidence is highest at midpoint, lowest at boundaries
        val distanceFromMidpoint = kotlin.math.abs(score - midpoint)
        val maxDistance = (upperBound - lowerBound) / 2

        return if (maxDistance > 0) {
            1.0f - (distanceFromMidpoint / maxDistance) * 0.5f
        } else {
            0.5f
        }
    }

    private fun detectQuestionType(lowerPrompt: String): Float {
        return when {
            // Evaluative questions (highest complexity)
            EVALUATIVE_INDICATORS.any { lowerPrompt.contains(it) } -> 0.2f
            // Creative tasks
            CREATIVE_INDICATORS.any { lowerPrompt.contains(it) } -> 0.18f
            // Analytical questions
            ANALYTICAL_INDICATORS.any { lowerPrompt.contains(it) } -> 0.15f
            // How-to questions
            lowerPrompt.startsWith("how to") || lowerPrompt.startsWith("how do") -> 0.1f
            // Factual questions
            FACTUAL_STARTERS.any { lowerPrompt.startsWith(it) } -> 0.05f
            // Default
            else -> 0.08f
        }
    }

    /**
     * Standalone complexity classification function - the original implementation
     * Returns a float score between 0.0 and 1.0 indicating complexity
     */
    suspend fun classifyComplexity(prompt: String): Float = withContext(Dispatchers.IO) {
        // Enhanced complexity classification with multiple feature extraction
        val lowerPrompt = prompt.lowercase()
        val words = prompt.split(Regex("\\s+"))
        val wordCount = words.size
        val uniqueWords = words.map { it.lowercase().trim() }.toSet().size
        val avgWordLength = if (words.isNotEmpty()) {
            words.sumOf { it.length } / words.size.toFloat()
        } else 0f

        // Initialize score components
        var lengthScore = 0f
        var vocabularyScore = 0f
        var structureScore = 0f
        var domainScore = 0f
        var questionTypeScore = 0f
        var technicalScore = 0f

        // 1. Length-based complexity (0-0.2)
        lengthScore = when {
            wordCount < 3 -> 0.02f
            wordCount < 5 -> 0.05f
            wordCount < 10 -> 0.1f
            wordCount < 20 -> 0.15f
            wordCount < 50 -> 0.18f
            else -> 0.2f
        }

        // 2. Vocabulary diversity (0-0.15)
        val uniquenessRatio = if (wordCount > 0) uniqueWords.toFloat() / wordCount else 0f
        vocabularyScore = minOf(uniquenessRatio * 0.2f, 0.15f)

        // Advanced vocabulary indicators
        val advancedWords = setOf(
            "analyze", "evaluate", "synthesize", "hypothesize", "extrapolate",
            "contextualize", "demonstrate", "elaborate", "justify", "critique",
            "differentiate", "correlate", "optimize", "implement", "architect"
        )
        val advancedWordCount = words.count { it.lowercase() in advancedWords }
        vocabularyScore += minOf(advancedWordCount * 0.02f, 0.05f)

        // 3. Structural complexity (0-0.15)
        val sentenceCount = prompt.split(Regex("[.!?]+")).filter { it.isNotBlank() }.size
        val hasSubClauses = prompt.contains(",") || prompt.contains(";") || prompt.contains(" - ")
        val hasConditionals = lowerPrompt.contains("if ") || lowerPrompt.contains("when ") ||
                             lowerPrompt.contains("unless ") || lowerPrompt.contains("whether ")
        val hasComparisons = lowerPrompt.contains("compare") || lowerPrompt.contains("versus") ||
                            lowerPrompt.contains("vs") || lowerPrompt.contains("difference between")

        structureScore = when (sentenceCount) {
            1 -> 0.02f
            2 -> 0.05f
            else -> 0.08f
        }
        if (hasSubClauses) structureScore += 0.03f
        if (hasConditionals) structureScore += 0.02f
        if (hasComparisons) structureScore += 0.02f

        // 4. Domain-specific complexity (0-0.25)
        val codeIndicators = setOf(
            "function", "class", "method", "variable", "array", "loop",
            "algorithm", "recursion", "async", "promise", "api", "database",
            "sql", "debug", "compile", "runtime", "syntax", "refactor",
            "```", "def ", "fn ", "func ", "import", "export", "return"
        )
        val mathIndicators = setOf(
            "solve", "calculate", "equation", "formula", "derivative", "integral",
            "matrix", "vector", "probability", "statistics", "theorem", "proof",
            "polynomial", "exponential", "logarithm", "trigonometry"
        )
        val scienceIndicators = setOf(
            "hypothesis", "experiment", "theory", "quantum", "relativity",
            "molecule", "reaction", "ecosystem", "evolution", "genetics",
            "thermodynamics", "electromagnetism", "photosynthesis"
        )
        val businessIndicators = setOf(
            "strategy", "roi", "market analysis", "competitive advantage",
            "stakeholder", "synergy", "leverage", "portfolio", "revenue model",
            "scalability", "disruption", "value proposition"
        )

        val hasCode = codeIndicators.any { lowerPrompt.contains(it) }
        val hasMath = mathIndicators.any { lowerPrompt.contains(it) }
        val hasScience = scienceIndicators.any { lowerPrompt.contains(it) }
        val hasBusiness = businessIndicators.any { lowerPrompt.contains(it) }

        if (hasCode) domainScore += 0.15f
        if (hasMath) domainScore += 0.12f
        if (hasScience) domainScore += 0.1f
        if (hasBusiness) domainScore += 0.08f
        domainScore = minOf(domainScore, 0.25f)

        // 5. Question type complexity (0-0.2)
        val isFactual = lowerPrompt.startsWith("what is") || lowerPrompt.startsWith("who is") ||
                       lowerPrompt.startsWith("when did") || lowerPrompt.startsWith("where is")
        val isHowTo = lowerPrompt.startsWith("how to") || lowerPrompt.startsWith("how do")
        val isAnalytical = lowerPrompt.contains("why") || lowerPrompt.contains("how does") ||
                          lowerPrompt.contains("explain") || lowerPrompt.contains("analyze")
        val isCreative = lowerPrompt.contains("create") || lowerPrompt.contains("design") ||
                        lowerPrompt.contains("write") || lowerPrompt.contains("generate") ||
                        lowerPrompt.contains("imagine") || lowerPrompt.contains("invent")
        val isEvaluative = lowerPrompt.contains("evaluate") || lowerPrompt.contains("judge") ||
                          lowerPrompt.contains("critique") || lowerPrompt.contains("review") ||
                          lowerPrompt.contains("assess")

        questionTypeScore = when {
            isEvaluative -> 0.2f
            isCreative -> 0.18f
            isAnalytical -> 0.15f
            isHowTo -> 0.1f
            isFactual -> 0.05f
            else -> 0.08f // Default for statements or other types
        }

        // 6. Technical indicators (0-0.05)
        val hasNumbers = prompt.contains(Regex("\\d+"))
        val hasSpecialChars = prompt.contains(Regex("[()\\[\\]{}@#$%^&*+=|\\\\/<>]"))
        val hasAcronyms = words.count { it.length > 1 && it == it.uppercase() } > 0

        if (hasNumbers) technicalScore += 0.02f
        if (hasSpecialChars) technicalScore += 0.02f
        if (hasAcronyms) technicalScore += 0.01f

        // Calculate weighted final score
        var finalScore = lengthScore * 1.0f +
                        vocabularyScore * 1.2f +
                        structureScore * 0.8f +
                        domainScore * 1.5f +
                        questionTypeScore * 1.3f +
                        technicalScore * 0.7f

        // Apply adjustments for edge cases
        // Very short prompts should be capped
        if (wordCount < 3) {
            finalScore = minOf(finalScore, 0.15f)
        }
        // Single word prompts are always trivial
        if (wordCount == 1) {
            finalScore = 0.05f
        }
        // Greetings and simple acknowledgments are trivial
        val greetings = setOf("hi", "hello", "hey", "thanks", "thank you", "yes", "no", "ok", "okay")
        if (greetings.contains(lowerPrompt.trim())) {
            finalScore = 0.05f
        }

        // Log classification details for debugging
        Timber.v("""
            Complexity Classification for: "${prompt.take(50)}..."
            Word Count: $wordCount, Unique Words: $uniqueWords
            Scores: Length=$lengthScore, Vocab=$vocabularyScore, Structure=$structureScore
            Domain=$domainScore, Question=$questionTypeScore, Technical=$technicalScore
            Final Score: $finalScore
        """.trimIndent())

        minOf(finalScore, 1.0f)
    }

    companion object {
        // Vocabulary sets for classification
        private val ADVANCED_VOCABULARY = setOf(
            "analyze", "evaluate", "synthesize", "hypothesize", "extrapolate",
            "contextualize", "demonstrate", "elaborate", "justify", "critique",
            "differentiate", "correlate", "optimize", "implement", "architect",
            "paradigm", "methodology", "framework", "abstraction", "heuristic"
        )

        private val CODE_INDICATORS = setOf(
            "function", "class", "method", "variable", "array", "loop",
            "algorithm", "recursion", "async", "promise", "api", "database",
            "sql", "debug", "compile", "runtime", "syntax", "refactor",
            "```", "def ", "fn ", "func ", "import", "export", "return",
            "git", "branch", "commit", "merge", "deploy", "docker", "kubernetes"
        )

        private val MATH_INDICATORS = setOf(
            "solve", "calculate", "equation", "formula", "derivative", "integral",
            "matrix", "vector", "probability", "statistics", "theorem", "proof",
            "polynomial", "exponential", "logarithm", "trigonometry", "calculus",
            "algebra", "geometry", "arithmetic", "factorial", "permutation"
        )

        private val SCIENCE_INDICATORS = setOf(
            "hypothesis", "experiment", "theory", "quantum", "relativity",
            "molecule", "reaction", "ecosystem", "evolution", "genetics",
            "thermodynamics", "electromagnetism", "photosynthesis", "entropy",
            "catalyst", "oxidation", "periodic table", "atom", "neutron"
        )

        private val BUSINESS_INDICATORS = setOf(
            "strategy", "roi", "market analysis", "competitive advantage",
            "stakeholder", "synergy", "leverage", "portfolio", "revenue model",
            "scalability", "disruption", "value proposition", "kpi", "metrics",
            "forecast", "budget", "investment", "equity", "acquisition"
        )

        private val CONDITIONAL_INDICATORS = setOf(
            "if ", "when ", "unless ", "whether ", "provided that",
            "assuming ", "given that", "in case ", "should "
        )

        private val COMPARISON_INDICATORS = setOf(
            "compare", "versus", " vs ", "difference between",
            "contrast", "similar to", "unlike", "whereas", "although"
        )

        private val EVALUATIVE_INDICATORS = setOf(
            "evaluate", "judge", "critique", "review", "assess",
            "rate", "rank", "prioritize", "weigh", "measure"
        )

        private val CREATIVE_INDICATORS = setOf(
            "create", "design", "write", "generate", "imagine",
            "invent", "compose", "craft", "develop", "build"
        )

        private val ANALYTICAL_INDICATORS = setOf(
            "why", "how does", "explain", "analyze", "reason",
            "cause", "effect", "relationship", "impact", "influence"
        )

        private val FACTUAL_STARTERS = setOf(
            "what is", "who is", "when did", "where is",
            "which", "how many", "how much", "name", "list"
        )

        private val GREETINGS = setOf(
            "hi", "hello", "hey", "thanks", "thank you",
            "yes", "no", "ok", "okay", "bye", "goodbye"
        )
    }
}