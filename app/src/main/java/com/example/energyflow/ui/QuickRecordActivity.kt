package com.example.energyflow.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.room.Room
import com.example.energyflow.data.AppDatabase
import com.example.energyflow.data.MeterRecord
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class QuickRecordActivity : ComponentActivity() {

    private lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "energyflow-db"
        ).build()

        setContent {
            val scope = rememberCoroutineScope()
            var electricTotal by remember { mutableStateOf("") }
            var waterTotal by remember { mutableStateOf("") }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x80000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 480.dp)
                        .fillMaxWidth()
                        .padding(24.dp)
                        .background(
                            color = Color(0xFF1C1C1E),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "⚡ 快速记录 / 💧 快速记录",
                        color = Color(0xFF00FFC4),
                        fontSize = 18.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = electricTotal,
                        onValueChange = { electricTotal = it },
                        label = { Text("电表总读数") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00FFC4),
                            unfocusedBorderColor = Color(0xFF3A3A3C),
                            focusedLabelColor = Color(0xFF00FFC4),
                            unfocusedLabelColor = Color(0xFF8E8E93),
                            cursorColor = Color(0xFF00FFC4),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = waterTotal,
                        onValueChange = { waterTotal = it },
                        label = { Text("水表总读数") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00FFC4),
                            unfocusedBorderColor = Color(0xFF3A3A3C),
                            focusedLabelColor = Color(0xFF00FFC4),
                            unfocusedLabelColor = Color(0xFF8E8E93),
                            cursorColor = Color(0xFF00FFC4),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            val electric = electricTotal.toDoubleOrNull()
                            val water = waterTotal.toDoubleOrNull()
                            if (electric == null && water == null) {
                                Toast.makeText(
                                    this@QuickRecordActivity,
                                    "请至少输入一个读数",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@Button
                            }
                            scope.launch {
                                try {
                                    database.meterRecordDao().insert(
                                        MeterRecord(
                                            timestamp = LocalDateTime.now(),
                                            isElectricRecorded = electric != null,
                                            electricTotal = electric,
                                            isWaterRecorded = water != null,
                                            waterTotal = water
                                        )
                                    )
                                    Toast.makeText(
                                        this@QuickRecordActivity,
                                        "保存成功",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    setResult(RESULT_OK)
                                    finish()
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        this@QuickRecordActivity,
                                        "保存失败：${e.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00FFC4),
                            contentColor = Color(0xFF0D0F12)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "保存",
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}
