package com.company.fieldapp.ui.expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.company.fieldapp.navigation.NavRoutes

@Composable
fun ExpensesScreen(navController: NavHostController) {
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
                    selected = true,
                    onClick = { },
                    icon = { Icon(Icons.Default.Receipt, "Expenses") },
                    label = { Text("Expenses") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFFF6B35),
                        selectedTextColor = Color(0xFFFF6B35),
                        indicatorColor = Color(0xFFFFE5D9)
                    )
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate(NavRoutes.Tasks.route) },
                    icon = { Icon(Icons.Default.Work, "Tasks") },
                    label = { Text("Tasks") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate(NavRoutes.Profile.route) },
                    icon = { Icon(Icons.Default.Person, "Profile") },
                    label = { Text("Profile") }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Receipt,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = Color.Gray
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Expenses Screen",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Coming Soon",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Gray
            )
        }
    }
}