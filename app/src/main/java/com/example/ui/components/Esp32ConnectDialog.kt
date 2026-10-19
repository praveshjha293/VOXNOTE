package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun Esp32ConnectDialog(
    initialIp: String,
    initialPort: Int,
    initialSsid: String,
    initialPassword: String,
    onDismiss: () -> Unit,
    onConnect: (ip: String, port: Int, ssid: String, password: String) -> Unit,
    onUseSimulationMode: () -> Unit
) {
    var ip by remember { mutableStateOf(initialIp) }
    var port by remember { mutableStateOf(initialPort.toString()) }
    var ssid by remember { mutableStateOf(initialSsid) }
    var password by remember { mutableStateOf(initialPassword) }
    var showPassword by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Wifi,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Connect to ESP32",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Hardware instructions
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Memory,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "1. Hold physical button on ESP32 for 3s.\n2. Confirm Blue LED is ON.\n3. Verify connection details below:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ip,
                        onValueChange = { ip = it.trim() },
                        label = { Text("Device IP") },
                        modifier = Modifier.weight(2f).testTag("dialog_esp32_ip"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.trim() },
                        label = { Text("Port") },
                        modifier = Modifier.weight(1f).testTag("dialog_esp32_port"),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = ssid,
                    onValueChange = { ssid = it.trim() },
                    label = { Text("Wi-Fi SSID") },
                    modifier = Modifier.fillMaxWidth().testTag("dialog_esp32_ssid"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Wi-Fi Password") },
                    modifier = Modifier.fillMaxWidth().testTag("dialog_esp32_password"),
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle password"
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))

                FilledTonalButton(
                    onClick = {
                        onUseSimulationMode()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().testTag("dialog_use_sim_btn")
                ) {
                    Text("No Hardware? Use Simulation Mode")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConnect(
                        ip.trim(),
                        port.trim().toIntOrNull() ?: 80,
                        ssid.trim(),
                        password
                    )
                    onDismiss()
                },
                modifier = Modifier.testTag("dialog_confirm_connect_btn")
            ) {
                Text("Connect")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
