package com.outlook.victoreduardo.aplicacionmovilbasica

import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @POST("register")
    suspend fun register(@Body request: AuthRequest): Response<ApiResponse>

    @POST("login")
    suspend fun login(@Body request: AuthRequest): Response<AuthResponse>

    @GET("tasks")
    suspend fun getTasks(@Header("Authorization") token: String): Response<TasksListResponse>

    @POST("tasks")
    suspend fun createTask(
        @Header("Authorization") token: String,
        @Body request: TaskRequest
    ): Response<SingleTaskResponse>

    @PUT("tasks/{id}")
    suspend fun updateTask(
        @Header("Authorization") token: String,
        @Path("id") taskId: Int,
        @Body request: TaskRequest
    ): Response<SingleTaskResponse>

    @DELETE("tasks/{id}")
    suspend fun deleteTask(
        @Header("Authorization") token: String,
        @Path("id") taskId: Int
    ): Response<ApiResponse>
}