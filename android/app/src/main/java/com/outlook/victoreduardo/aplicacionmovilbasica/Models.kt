package com.outlook.victoreduardo.aplicacionmovilbasica

// --- MODELOS DE PETICIÓN ---
data class AuthRequest(
    val username: String,
    val password: String
)

data class TaskRequest(
    val title: String,
    val description: String = "",
    val status: String = "pending"
)

// --- MODELOS DE RESPUESTA ---
data class UserDto(
    val id: Int,
    val username: String
)

data class AuthResponse(
    val message: String?,
    val access_token: String?,
    val user: UserDto?,
    val error: String?
)

data class TaskDto(
    val id: Int,
    val title: String,
    val description: String,
    val status: String,
    val user_id: Int
)

data class TasksListResponse(
    val tasks: List<TaskDto>?,
    val error: String?
)

data class SingleTaskResponse(
    val message: String?,
    val task: TaskDto?,
    val error: String?
)

data class ApiResponse(
    val message: String?,
    val error: String?
)