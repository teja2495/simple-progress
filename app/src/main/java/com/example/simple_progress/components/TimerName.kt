package com.example.simple_progress.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// ============================================================================
// TIMER NAME SECTION
// ============================================================================

@Composable
fun TimerNameSection(
    timerName: String,
    isRunning: Boolean,
    onShowNameDialog: () -> Unit
) {
    if (timerName.isNotEmpty()) {
        // Show clickable name for editing
        Text(
            text = timerName,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier
                .padding(bottom = 30.dp)
                .clickable { onShowNameDialog() }
        )
        Spacer(modifier = Modifier.height(16.dp))
    } else if (isRunning) {
        // Show Add Name button when running and no name is set
        Card(
            modifier = Modifier
                .padding(bottom = 30.dp)
                .clickable { onShowNameDialog() },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Text(
                text = "Add Name",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ============================================================================
// DIALOG COMPONENTS
// ============================================================================

@Composable
fun TimerNameDialog(
    currentName: String,
    onNameChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Timer Name") },
        text = {
            OutlinedTextField(
                value = currentName,
                onValueChange = onNameChanged,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
} 