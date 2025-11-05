# LLM Mobile Benchmark App

A comprehensive benchmarking app for testing Phi-3 Mini, Gemma 2B, and TinyLlama performance on Android devices.

## 🎯 Purpose

This app measures the feasibility of running small language models on mobile devices, specifically testing:
- Battery drain rate
- Token generation speed
- Memory consumption
- CPU usage and temperature
- Model quality for different tasks

## 📱 Supported Models

1. **Phi-3 Mini (3.8B)** - Microsoft's powerful small model
2. **Gemma 2B** - Google's efficient model (TODO)
3. **TinyLlama (1.1B)** - Ultra-lightweight model (TODO)

## 🔧 Setup Instructions

### Prerequisites

- Android Studio (Latest version)
- Android device with 6GB+ RAM (8GB recommended)
- 5GB free storage for models
- Android 8.0 (API 26) or higher

### Step 1: Clone and Open Project

```bash
git clone [your-repo]
cd mobile-llm-test
```

Open in Android Studio and sync Gradle.

### Step 2: Download Models

#### Phi-3 Mini ONNX Model

1. Download from HuggingFace:
```bash
# Install huggingface-cli if needed
pip install huggingface-hub

# Download INT4 quantized model
huggingface-cli download microsoft/Phi-3-mini-4k-instruct-onnx \
  --include "cpu_and_mobile/cpu-int4-rtn-block-32-acc-level-4/*" \
  --local-dir ./models/phi3
```

2. Copy to device:
```bash
adb push models/phi3/cpu_and_mobile/cpu-int4-rtn-block-32-acc-level-4/* \
  /sdcard/Android/data/com.llmtest.hybrid/files/
```

### Step 3: Run Tests

1. Connect your Android device via USB
2. Enable Developer Mode and USB Debugging
3. Run the app from Android Studio
4. Select model and test type
5. Click "Start Test"

## 📊 Test Scenarios

### Basic Performance Test
- 5-10 simple prompts
- Measures tokens/second, latency, battery
- Duration: ~5 minutes

### Sustained Conversation Test
- 30-minute conversation simulation
- Tracks performance degradation
- Battery drain over time

### Routing Classification Test
- Tests ability to classify query complexity
- Critical for hybrid routing architecture
- Measures classification accuracy

### Burst Usage Test
- Simulates real-world intermittent usage
- 10 queries, pause, repeat
- Tests cold-start performance

## 📈 Metrics Collected

- **Performance**: Tokens/second, first token latency
- **Resources**: Memory usage, CPU usage, temperature
- **Battery**: Drain rate per minute, total drain
- **Quality**: Response accuracy, routing classification

## 🔄 Next Steps

### Immediate (Week 1)
- [ ] Fix ONNX Runtime model loading
- [ ] Implement proper Phi-3 tokenizer
- [ ] Add model download manager
- [ ] Test on multiple devices

### Week 2
- [ ] Add Gemma 2B support (GGUF format)
- [ ] Add TinyLlama support
- [ ] Implement side-by-side comparison
- [ ] Add export functionality for results

### Week 3
- [ ] Optimize memory management
- [ ] Add power management options
- [ ] Create detailed analytics dashboard
- [ ] Generate final report

## 🐛 Known Issues

1. **Model Loading**: Need to implement proper model file management
2. **Tokenizer**: Currently using placeholder - need real Phi-3 tokenizer
3. **Memory**: Large models may crash on <6GB devices

## 📝 Test Results Format

Results are saved as JSON in:
```
/data/data/com.llmtest.hybrid/files/benchmark_*.json
```

Example:
```json
{
  "modelName": "Phi-3 Mini 4K Instruct",
  "device": {
    "model": "SM-S921B",
    "androidVersion": "14"
  },
  "metrics": {
    "avgTokensPerSecond": 8.3,
    "totalBatteryDrain": 12.5,
    "peakMemoryMB": 2456
  }
}
```

## 🎯 Success Criteria

For our Hybrid Router to be viable:

### Minimum Requirements
- Battery drain: <15% per 30 minutes
- Speed: >6 tokens/second
- Stability: Zero crashes
- Memory: <3GB peak

### Ideal Targets
- Battery drain: <10% per 30 minutes  
- Speed: >10 tokens/second
- Memory: <2GB peak
- First token: <1 second

## 🤝 Contributing

This is a research project for testing LLM feasibility on mobile.

## 📧 Contact

[Your contact info]

## 📄 License

MIT

---

## Implementation TODOs

### High Priority
1. **Fix Model Loading**
   - Implement ONNX model downloader
   - Add progress tracking
   - Handle large file management

2. **Real Tokenizer**
   - Port sentencepiece or tiktoken
   - Or use pre-tokenized inputs

3. **Gemma Integration**
   ```kotlin
   class GemmaModelAdapter : BaseModelAdapter {
       // Use llama.cpp or MediaPipe
   }
   ```

4. **TinyLlama Integration**
   ```kotlin
   class TinyLlamaAdapter : BaseModelAdapter {
       // Use GGUF format with llama.cpp
   }
   ```

### Testing Checklist

- [ ] Samsung Galaxy S24 (High-end)
- [ ] Pixel 7 (Stock Android)
- [ ] Samsung A54 (Mid-range)
- [ ] OnePlus Nord (Different chipset)
- [ ] Older device (2020-2021)

### Data Collection Goals

After 1 week of testing, we need:
- 50+ test runs across 5+ devices
- Battery drain curves
- Performance vs temperature graphs
- Model comparison matrix
- Go/no-go decision for Hybrid Router

## 🚀 Quick Start Commands

```bash
# Build and install
./gradlew installDebug

# View logs
adb logcat -s "LLMTest"

# Pull results
adb pull /sdcard/Android/data/com.llmtest.hybrid/files/benchmark_*.json ./results/

# Monitor performance
adb shell dumpsys battery
adb shell dumpsys meminfo com.llmtest.hybrid
adb shell top -m 10
```
