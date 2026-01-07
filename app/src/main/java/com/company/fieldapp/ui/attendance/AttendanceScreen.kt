package com.company.fieldapp.ui.attendance

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.company.fieldapp.navigation.NavRoutes
import com.company.fieldapp.utils.LocationUtils
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AttendanceScreen(
    navController: NavHostController,
    viewModel: AttendanceViewModel = viewModel()
) {
    val context = LocalContext.current
    val status by viewModel.status.collectAsState()
    val todayAttendance by viewModel.todayAttendance.collectAsState()
    var showCamera by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    val locationPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                LocationUtils.getCurrentLocation(
                    context,
                    onLocationReceived = { lat, lng ->
                        viewModel.onLocationReceived(lat, lng)
                    },
                    onError = {
                        viewModel.onLocationError(it)
                    }
                )
            } else {
                viewModel.onLocationError("Location permission denied")
            }
        }

    if (showCamera) {
        // Camera screen without footer
        CameraPreviewScreen(
            onImageCaptured = { file ->
                viewModel.onSelfieCaptured(file)
                showCamera = false

                // Request location after selfie
                val permissionStatus = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )

                if (permissionStatus == PackageManager.PERMISSION_GRANTED) {
                    LocationUtils.getCurrentLocation(
                        context,
                        onLocationReceived = { lat, lng ->
                            viewModel.onLocationReceived(lat, lng)
                        },
                        onError = {
                            viewModel.onLocationError(it)
                        }
                    )
                } else {
                    locationPermissionLauncher.launch(
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                }
            },
            onError = {
                viewModel.onCameraError(it)
                showCamera = false
            }
        )
    } else {
        // Main screen with footer
        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = true,
                        onClick = { },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = "Attendance"
                            )
                        },
                        label = { Text("Attendance") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFFFF6B35),
                            selectedTextColor = Color(0xFFFF6B35),
                            indicatorColor = Color(0xFFFFE5D9)
                        )
                    )

                    NavigationBarItem(
                        selected = false,
                        onClick = { navController.navigate(NavRoutes.Expenses.route) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Receipt,
                                contentDescription = "Expenses"
                            )
                        },
                        label = { Text("Expenses") }
                    )

                    NavigationBarItem(
                        selected = false,
                        onClick = { navController.navigate(NavRoutes.Tasks.route) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Work,
                                contentDescription = "Tasks"
                            )
                        },
                        label = { Text("Tasks") }
                    )

                    NavigationBarItem(
                        selected = false,
                        onClick = { navController.navigate(NavRoutes.Profile.route) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Profile"
                            )
                        },
                        label = { Text("Profile") }
                    )
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF5F5F5))
                    .padding(paddingValues)
                    .verticalScroll(scrollState)
            ) {
                // Header
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Time Attendance",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
                                .format(Date()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Check-in card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Clock icon
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .background(
                                    Color(0xFFFFE5D9),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = "Clock",
                                tint = Color(0xFFFF6B35),
                                modifier = Modifier.size(50.dp)
                            )
                        }

                        Spacer(Modifier.height(24.dp))

                        Text(
                            text = "Ready to Check In",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = "Capture your selfie and location",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )

                        Spacer(Modifier.height(24.dp))

                        Button(
                            onClick = { showCamera = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4CAF50)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoCamera,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Check In",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Status message
                if (status != "Ready to check in") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Text(
                            text = status,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // Today's Activity
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Today's Activity",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(16.dp))

                        if (todayAttendance.isEmpty()) {
                            Text(
                                text = "No attendance records today",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            todayAttendance.forEach { attendance ->
                                val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                val time = timeFormat.format(Date(attendance.timestamp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = "Check-in: $time",
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            text = "Lat: ${attendance.latitude}, Lng: ${attendance.longitude}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                    }

                                    Text(
                                        text = if (attendance.isSynced) "✓ Synced" else "⟳ Pending",
                                        color = if (attendance.isSynced) Color.Green else Color(0xFFFF9800),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }

                                if (attendance != todayAttendance.last()) {
                                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Sync button
                Button(
                    onClick = { viewModel.syncAttendanceToServer() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2196F3)
                    )
                ) {
                    Text("Sync Attendance to Server")
                }

                // Extra spacing at bottom to ensure last button is visible
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}