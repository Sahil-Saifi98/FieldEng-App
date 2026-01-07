package com.company.fieldapp.data.model

data class User(
    val employeeId: String,
    val name: String,
    val password: String,
    val email: String,
    val department: String,
    val designation: String
)

// Predefined users for local authentication
object UserDatabase {
    val users = listOf(
        User(
            employeeId = "EMP-2024-001",
            name = "John Engineer",
            password = "password123",
            email = "john.engineer@company.com",
            department = "Field Operations",
            designation = "Senior Field Engineer"
        ),
        User(
            employeeId = "EMP-2024-002",
            name = "Sarah Smith",
            password = "sarah123",
            email = "sarah.smith@company.com",
            department = "Technical Services",
            designation = "Field Engineer"
        ),
        User(
            employeeId = "EMP-2024-003",
            name = "Mike Johnson",
            password = "mike123",
            email = "mike.johnson@company.com",
            department = "Maintenance",
            designation = "Lead Technician"
        )
    )

    fun authenticate(employeeId: String, password: String): User? {
        return users.find {
            it.employeeId == employeeId && it.password == password
        }
    }

    fun getUserById(employeeId: String): User? {
        return users.find { it.employeeId == employeeId }
    }
}