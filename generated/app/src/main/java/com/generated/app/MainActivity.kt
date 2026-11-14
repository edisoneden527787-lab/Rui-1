package com.generated.app
import android.os.Bundle
import android.os.CountDownTimer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TimerScreen()
                }
            }
        }
    }
}

@Composable
fun TimerScreen() {
    val presetMinutes = listOf(1, 3, 5, 10)
    var selectedMinutes by remember { mutableStateOf(1) }
    var remainingMillis by remember { mutableLongStateOf(1 * 60 * 1000L) }
    var isRunning by remember { mutableStateOf(false) }
    var isFinished by remember { mutableStateOf(false) }
    var timer: CountDownTimer? by remember { mutableStateOf(null) }

    fun stopTimer() {
        timer?.cancel()
        timer = null
        isRunning = false
    }

    fun startTimer() {
        stopTimer()
        isFinished = false
        isRunning = true
        timer = object : CountDownTimer(remainingMillis, 100) {
            override fun onTick(millisUntilFinished: Long) {
                remainingMillis = millisUntilFinished
            }

            override fun onFinish() {
                remainingMillis = 0
                isRunning = false
                isFinished = true
            }
        }.start()
    }

    fun selectMinutes(mins: Int) {
        stopTimer()
        selectedMinutes = mins
        remainingMillis = mins * 60 * 1000L
        isFinished = false
    }

    fun resetTimer() {
        stopTimer()
        remainingMillis = selectedMinutes * 60 * 1000L
        isFinished = false
    }

    DisposableEffect(Unit) {
        onDispose {
            timer?.cancel()
        }
    }

    val minutes = (remainingMillis / 1000) / 60
    val seconds = (remainingMillis / 1000) % 60
    val timeDisplay = String.format("%02d:%02d", minutes, seconds)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "倒數計時器",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            presetMinutes.forEach { mins ->
                val isSelected = mins == selectedMinutes && !isRunning
                if (isSelected) {
                    Button(onClick = { selectMinutes(mins) }) {
                        Text(text = "${mins} 分", fontSize = 16.sp)
                    }
                } else {
                    OutlinedButton(
                        onClick = { selectMinutes(mins) },
                        enabled = !isRunning
                    ) {
                        Text(text = "${mins} 分", fontSize = 16.sp)
                    }
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isFinished) Color(0xFFFFCDD2) else MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(36.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isFinished) {
                    Text(
                        text = "時間到！",
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD32F2F)
                    )
                } else {
                    Text(
                        text = timeDisplay,
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!isRunning) {
                Button(
                    onClick = {
                        if (remainingMillis <= 0) {
                            remainingMillis = selectedMinutes * 60 * 1000L
                        }
                        startTimer()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = if (isFinished) "重新開始" else "開始", fontSize = 18.sp)
                }
            } else {
                Button(
                    onClick = { stopTimer() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE53935)
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "暫停", fontSize = 18.sp)
                }
            }

            OutlinedButton(
                onClick = { resetTimer() },
                modifier = Modifier.weight(1f)
            ) {
                Text(text = "重設", fontSize = 18.sp)
            }
        }
    }
}
