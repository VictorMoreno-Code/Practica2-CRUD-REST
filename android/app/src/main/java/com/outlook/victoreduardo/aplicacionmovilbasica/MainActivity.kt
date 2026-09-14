/*
 * MainActivity.kt
 *
 * Interfaz completa en Jetpack Compose (Material 3) para consumir la API
 * REST de tareas (Flask + JWT). Este archivo asume que YA EXISTEN y están
 * listos para usarse (no se generan aquí):
 *
 *   - RetrofitClient.apiService, con los métodos suspend:
 *       register(request: AuthRequest): Response<ApiResponse>
 *       login(request: AuthRequest): Response<AuthResponse>
 *       getTasks(token: String): Response<TasksListResponse>
 *       createTask(token: String, request: TaskRequest): Response<SingleTaskResponse>
 *       updateTask(token: String, id: Int, request: TaskRequest): Response<SingleTaskResponse>
 *       deleteTask(token: String, id: Int): Response<ApiResponse>
 *   - Modelos (definidos en Models.kt): AuthRequest, TaskRequest, TaskDto
 *     (id, title, description, status), AuthResponse (con access_token),
 *     ApiResponse (wrapper genérico de mensaje/estado), TasksListResponse
 *     (contiene "tasks": List<TaskDto>), SingleTaskResponse (contiene "task": TaskDto).
 *
 * NOTA IMPORTANTE: apiService devuelve retrofit2.Response<T>, lo que permite
 * inspeccionar el código HTTP real que manda el backend (200, 201, 400, 401,
 * 404) y mostrar el mensaje de error correspondiente.
 *
 * Paquete y tema ya ajustados al proyecto "AplicacionMovilBasica".
 */

package com.outlook.victoreduardo.aplicacionmovilbasica

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.Response
import com.outlook.victoreduardo.aplicacionmovilbasica.ui.theme.AplicacionMovilBasicaTheme

// Si RetrofitClient y las clases de Models.kt (AuthRequest, TaskRequest, TaskDto,
// AuthResponse, ApiResponse, TasksListResponse, SingleTaskResponse) viven en un
// subpaquete distinto (p. ej. .data o .network), agrega aquí sus imports, por ejemplo:
// import com.outlook.victoreduardo.aplicacionmovilbasica.data.RetrofitClient
// import com.outlook.victoreduardo.aplicacionmovilbasica.data.*

// ---------------------------------------------------------------------------
// Navegación simple basada en estado (sin Navigation Compose)
// ---------------------------------------------------------------------------

