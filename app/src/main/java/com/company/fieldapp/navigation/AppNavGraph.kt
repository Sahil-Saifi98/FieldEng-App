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

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val sessionManager = SessionManager(context)

    // Determine start destination based on login status
    val startDestination = if (sessionManager.isLoggedIn()) {
        NavRoutes.Attendance.route
    } else {
        NavRoutes.Login.route
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(NavRoutes.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    // Clear back stack and navigate to attendance
                    navController.navigate(NavRoutes.Attendance.route) {
                        popUpTo(NavRoutes.Login.route) { inclusive = true }
                    }
                }
            )
        }

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
                    // Navigate to login and clear back stack
                    navController.navigate(NavRoutes.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}