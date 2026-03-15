package com.example.clu

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.clu.ui.theme.CluTheme
import com.example.clu.data.VaultRecord
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottomAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStartAxis
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarkerValueFormatter
import com.patrykandpatrick.vico.core.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.common.*
import com.patrykandpatrick.vico.compose.common.component.*
import com.patrykandpatrick.vico.core.common.*
import androidx.compose.ui.graphics.toArgb
import java.text.SimpleDateFormat
import java.util.*
import java.time.format.DateTimeFormatter
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: VaultViewModel = viewModel()
            val themePreference by viewModel.isDarkMode.collectAsState()
            val useDarkTheme = themePreference ?: isSystemInDarkTheme()
            
            CluTheme(darkTheme = useDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DashboardScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun DashboardScreen(viewModel: VaultViewModel = viewModel()) {
    val connectionState by viewModel.connectionState.collectAsState()
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    val temp by viewModel.temperature.collectAsState()
    val humidity by viewModel.humidity.collectAsState()
    val ruleIndex by viewModel.ruleIndex.collectAsState()
    val ruleStatus by viewModel.ruleStatus.collectAsState()
    val historicalRecords by viewModel.historicalRecords.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val dailyMaxTemp by viewModel.dailyMaxTemp.collectAsState()
    val dailyMinTemp by viewModel.dailyMinTemp.collectAsState()
    val dailyAvgHumidity by viewModel.dailyAvgHumidity.collectAsState()

    val isAlertActive by viewModel.isAlertActive.collectAsState()
    val tempThreshold by viewModel.tempThreshold.collectAsState()
    val humThreshold by viewModel.humThreshold.collectAsState()
    val ruleThreshold by viewModel.ruleThreshold.collectAsState()

    var showHistorySheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            viewModel.startScan()
        }
    }

    LaunchedEffect(Unit) {
        launcher.launch(permissionsToRequest)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        // Integrated Header & Status
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val themePreference by viewModel.isDarkMode.collectAsState()
            ConnectionBadge(state = connectionState)
            Row {
                IconButton(onClick = { viewModel.toggleTheme() }) {
                    Icon(
                        imageVector = when (themePreference) {
                            null -> Icons.Default.BrightnessAuto
                            true -> Icons.Default.WbSunny
                            false -> Icons.Default.NightsStay
                        },
                        contentDescription = "Toggle Theme",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = { showSettingsSheet = true }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Background Decorative Element
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    )
                )
        )

        Spacer(modifier = Modifier.height(32.dp))

        when (connectionState) {
            BleManager.ConnectionState.SCANNING -> {
                Text(
                    text = "Searching for Devices...",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    discoveredDevices.forEach { device ->
                        DeviceItem(device = device) {
                            viewModel.connectToDevice(device)
                        }
                    }
                }
            }
            BleManager.ConnectionState.CONNECTED -> {
                // Alert Banner
                if (isAlertActive) {
                    AlertBanner()
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Climate Card
                DashboardCard(
                    title = "Internal Climate",
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MetricItem(label = "Temperature", value = "$temp°C")
                        VerticalDivider(
                            modifier = Modifier
                                .height(60.dp)
                                .width(1.dp),
                            color = Color.DarkGray
                        )
                        MetricItem(label = "Humidity", value = "$humidity%")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Rule of 100 Card
                DashboardCard(
                    title = "Harrington's Rule of 100",
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AnimatedContent(
                            targetState = ruleIndex,
                            transitionSpec = {
                                (slideInVertically { height -> height } + fadeIn() togetherWith
                                        slideOutVertically { height -> -height } + fadeOut())
                                    .using(SizeTransform(clip = false))
                            }, label = "ruleIndexAnimation"
                        ) { targetIndex ->
                            Text(
                                text = targetIndex,
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontSize = 80.sp,
                                    lineHeight = 80.sp
                                ),
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "Rule Index",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        StatusBadge(status = ruleStatus)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))

                // Environment History Card (Charts)
                HistoryCard(
                    records = historicalRecords,
                    selectedDate = selectedDate,
                    maxTemp = dailyMaxTemp,
                    minTemp = dailyMinTemp,
                    avgHumidity = dailyAvgHumidity,
                    onPreviousDay = { viewModel.selectPreviousDay() },
                    onNextDay = { viewModel.selectNextDay() },
                    onSeeMore = { showHistorySheet = true }
                )

                Spacer(modifier = Modifier.height(24.dp)) // Optimized spacing
            }
            else -> {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (connectionState == BleManager.ConnectionState.CONNECTING) "Connecting..." else "App ready to scan",
                        color = Color.Gray
                    )
                }
            }
        }

        Button(
            onClick = {
                if (connectionState == BleManager.ConnectionState.DISCONNECTED) {
                    launcher.launch(permissionsToRequest)
                } else {
                    viewModel.disconnect()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (connectionState == BleManager.ConnectionState.DISCONNECTED) 
                    MaterialTheme.colorScheme.primary 
                else 
                    Color(0xFFCC0000)
            )
        ) {
            Text(
                text = if (connectionState == BleManager.ConnectionState.DISCONNECTED) "Initialize Scan" else "Stop Connection",
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Professional Footer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp, bottom = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                HorizontalDivider(
                    modifier = Modifier.width(40.dp).padding(bottom = 12.dp),
                    thickness = 2.dp,
                    color = Color.Gray.copy(alpha = 0.2f)
                )
                Text(
                    text = "DESIGNED BY",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray.copy(alpha = 0.4f),
                    letterSpacing = 3.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Hariom Sharnam".uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }

    if (showHistorySheet) {
        DetailedHistorySheet(
            records = historicalRecords,
            onDismiss = { showHistorySheet = false }
        )
    }

    if (showSettingsSheet) {
        ThresholdSettingsSheet(
            temp = tempThreshold,
            hum = humThreshold,
            rule = ruleThreshold,
            onDismiss = { showSettingsSheet = false },
            onSave = { t, h, r -> viewModel.updateThresholds(t, h, r) }
        )
    }
}

@SuppressLint("MissingPermission")
@Composable
fun DeviceItem(device: BluetoothDevice, onClick: () -> Unit) {
    val tonalColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(28.dp),
        color = tonalColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = (device.name ?: "Unknown Device").uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.Gray.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun ConnectionBadge(state: BleManager.ConnectionState) {
    val color = when (state) {
        BleManager.ConnectionState.CONNECTED -> Color(0xFF4CAF50)
        BleManager.ConnectionState.SCANNING -> Color(0xFF2196F3)
        BleManager.ConnectionState.CONNECTING -> Color(0xFFFFC107)
        BleManager.ConnectionState.DISCONNECTED -> Color(0xFFF44336)
        else -> Color.Gray
    }

    val tonalColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(tonalColor, RoundedCornerShape(24.dp))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, CircleShape)
                .border(2.dp, Color.White.copy(alpha = 0.2f), CircleShape)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = state.name,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun AlertBanner() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Alert",
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "THRESHOLD EXCEEDED",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Current environment readings are above safety limits.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
fun DateNavigator(
    selectedDate: LocalDate,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit
) {
    val formatter = remember { DateTimeFormatter.ofPattern("MMM dd, yyyy") }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPreviousDay) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous Day")
        }
        Text(
            text = selectedDate.format(formatter),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        IconButton(onClick = onNextDay) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Day")
        }
    }
}

@Composable
fun SummaryCard(label: String, value: String, unit: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp).copy(alpha = 0.6f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .width(100.dp)
            .padding(4.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value + unit,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun DailySummaryRow(maxTemp: Float?, minTemp: Float?, avgHum: Float?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        SummaryCard(label = "MAX TEMP", value = maxTemp?.let { String.format("%.1f", it) } ?: "--", unit = "°C")
        SummaryCard(label = "MIN TEMP", value = minTemp?.let { String.format("%.1f", it) } ?: "--", unit = "°C")
        SummaryCard(label = "AVG HUM", value = avgHum?.let { String.format("%.1f", it) } ?: "--", unit = "%")
    }
}

@Composable
fun HistoryCard(
    records: List<VaultRecord>,
    selectedDate: LocalDate,
    maxTemp: Float?,
    minTemp: Float?,
    avgHumidity: Float?,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onSeeMore: () -> Unit
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    val marker = rememberDefaultCartesianMarker(label = rememberTextComponent())
    
    LaunchedEffect(records) {
        if (records.isNotEmpty()) {
            modelProducer.runTransaction {
                lineSeries {
                    series(records.map { it.temperature })
                    series(records.map { it.humidity })
                }
            }
        }
    }

    DashboardCard(
        title = "Environment History",
        modifier = Modifier.fillMaxWidth()
    ) {
        DateNavigator(selectedDate, onPreviousDay, onNextDay)
        DailySummaryRow(maxTemp, minTemp, avgHumidity)

        if (records.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "No Data Available for this Date",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.height(240.dp)) {
                    CartesianChartHost(
                        chart = rememberCartesianChart(
                            rememberLineCartesianLayer(),
                            startAxis = rememberStartAxis(),
                            bottomAxis = rememberBottomAxis(
                                valueFormatter = { value, _, _ ->
                                    val recordIndex = value.toInt()
                                    if (recordIndex in records.indices) {
                                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(records[recordIndex].timestamp))
                                    } else ""
                                }
                            ),
                            marker = marker
                        ),
                        modelProducer = modelProducer,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                TextButton(
                    onClick = onSeeMore,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("SEE FULL LOG", style = MaterialTheme.typography.labelLarge)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailedHistorySheet(records: List<VaultRecord>, onDismiss: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = "Historical Readings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            LazyColumn(
                modifier = Modifier.fillMaxHeight(0.6f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(records.asReversed()) { record ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = dateFormat.format(Date(record.timestamp)),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${record.temperature}°C", fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("${record.humidity}%", fontWeight = FontWeight.Bold, color = Color.Gray)
                            }
                        }
                        StatusBadge(status = when {
                            record.ruleIndex < 90 -> RuleStatus.OPTIMAL
                            record.ruleIndex <= 100 -> RuleStatus.WARNING
                            else -> RuleStatus.MOLD_RISK
                        })
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThresholdSettingsSheet(
    temp: Float,
    hum: Float,
    rule: Float,
    onDismiss: () -> Unit,
    onSave: (Float, Float, Float) -> Unit
) {
    var sTemp by remember { mutableStateOf(temp) }
    var sHum by remember { mutableStateOf(hum) }
    var sRule by remember { mutableStateOf(rule) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text("Safety Thresholds", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))
            
            ThresholdSlider(label = "Temp Alert", value = sTemp, range = 20f..50f, unit = "°C") { sTemp = it }
            ThresholdSlider(label = "Humidity Alert", value = sHum, range = 30f..90f, unit = "%") { sHum = it }
            ThresholdSlider(label = "Rule Index Alert", value = sRule, range = 80f..120f, unit = "") { sRule = it }

            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = {
                    onSave(sTemp, sHum, sRule)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("SAVE SETTINGS", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun ThresholdSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, unit: String, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text("${value.toInt()}$unit", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun DashboardCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val tonalColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
    
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(32.dp),
        color = tonalColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(20.dp))
            content()
        }
    }
}

@Composable
fun MetricItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                (fadeIn() + scaleIn(initialScale = 0.8f) togetherWith
                        fadeOut() + scaleOut(targetScale = 1.2f))
            }, label = "metricAnimation"
        ) { targetValue ->
            Text(
                text = targetValue,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = Color.Gray,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun StatusBadge(status: RuleStatus) {
    val color = when (status) {
        RuleStatus.OPTIMAL -> Color(0xFF4CAF50)
        RuleStatus.WARNING -> Color(0xFFFF9800)
        RuleStatus.MOLD_RISK -> Color(0xFFF44336)
        else -> Color.Gray
    }

    Surface(
        color = color,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = status.label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}