package com.generated.app
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                Surface {
                    AppScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::)
@Composable
fun AppScreen() {
    var selectedMinutes by remember { mutableIntStateOf(1) }
    var timeLeftSeconds by remember { mutableIntStateOf(1 * 60) }
    var isRunning by remember { mutableStateOf(false) }
    var isFinished by remember { mutableStateOf(false) }

    LaunchedEffect(isRunning, timeLeftSeconds) {
        if (isRunning && timeLeftSeconds > 0) {
            delay(1000L)
            timeLeftSeconds--
            if (timeLeftSeconds == 0) {
                isRunning = false
                isFinished = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("倒數計時器", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Quick preset selection
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "選擇倒數時間",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val options = listOf(1, 3, 5, 10)
                    options.forEach { minutes ->
                        val isSelected = (selectedMinutes == minutes) && !isRunning && !isFinished
                        if (isSelected) {
                            Button(
                                onClick = {
                                    selectedMinutes = minutes
                                    timeLeftSeconds = minutes * 60
                                },
                                enabled = !isRunning
                            ) {
                                Text("${minutes}分")
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    selectedMinutes = minutes
                                    timeLeftSeconds = minutes * 60
                                    isFinished = false
                                },
                                enabled = !isRunning
                            ) {
                                Text("${minutes}分")
                            }
                        }
                    }
                }
            }

            // Main display card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isFinished)
                        MaterialTheme.colorScheme.errorContainer
                    else
                        MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (isFinished) {
                        Text(
                            text = "時間到！",
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    } else {
                        val mins = timeLeftSeconds / 60
                        val secs = timeLeftSeconds % 60
                        val formattedTime = String.format("%02d:%02d", mins, secs)
                        Text(
                            text = formattedTime,
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isRunning) "計時中..." else "準備就緒",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                if (isFinished) {
                    Button(
                        onClick = {
                            isFinished = false
                            timeLeftSeconds = selectedMinutes * 60
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Text("重新開始", fontSize = 18.sp)
                    }
                } else {
                    if (isRunning) {
                        Button(
                            onClick = { isRunning = false },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                        ) {
                            Text("暫停", fontSize = 18.sp)
                        }
                    } else {
                        Button(
                            onClick = { isRunning = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                        ) {
                            Text("開始計時", fontSize = 18.sp)
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    OutlinedButton(
                        onClick = {
                            isRunning = false
                            isFinished = false
                            timeLeftSeconds = selectedMinutes * 60
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                    ) {
                        Text("重置", fontSize = 18.sp)
                    }
                }
            }
        }
    }
}