private enum class Screen(val label: String) {
    LOGIN("Inicio de Sesión"),
    REGISTER("Registro de Usuario"),
    CRUD("Operaciones CRUD"),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Ajusta la ruta del import de arriba si tu tema generado por
            // Android Studio vive en otro subpaquete.
            AplicacionMovilBasicaTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Raíz de la app: Scaffold + TopAppBar con menú desplegable
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot() {
    var currentScreen by remember { mutableStateOf(Screen.LOGIN) }
    // Token con el prefijo "Bearer " ya incluido, listo para el header Authorization.
    var authToken by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            AppTopBar(
                currentScreen = currentScreen,
                onSelectScreen = { screen ->
                    if (screen == Screen.CRUD && authToken == null) {
                        Toast.makeText(
                            context,
                            "Debes iniciar sesión antes de ver tus tareas",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        currentScreen = screen
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (currentScreen) {
                Screen.LOGIN -> LoginScreen(
                    onLoginSuccess = { token ->
                        authToken = token
                        currentScreen = Screen.CRUD
                    }
                )

                Screen.REGISTER -> RegisterScreen(
                    onRegisterSuccess = { currentScreen = Screen.LOGIN }
                )

                Screen.CRUD -> {
                    val token = authToken
                    if (token == null) {
                        // Defensa extra por si se llega aquí sin sesión activa.
                        Text(
                            text = "Inicia sesión para ver tus tareas.",
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(16.dp)
                        )
                    } else {
                        CrudScreen(token = token)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(
    currentScreen: Screen,
    onSelectScreen: (Screen) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    TopAppBar(
        title = { Text(currentScreen.label) },
        actions = {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Menú de navegación")
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                Screen.entries.forEach { screen ->
                    DropdownMenuItem(
                        text = { Text(screen.label) },
                        onClick = {
                            menuExpanded = false
                            onSelectScreen(screen)
                        }
                    )
                }
            }
        }
    )
}

// ---------------------------------------------------------------------------
// Helpers de red: manejo uniforme de respuestas y errores del backend
// ---------------------------------------------------------------------------

/**
 * Extrae un mensaje de error legible del cuerpo de error del backend Flask,
 * que siempre responde JSON con la forma {"error": "..."}. Si no puede
 * parsear el cuerpo, cae en un mensaje genérico con el código HTTP.
 */
private fun errorMessageFrom(response: Response<*>): String {
    return try {
        val rawError = response.errorBody()?.string()
        if (!rawError.isNullOrBlank()) {
            JSONObject(rawError).optString("error", "Error ${response.code()}")
        } else {
            "Error ${response.code()}"
        }
    } catch (e: Exception) {
        "Error ${response.code()}"
    }
}

/** Mensaje uniforme para fallas de red (sin conexión, timeout, DNS, etc.). */
private fun connectionErrorMessage(e: Exception): String {
    return "No se pudo conectar con el servidor: ${e.localizedMessage ?: e.toString()}"
}

// ---------------------------------------------------------------------------
// Pantalla de Registro
// ---------------------------------------------------------------------------

@Composable
private fun RegisterScreen(onRegisterSuccess: () -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Crear cuenta", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Usuario") },
            singleLine = true,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            singleLine = true,
            enabled = !isLoading,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                if (username.isBlank() || password.isBlank()) {
                    Toast.makeText(context, "Usuario y contraseña son obligatorios", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                scope.launch {
                    isLoading = true
                    try {
                        // register devuelve Response<ApiResponse>; basta con el código HTTP.
                        val response = RetrofitClient.apiService.register(
                            AuthRequest(username = username, password = password)
                        )
                        if (response.isSuccessful) {
                            Toast.makeText(context, "Usuario registrado correctamente", Toast.LENGTH_SHORT).show()
                            username = ""
                            password = ""
                            onRegisterSuccess()
                        } else {
                            Toast.makeText(context, errorMessageFrom(response), Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, connectionErrorMessage(e), Toast.LENGTH_LONG).show()
                    } finally {
                        isLoading = false
                    }
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Registrarme")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Pantalla de Login
// ---------------------------------------------------------------------------

@Composable
private fun LoginScreen(onLoginSuccess: (token: String) -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Iniciar sesión", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Usuario") },
            singleLine = true,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            singleLine = true,
            enabled = !isLoading,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                if (username.isBlank() || password.isBlank()) {
                    Toast.makeText(context, "Usuario y contraseña son obligatorios", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                scope.launch {
                    isLoading = true
                    try {
                        val response: Response<AuthResponse> = RetrofitClient.apiService.login(
                            AuthRequest(username = username, password = password)
                        )
                        if (response.isSuccessful) {
                            val body = response.body()
                            val rawToken = body?.access_token
                            if (rawToken.isNullOrBlank()) {
                                Toast.makeText(context, "Respuesta inválida del servidor", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Inicio de sesión exitoso", Toast.LENGTH_SHORT).show()
                                onLoginSuccess("Bearer $rawToken")
                            }
                        } else {
                            // Aquí típicamente cae un 401 por credenciales inválidas.
                            Toast.makeText(context, errorMessageFrom(response), Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, connectionErrorMessage(e), Toast.LENGTH_LONG).show()
                    } finally {
                        isLoading = false
                    }
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Entrar")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Pantalla CRUD
// ---------------------------------------------------------------------------

@Composable
private fun CrudScreen(token: String) {
    var tasks by remember { mutableStateOf<List<TaskDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    // Estado del diálogo de creación/edición.
    var showDialog by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<TaskDto?>(null) }
    var dialogTitle by remember { mutableStateOf("") }
    var dialogDescription by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun refreshTasks() {
        scope.launch {
            isLoading = true
            try {
                // getTasks devuelve Response<TasksListResponse>; la lista real
                // viene en el campo "tasks" del cuerpo.
                val response = RetrofitClient.apiService.getTasks(token)
                if (response.isSuccessful) {
                    tasks = response.body()?.tasks ?: emptyList()
                } else {
                    Toast.makeText(context, errorMessageFrom(response), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, connectionErrorMessage(e), Toast.LENGTH_LONG).show()
            } finally {
                isLoading = false
            }
        }
    }

    // Carga inicial de tareas al entrar a la pantalla.
    LaunchedEffect(token) {
        refreshTasks()
    }

    fun openCreateDialog() {
        editingTask = null
        dialogTitle = ""
        dialogDescription = ""
        showDialog = true
    }

    fun openEditDialog(task: TaskDto) {
        editingTask = task
        dialogTitle = task.title
        dialogDescription = task.description ?: ""
        showDialog = true
    }

    fun saveTask() {
        if (dialogTitle.isBlank()) {
            Toast.makeText(context, "El título es obligatorio", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            isSaving = true
            try {
                val current = editingTask
                val request = TaskRequest(
                    title = dialogTitle,
                    description = dialogDescription,
                    status = current?.status ?: "pending"
                )
                // createTask/updateTask devuelven Response<SingleTaskResponse>;
                // la tarea guardada viene en el campo "task" del cuerpo.
                val response = if (current == null) {
                    RetrofitClient.apiService.createTask(token, request)
                } else {
                    RetrofitClient.apiService.updateTask(token, current.id, request)
                }
                if (response.isSuccessful) {
                    val savedTask = response.body()?.task
                    if (savedTask == null) {
                        // Código 2xx pero sin cuerpo de tarea: se avisa, pero
                        // igual se refresca la lista para no dejar la UI inconsistente.
                        Toast.makeText(context, "Respuesta inesperada del servidor", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(
                            context,
                            if (current == null) "Tarea creada" else "Tarea actualizada",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    showDialog = false
                    refreshTasks()
                } else {
                    Toast.makeText(context, errorMessageFrom(response), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, connectionErrorMessage(e), Toast.LENGTH_LONG).show()
            } finally {
                isSaving = false
            }
        }
    }

    fun deleteTask(task: TaskDto) {
        scope.launch {
            isLoading = true
            try {
                // deleteTask devuelve Response<ApiResponse>; basta con el código HTTP.
                val response = RetrofitClient.apiService.deleteTask(token, task.id)
                if (response.isSuccessful) {
                    Toast.makeText(context, "Tarea eliminada", Toast.LENGTH_SHORT).show()
                    refreshTasks()
                } else {
                    Toast.makeText(context, errorMessageFrom(response), Toast.LENGTH_LONG).show()
                    isLoading = false
                }
            } catch (e: Exception) {
                Toast.makeText(context, connectionErrorMessage(e), Toast.LENGTH_LONG).show()
                isLoading = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading && tasks.isEmpty() -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            tasks.isEmpty() -> {
                Text(
                    text = "No tienes tareas todavía. Usa el botón + para crear una.",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items = tasks, key = { it.id }) { task ->
                        TaskCard(
                            task = task,
                            onEdit = { openEditDialog(task) },
                            onDelete = { deleteTask(task) }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { openCreateDialog() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Nueva tarea")
        }
    }

    if (showDialog) {
        TaskDialog(
            isEditing = editingTask != null,
            title = dialogTitle,
            description = dialogDescription,
            isSaving = isSaving,
            onTitleChange = { dialogTitle = it },
            onDescriptionChange = { dialogDescription = it },
            onConfirm = { saveTask() },
            onDismiss = { if (!isSaving) showDialog = false }
        )
    }
}

@Composable
private fun TaskCard(
    task: TaskDto,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = task.title, style = MaterialTheme.typography.titleMedium)
                if (!task.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = task.description, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Estado: ${task.status}", style = MaterialTheme.typography.labelSmall)
            }

            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Editar tarea")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar tarea")
            }
        }
    }
}

@Composable
private fun TaskDialog(
    isEditing: Boolean,
    title: String,
    description: String,
    isSaving: Boolean,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Editar tarea" else "Nueva tarea") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    label = { Text("Título") },
                    singleLine = true,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = onDescriptionChange,
                    label = { Text("Descripción") },
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isSaving) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (isEditing) "Guardar" else "Crear")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("Cancelar")
            }
        }
    )
}