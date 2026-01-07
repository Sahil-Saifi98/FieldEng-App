package com.company.fieldapp.ui.profile

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.company.fieldapp.navigation.NavRoutes

@Composable
fun ProfileScreen(
    navController: NavHostController,
    viewModel: ProfileViewModel = viewModel(),
    onLogout: () -> Unit = {}
) {
    val userProfile by viewModel.userProfile.collectAsState()
    val statistics by viewModel.statistics.collectAsState()
    var showSignOutDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate(NavRoutes.Attendance.route) },
                    icon = { Icon(Icons.Default.AccessTime, "Attendance") },
                    label = { Text("Attendance") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate(NavRoutes.Expenses.route) },
                    icon = { Icon(Icons.Default.Receipt, "Expenses") },
                    label = { Text("Expenses") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate(NavRoutes.Tasks.route) },
                    icon = { Icon(Icons.Default.Work, "Tasks") },
                    label = { Text("Tasks") }
                )
                NavigationBarItem(
                    selected = true,
                    onClick = { },
                    icon = { Icon(Icons.Default.Person, "Profile") },
                    label = { Text("Profile") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFFF6B35),
                        selectedTextColor = Color(0xFFFF6B35),
                        indicatorColor = Color(0xFFFFE5D9)
                    )
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
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Profile",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Profile Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(Color(0xFFFFE5D9), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = userProfile.initials,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF6B35)
                        )
                    }

                    Spacer(Modifier.width(16.dp))

                    Column {
                        Text(
                            text = userProfile.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = userProfile.designation,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            color = Color(0xFFE8F5E9),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Wifi,
                                    contentDescription = null,
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Online",
                                    color = Color(0xFF4CAF50),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Statistics Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "Check-ins",
                    value = statistics.checkIns.toString(),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Expenses",
                    value = statistics.expenses.toString(),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Tasks Done",
                    value = statistics.tasksDone.toString(),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(16.dp))

            // Info Items
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ProfileInfoItem(
                        icon = Icons.Default.Badge,
                        label = "Employee ID",
                        value = userProfile.employeeId
                    )
                    Divider()
                    ProfileInfoItem(
                        icon = Icons.Default.Email,
                        label = "Email",
                        value = userProfile.email
                    )
                    Divider()
                    ProfileInfoItem(
                        icon = Icons.Default.Business,
                        label = "Department",
                        value = userProfile.department
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Sign Out Button
            Button(
                onClick = { showSignOutDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF5252)
                )
            ) {
                Icon(Icons.Default.ExitToApp, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Sign Out",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(Modifier.height(8.dp))

            // App Version
            Text(
                text = "FieldEng App v1.0.0",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))
        }
    }

    // Sign Out Dialog
    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign Out") },
            text = { Text("Are you sure you want to sign out?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.signOut()
                        showSignOutDialog = false
                        onLogout()
                    }
                ) {
                    Text("Sign Out", color = Color(0xFFFF5252))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFF6B35)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun ProfileInfoItem(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = Color(0xFFF5F5F5),
            shape = CircleShape,
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFF757575),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(Modifier.width(16.dp))

        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}