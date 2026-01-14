package com.company.fieldapp.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.company.fieldapp.data.session.SessionManager
import com.company.fieldapp.navigation.NavRoutes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(navController: NavHostController) {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    var showLogoutMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Dashboard,
                            contentDescription = null,
                            tint = Color(0xFFFF6B35)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Admin Dashboard", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    Box {
                        Row(
                            modifier = Modifier
                                .clickable { showLogoutMenu = true }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Administrator",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Admin Menu",
                                tint = Color(0xFFFF6B35)
                            )
                        }

                        DropdownMenu(
                            expanded = showLogoutMenu,
                            onDismissRequest = { showLogoutMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Logout,
                                            contentDescription = null,
                                            tint = Color.Red,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text("Logout", color = Color.Red)
                                    }
                                },
                                onClick = {
                                    showLogoutMenu = false
                                    sessionManager.clearSession()
                                    navController.navigate(NavRoutes.Login.route) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Quick Action Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionCard(
                    title = "Attendance",
                    icon = Icons.Default.AccessTime,
                    color = Color(0xFFFF6B35),
                    modifier = Modifier.weight(1f),
                    onClick = { navController.navigate(NavRoutes.AdminAttendance.route) }
                )

                QuickActionCard(
                    title = "Expenses",
                    icon = Icons.Default.Receipt,
                    color = Color(0xFF2196F3),
                    modifier = Modifier.weight(1f),
                    onClick = { /* Navigate to expenses */ }
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionCard(
                    title = "Tasks",
                    icon = Icons.Default.Work,
                    color = Color(0xFF4CAF50),
                    modifier = Modifier.weight(1f),
                    onClick = { /* Navigate to tasks */ }
                )

                QuickActionCard(
                    title = "Export",
                    icon = Icons.Default.Download,
                    color = Color(0xFFFF9800),
                    modifier = Modifier.weight(1f),
                    onClick = { navController.navigate(NavRoutes.AdminExport.route) }
                )
            }

            Spacer(Modifier.height(24.dp))

            // Recent Activity Section
            Text(
                text = "Recent Activity",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Dashboard Overview",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Select an option above to view detailed reports and manage data.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun QuickActionCard(
    title: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = color,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}