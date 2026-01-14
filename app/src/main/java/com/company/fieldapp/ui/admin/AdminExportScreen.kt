package com.company.fieldapp.ui.admin

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.company.fieldapp.data.session.SessionManager
import com.company.fieldapp.navigation.NavRoutes
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminExportScreen(
    navController: NavHostController,
    viewModel: AdminExportViewModel = viewModel()
) {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    val users by viewModel.users.collectAsState()
    val selectedUser by viewModel.selectedUser.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val exportStatus by viewModel.exportStatus.collectAsState()
    val startDate by viewModel.startDate.collectAsState()
    val endDate by viewModel.endDate.collectAsState()

    var showUserDropdown by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Scaffold(
        containerColor = Color(0xFFF5F5F5)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
        ) {
            // Header Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // Top Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { navController.navigateUp() },
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFFF5F5F5), CircleShape)
                        ) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color(0xFF212121)
                            )
                        }

                        Text(
                            text = "Administrator",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    // Title with Icon
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    Color(0xFFFF9800).copy(alpha = 0.1f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                tint = Color(0xFFFF9800),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Data Export",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Export user data, attendance records, expenses, and images",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Export User Data Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = Color(0xFFFF6B35),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Export User Data with Images",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // User Dropdown
                    ExposedDropdownMenuBox(
                        expanded = showUserDropdown,
                        onExpandedChange = { showUserDropdown = it }
                    ) {
                        OutlinedTextField(
                            value = selectedUser?.name ?: "Select a user...",
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = showUserDropdown)
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = Color(0xFFE0E0E0),
                                focusedBorderColor = Color(0xFFFF6B35)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        ExposedDropdownMenu(
                            expanded = showUserDropdown,
                            onDismissRequest = { showUserDropdown = false }
                        ) {
                            users.forEach { user ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = user.name,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = user.employeeId,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.Gray
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.selectUser(user)
                                        showUserDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Date Range Selection
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Start Date
                        OutlinedButton(
                            onClick = {
                                val calendar = Calendar.getInstance()
                                DatePickerDialog(
                                    context,
                                    { _, year, month, day ->
                                        val selected = Calendar.getInstance().apply {
                                            set(year, month, day)
                                        }
                                        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                        viewModel.setStartDate(dateFormat.format(selected.time))
                                    },
                                    calendar.get(Calendar.YEAR),
                                    calendar.get(Calendar.MONTH),
                                    calendar.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color(0xFFF5F5F5)
                            )
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Start Date",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                                Text(
                                    text = startDate ?: "Select",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // End Date
                        OutlinedButton(
                            onClick = {
                                val calendar = Calendar.getInstance()
                                DatePickerDialog(
                                    context,
                                    { _, year, month, day ->
                                        val selected = Calendar.getInstance().apply {
                                            set(year, month, day)
                                        }
                                        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                        viewModel.setEndDate(dateFormat.format(selected.time))
                                    },
                                    calendar.get(Calendar.YEAR),
                                    calendar.get(Calendar.MONTH),
                                    calendar.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color(0xFFF5F5F5)
                            )
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "End Date",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                                Text(
                                    text = endDate ?: "Select",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Export Button
                    Button(
                        onClick = { viewModel.exportUserData() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        enabled = selectedUser != null && !isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF9800)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                        } else {
                            Icon(Icons.Default.Archive, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = "Export User ZIP",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Export All Data Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = Color(0xFF2196F3),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Export All Data",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // Date Range for All Data
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val calendar = Calendar.getInstance()
                                DatePickerDialog(
                                    context,
                                    { _, year, month, day ->
                                        val selected = Calendar.getInstance().apply {
                                            set(year, month, day)
                                        }
                                        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                        viewModel.setStartDate(dateFormat.format(selected.time))
                                    },
                                    calendar.get(Calendar.YEAR),
                                    calendar.get(Calendar.MONTH),
                                    calendar.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Start", style = MaterialTheme.typography.labelSmall)
                                Text(startDate ?: "Select", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                val calendar = Calendar.getInstance()
                                DatePickerDialog(
                                    context,
                                    { _, year, month, day ->
                                        val selected = Calendar.getInstance().apply {
                                            set(year, month, day)
                                        }
                                        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                        viewModel.setEndDate(dateFormat.format(selected.time))
                                    },
                                    calendar.get(Calendar.YEAR),
                                    calendar.get(Calendar.MONTH),
                                    calendar.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("End", style = MaterialTheme.typography.labelSmall)
                                Text(endDate ?: "Select", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = { viewModel.exportAllData() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2196F3)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Export All Data as ZIP",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Quick Exports Section
            Text(
                text = "Quick Exports",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Quick Export Cards
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickExportCard(
                    title = "Attendance",
                    icon = Icons.Default.AccessTime,
                    color = Color(0xFFFF6B35),
                    modifier = Modifier.weight(1f),
                    onPdfClick = { viewModel.exportAttendance("pdf") },
                    onCsvClick = { viewModel.exportAttendance("csv") },
                    isLoading = isLoading
                )

                QuickExportCard(
                    title = "Expenses",
                    icon = Icons.Default.Receipt,
                    color = Color(0xFF2196F3),
                    modifier = Modifier.weight(1f),
                    onPdfClick = { viewModel.exportExpenses("pdf") },
                    onCsvClick = { viewModel.exportExpenses("csv") },
                    isLoading = isLoading
                )

                QuickExportCard(
                    title = "Tasks",
                    icon = Icons.Default.Work,
                    color = Color(0xFF4CAF50),
                    modifier = Modifier.weight(1f),
                    onPdfClick = { viewModel.exportTasks("pdf") },
                    onCsvClick = { viewModel.exportTasks("csv") },
                    isLoading = isLoading
                )
            }

            Spacer(Modifier.height(16.dp))

            // Status Message
            exportStatus?.let { status ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (status.isSuccess)
                            Color(0xFF4CAF50).copy(alpha = 0.1f)
                        else
                            Color(0xFFF44336).copy(alpha = 0.1f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (status.isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (status.isSuccess) Color(0xFF4CAF50) else Color(0xFFF44336),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = status.message,
                            color = if (status.isSuccess) Color(0xFF4CAF50) else Color(0xFFF44336),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun QuickExportCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onPdfClick: () -> Unit,
    onCsvClick: () -> Unit,
    isLoading: Boolean
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(color.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF212121)
            )

            Spacer(Modifier.height(12.dp))

            // PDF Button
            Button(
                onClick = onPdfClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = color
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                Icon(
                    Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("PDF", style = MaterialTheme.typography.labelSmall)
            }

            Spacer(Modifier.height(8.dp))

            // CSV Button
            OutlinedButton(
                onClick = onCsvClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = color
                )
            ) {
                Icon(
                    Icons.Default.TableChart,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Excel", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

data class ExportStatus(
    val isSuccess: Boolean,
    val message: String
)