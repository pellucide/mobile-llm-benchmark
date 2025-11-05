package com.palmabrahma.smallmodeltest

import android.os.Bundle
import android.widget.ScrollView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.palmabrahma.smallmodeltest.models.ModelManager
import com.palmabrahma.smallmodeltest.models.ModelType
import com.palmabrahma.smallmodeltest.ui.ModelDownloadDialog
import com.palmabrahma.smallmodeltest.ui.theme.SmallModelTestTheme
import kotlinx.coroutines.launch


class MainActivity_studioGenerated : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmallModelTestTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    SmallModelTestTheme {
        Greeting("Android")
    }
}


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SmallModelTestTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BenchmarkScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenchmarkScreen(
    viewModel: BenchmarkViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val modelManager = remember { ModelManager(viewModel.getApplication()) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var modelToDownload by remember { mutableStateOf<ModelType?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LLM Model Benchmark") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Model Selection with download status
            ModelSelectionCard(
                selectedModel = uiState.selectedModel,
                onModelSelected = { viewModel.selectModel(it) },
                modelManager = modelManager,
                onDownloadRequired = { model ->
                    modelToDownload = model
                    showDownloadDialog = true
                }
            )

            // Test Configuration
            TestConfigurationCard(
                selectedTest = uiState.selectedTest,
                onTestSelected = { viewModel.selectTest(it) }
            )

            // Real-time Metrics Display
            if (uiState.isRunning) {
                MetricsCard(
                    currentMetrics = uiState.currentMetrics
                )
            }

            // Control Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            viewModel.startBenchmark()
                        }
                    },
                    enabled = !uiState.isRunning && uiState.selectedModel != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (uiState.isRunning) "Running..." else "Start Test")
                }

                Button(
                    onClick = { viewModel.stopBenchmark() },
                    enabled = uiState.isRunning,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Stop")
                }
            }

            // Results Display
            if (uiState.results.isNotEmpty()) {
                ResultsCard(results = uiState.results)
            }
        }
    }

    // Model download dialog
    modelToDownload?.let { model ->
        ModelDownloadDialog(
            modelType = model,
            modelManager = modelManager,
            onDownloadComplete = {
                viewModel.selectModel(model)
                modelToDownload = null
                showDownloadDialog = false
            },
            onDismiss = {
                modelToDownload = null
                showDownloadDialog = false
            }
        )
    }
}

@Composable
fun ModelSelectionCard(
    selectedModel: ModelType?,
    onModelSelected: (ModelType) -> Unit,
    modelManager: ModelManager,
    onDownloadRequired: (ModelType) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                "Select Model",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            ModelType.entries.forEach { model ->
                val isDownloaded = remember(model) {
                    modelManager.isModelDownloaded(model)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedModel == model,
                        onClick = {
                            if (isDownloaded) {
                                onModelSelected(model)
                            } else {
                                onDownloadRequired(model)
                            }
                        }
                    )
                    Column(
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                    ) {
                        Text(
                            model.displayName,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "${model.parameters} • ${model.sizeMB}MB",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!isDownloaded) {
                        Text(
                            "Not downloaded",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Downloaded",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TestConfigurationCard(
    selectedTest: TestType,
    onTestSelected: (TestType) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                "Test Scenario",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            TestType.values().forEach { test ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedTest == test,
                        onClick = { onTestSelected(test) }
                    )
                    Column(
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            test.displayName,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            test.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MetricsCard(
    currentMetrics: RealTimeMetrics?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                "Live Metrics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (currentMetrics != null) {
                Spacer(modifier = Modifier.height(8.dp))

                MetricRow("Tokens/sec", "${currentMetrics.tokensPerSecond}", Color.Green)
                MetricRow("Battery", "${currentMetrics.batteryLevel}%",
                    if (currentMetrics.batteryLevel > 20) Color.Green else Color.Red)
                MetricRow("Memory", "${currentMetrics.memoryUsedMB}MB", Color.Blue)
                MetricRow("CPU", "${currentMetrics.cpuUsage}%",
                    if (currentMetrics.cpuUsage < 80) Color.Green else Color.Red)
                MetricRow("Temperature", "${currentMetrics.temperature}°C",
                    if (currentMetrics.temperature < 40) Color.Green else Color.Red)
            }
        }
    }
}

@Composable
fun MetricRow(
    label: String,
    value: String,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ResultsCard(
    results: List<TestResult>
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                "Test Results",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(results) { result ->
                    ResultItem(result)
                }
            }
        }
    }
}

@Composable
fun ResultItem(result: TestResult) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    result.modelName,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    result.testType,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                "Avg: ${result.avgTokensPerSecond} tok/s • " +
                        "Battery: -${result.batteryDrain}% • " +
                        "Memory: ${result.peakMemoryMB}MB",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

// Data classes for UI state
data class RealTimeMetrics(
    val tokensPerSecond: Float = 0f,
    val batteryLevel: Float = 100f,
    val memoryUsedMB: Long = 0,
    val cpuUsage: Float = 0f,
    val temperature: Float = 0f
)

data class TestResult(
    val modelName: String,
    val testType: String,
    val avgTokensPerSecond: Float,
    val batteryDrain: Float,
    val peakMemoryMB: Long,
    val timestamp: Long = System.currentTimeMillis()
)

enum class TestType(
    val displayName: String,
    val description: String
) {
    BASIC("Basic Performance", "Quick test with simple prompts"),
    SUSTAINED("Sustained Conversation", "30-minute conversation test"),
    ROUTING("Routing Classification", "Test complexity detection"),
    BURST("Burst Usage", "Intermittent usage pattern")
}