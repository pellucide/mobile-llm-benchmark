# Contributing to Mobile LLM Benchmark

Thank you for your interest in contributing to the Mobile LLM Benchmark project! This document provides guidelines and instructions for contributing.

## 🎯 Goal

Our goal is to create a comprehensive, reliable benchmark suite for testing small language models on Android devices. We aim to provide clear, actionable data about the feasibility of on-device AI.

## 📋 How to Contribute

### Reporting Issues

1. **Check existing issues** first to avoid duplicates
2. Use the issue template when available
3. Include:
   - Device model and Android version
   - Steps to reproduce
   - Expected vs actual behavior
   - Logs if applicable (use `adb logcat`)

### Suggesting Features

1. Open an issue with the `enhancement` label
2. Describe the feature and its benefits
3. Include use cases and examples

### Code Contributions

1. **Fork the repository**
2. **Create a feature branch**
   ```bash
   git checkout -b feature/your-feature-name
   ```
3. **Make your changes**
   - Follow the coding style (Kotlin conventions)
   - Add tests where applicable
   - Update documentation
4. **Test thoroughly**
   ```bash
   ./gradlew build
   ./gradlew connectedAndroidTest
   ```
5. **Commit with clear messages**
   ```bash
   git commit -m "feat: add support for new metric"
   ```
6. **Push and create a Pull Request**

## 🔧 Development Setup

### Prerequisites
- Android Studio Arctic Fox or later
- Kotlin 1.9.20+
- Android SDK 34
- Git

### Building
```bash
# Clone your fork
git clone https://github.com/yourusername/mobile-llm-benchmark.git
cd mobile-llm-benchmark

# Open in Android Studio
studio .

# Or build from command line
./gradlew assembleDebug
```

## 📝 Coding Standards

### Kotlin Style
- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful variable names
- Add KDoc comments for public APIs
- Keep functions small and focused

### Example:
```kotlin
/**
 * Calculates tokens per second from inference metrics
 * @param tokenCount Total tokens generated
 * @param timeMs Time taken in milliseconds
 * @return Tokens per second rate
 */
fun calculateTokensPerSecond(
    tokenCount: Int,
    timeMs: Long
): Float {
    return if (timeMs > 0) {
        (tokenCount * 1000f) / timeMs
    } else {
        0f
    }
}
```

### Commit Messages
Follow [Conventional Commits](https://www.conventionalcommits.org/):
- `feat:` New feature
- `fix:` Bug fix
- `docs:` Documentation changes
- `style:` Code style changes
- `refactor:` Code refactoring
- `test:` Test additions/changes
- `chore:` Maintenance tasks

## 🧪 Testing

### Unit Tests
Place in `app/src/test/java/`
```kotlin
@Test
fun testMetricsCalculation() {
    val result = calculateTokensPerSecond(100, 1000)
    assertEquals(100f, result)
}
```

### Instrumented Tests
Place in `app/src/androidTest/java/`
```kotlin
@Test
fun testModelLoading() {
    val adapter = Phi3MiniAdapter(context)
    val loadTime = adapter.initialize()
    assertTrue(loadTime > 0)
}
```

## 📊 Adding New Models

To add support for a new model:

1. Create adapter in `app/src/main/java/com/llmtest/hybrid/models/`
2. Implement `BaseModelAdapter` interface
3. Add to `ModelType` enum
4. Update `ModelManager` with download URLs
5. Add documentation

Example:
```kotlin
class YourModelAdapter(
    private val context: Context
) : BaseModelAdapter {
    override val modelName = "Your Model"
    override val modelSizeMB = 1000
    // ... implement all methods
}
```

## 📈 Adding New Metrics

To add a new metric:

1. Update `MetricsCollector.kt`
2. Add to `SystemMetrics` data class
3. Update UI to display new metric
4. Document the metric

## 🌍 Internationalization

Currently English-only, but PRs for translations welcome:
- Add strings to `res/values-{lang}/strings.xml`
- Keep UI text in resources, not hardcoded

## 🔒 Security

- Never commit API keys or credentials
- Validate all user inputs
- Use HTTPS for model downloads
- Report security issues privately

## 📜 Legal

By contributing, you agree that your contributions will be licensed under the MIT License.

## 🙋 Questions?

- Open an issue with the `question` label
- Check our [FAQ](docs/FAQ.md) (coming soon)
- Review existing discussions

## 🏆 Recognition

Contributors will be recognized in:
- README.md contributors section
- Release notes
- Special thanks in the app

Thank you for helping make on-device AI benchmarking better!
