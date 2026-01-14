package com.company.fieldapp.ui.admin

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.company.fieldapp.data.session.SessionManager
import com.company.fieldapp.navigation.NavRoutes
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminAttendanceScreen(
    navController: NavHostController,
    viewModel: AdminAttendanceViewModel = viewModel()
) {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    val attendanceList by viewModel.attendanceList.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var selectedImageUrl by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = Color(0xFFF5F5F5)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
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
                                    Color(0xFFFF6B35).copy(alpha = 0.1f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.AccessTime,
                                contentDescription = null,
                                tint = Color(0xFFFF6B35),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Attendance Logs",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${attendanceList.size} records found",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.onSearchQueryChange(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search by name or location...") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, "Search", tint = Color.Gray)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                    Icon(Icons.Default.Close, "Clear", tint = Color.Gray)
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color(0xFFE0E0E0),
                            focusedBorderColor = Color(0xFFFF6B35)
                        )
                    )

                    Spacer(Modifier.height(12.dp))

                    // Filter Section
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Date Filter with Calendar
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
                                        viewModel.onDateSelected(dateFormat.format(selected.time))
                                    },
                                    calendar.get(Calendar.YEAR),
                                    calendar.get(Calendar.MONTH),
                                    calendar.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.White
                            )
                        ) {
                            Icon(
                                Icons.Default.CalendarToday,
                                "Date",
                                modifier = Modifier.size(18.dp),
                                tint = Color(0xFFFF6B35)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (selectedDate.startsWith("20")) {
                                    selectedDate
                                } else {
                                    selectedDate.replaceFirstChar { it.uppercase() }
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        // All Types Filter
                        OutlinedButton(
                            onClick = { /* Show filter options */ },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.White
                            )
                        ) {
                            Icon(
                                Icons.Default.FilterList,
                                "Filter",
                                modifier = Modifier.size(18.dp),
                                tint = Color(0xFFFF6B35)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "All Types",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Attendance List
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFFFF6B35))
                }
            } else if (errorMessage != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = Color(0xFFF44336),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            errorMessage ?: "",
                            color = Color(0xFFF44336),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.loadAttendance() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF6B35)
                            )
                        ) {
                            Text("Retry")
                        }
                    }
                }
            } else if (attendanceList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.EventBusy,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(80.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "No attendance records found",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.Gray
                        )
                        Text(
                            text = "Try selecting a different date",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(attendanceList) { attendance ->
                        EnhancedAttendanceCard(
                            attendance = attendance,
                            onSelfieClick = { selectedImageUrl = attendance.selfieUrl }
                        )
                    }
                }
            }
        }
    }

    // Image Viewer Dialog
    if (selectedImageUrl != null) {
        ImageViewerDialog(
            imageUrl = selectedImageUrl!!,
            onDismiss = { selectedImageUrl = null }
        )
    }
}

@Composable
fun EnhancedAttendanceCard(
    attendance: AdminAttendanceData,
    onSelfieClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Selfie with border
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .background(
                        Color(0xFFFF6B35).copy(alpha = 0.1f),
                        CircleShape
                    )
                    .padding(4.dp)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(attendance.selfieUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Selfie",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .clickable(onClick = onSelfieClick)
                        .background(Color.LightGray)
                )
            }

            Spacer(Modifier.width(16.dp))

            // Employee Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = attendance.employeeName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF212121)
                )

                Spacer(Modifier.height(4.dp))

                // Badge
                Surface(
                    color = Color(0xFFFF6B35),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "Check-in",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Date & Time
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = Color.Gray
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${attendance.date} ${attendance.checkInTime}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }

                Spacer(Modifier.height(4.dp))

                // Location
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = Color.Gray
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${String.format("%.4f", attendance.latitude)}, ${String.format("%.4f", attendance.longitude)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }

            // Status Badge
            Surface(
                color = Color(0xFF4CAF50).copy(alpha = 0.15f),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFF4CAF50), CircleShape)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Synced",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun ImageViewerDialog(
    imageUrl: String,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Black
            )
        ) {
            Box {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Selfie Full View",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 500.dp)
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

// Data class for attendance
data class AdminAttendanceData(
    val id: String,
    val employeeName: String,
    val employeeId: String,
    val date: String,
    val checkInTime: String,
    val latitude: Double,
    val longitude: Double,
    val selfieUrl: String,
    val isSynced: Boolean
)