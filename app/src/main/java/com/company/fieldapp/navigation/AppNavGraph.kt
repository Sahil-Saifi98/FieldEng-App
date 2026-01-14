package com.company.fieldapp.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.company.fieldapp.data.session.SessionManager
import com.company.fieldapp.ui.attendance.AttendanceScreen
import com.company.fieldapp.ui.expenses.ExpensesScreen
import com.company.fieldapp.ui.login.LoginScreen
import com.company.fieldapp.ui.tasks.TasksScreen
import com.company.fieldapp.ui.profile.ProfileScreen
import com.company.fieldapp.ui.admin.AdminDashboardScreen
import com.company.fieldapp.ui.admin.AdminAttendanceScreen
import com.company.fieldapp.ui.admin.AdminExportScreen

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val sessionManager = SessionManager(context)

    // Determine start destination based on login status and role
    val startDestination = if (sessionManager.isLoggedIn()) {
        val userRole = sessionManager.getRole()
        if (userRole == "admin") {
            NavRoutes.AdminDashboard.route
        } else {
            NavRoutes.Attendance.route
        }
    } else {
        NavRoutes.Login.route
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(NavRoutes.Login.route) {
            LoginScreen(
                onLoginSuccess = { isAdmin ->
                    // Navigate based on role
                    val destination = if (isAdmin) {
                        NavRoutes.AdminDashboard.route
                    } else {
                        NavRoutes.Attendance.route
                    }

                    navController.navigate(destination) {
                        popUpTo(NavRoutes.Login.route) { inclusive = true }
                    }
                }
            )
        }

        // Employee routes
        composable(NavRoutes.Attendance.route) {
            AttendanceScreen(navController = navController)
        }

        composable(NavRoutes.Expenses.route) {
            ExpensesScreen(navController = navController)
        }

        composable(NavRoutes.Tasks.route) {
            TasksScreen(navController = navController)
        }

        composable(NavRoutes.Profile.route) {
            ProfileScreen(
                navController = navController,
                onLogout = {
                    navController.navigate(NavRoutes.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // Admin routes
        composable(NavRoutes.AdminDashboard.route) {
            AdminDashboardScreen(navController = navController)
        }

        composable(NavRoutes.AdminAttendance.route) {
            AdminAttendanceScreen(navController = navController)
        }

        composable(NavRoutes.AdminExport.route) {
            AdminExportScreen(navController = navController)
        }
    }
}