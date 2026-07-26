package com.uplb.punla.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.uplb.punla.data.PunlaRepository
import com.uplb.punla.data.entity.Expense
import com.uplb.punla.ui.screens.getCategoryColor
import com.uplb.punla.ui.theme.LocalPunlaPalette
import com.uplb.punla.ui.theme.PunlaTheme
import com.uplb.punla.widget.WidgetRefresher
import kotlinx.coroutines.launch
import java.time.LocalDate

private val QUICK_ADD_CATEGORIES = listOf(
    "Food / Allowance",
    "Transportation",
    "Mobile Load / Internet",
    "Supplies",
    "Org / Activities",
    "Miscellaneous"
)

/**
 * Floating "quick add expense" card, launched straight from the Budget
 * widget's + button. Skips MainActivity/navigation entirely so logging a
 * jeepney fare or a canteen meal is a couple of taps, not a full app launch.
 */
class QuickAddExpenseActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = PunlaRepository(this)
        setContent {
            PunlaTheme(
                darkTheme = isSystemInDarkTheme(),
                preset = repo.themePreset,
                customSeedArgb = repo.customSeedColor,
                fontChoice = repo.fontChoice
            ) {
                QuickAddExpenseCard(
                    onDismiss = { finish() },
                    onSave = { expense ->
                        lifecycleScope.launch {
                            repo.addExpense(expense)
                            WidgetRefresher.refreshAll(this@QuickAddExpenseActivity)
                            finish()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun QuickAddExpenseCard(onDismiss: () -> Unit, onSave: (Expense) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(QUICK_ADD_CATEGORIES.first()) }
    var note by remember { mutableStateOf("") }
    val today = remember { LocalDate.now().toString() }
    val canSave = amount.toDoubleOrNull()?.let { it > 0 } == true

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "Quick add expense",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Logged for today, $today",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = amount,
                    onValueChange = { input -> if (input.all { it.isDigit() || it == '.' }) amount = input },
                    label = { Text("Amount (₱)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(14.dp))
                Text(
                    "Category",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(QUICK_ADD_CATEGORIES) { opt ->
                        val selected = opt == category
                        FilterChip(
                            selected = selected,
                            onClick = { category = opt },
                            label = { Text(opt) },
                            leadingIcon = {
                                Box(
                                    Modifier
                                        .size(8.dp)
                                        .background(getCategoryColor(opt, LocalPunlaPalette.current), RoundedCornerShape(4.dp))
                                )
                            }
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = canSave,
                        onClick = {
                            val value = amount.toDoubleOrNull() ?: return@Button
                            onSave(
                                Expense(
                                    amount = value,
                                    category = category,
                                    date = today,
                                    note = note.ifBlank { null }
                                )
                            )
                        }
                    ) { Text("Save") }
                }
            }
        }
    }
}
