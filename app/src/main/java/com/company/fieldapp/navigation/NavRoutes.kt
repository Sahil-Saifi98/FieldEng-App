package com.company.fieldapp.navigation

sealed class NavRoutes(val route: String) {
    object Login : NavRoutes("login")
    object Attendance : NavRoutes("attendance")
    object Expenses : NavRoutes("expenses")
    object Tasks : NavRoutes("tasks")
    object Profile : NavRoutes("profile")
}