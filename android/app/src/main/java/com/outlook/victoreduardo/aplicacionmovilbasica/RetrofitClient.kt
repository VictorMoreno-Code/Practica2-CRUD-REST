package com.outlook.victoreduardo.aplicacionmovilbasica

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Puerto en el que el backend queda publicado (ver docker-compose.yml). */
private const val BACKEND_PORT = 5000

/**
 * Hosts que se intentan, en orden, hasta que uno responda.
 * Para un teléfono conectado solo por Wi-Fi, agrega aquí como PRIMER elemento
 * la IP local de tu computadora, por ejemplo "192.168.1.70".
 */
private val CANDIDATE_HOSTS = listOf("127.0.0.1", "10.0.2.2")

/** URL base inicial; el interceptor sustituye el host si hace falta. */
private const val BASE_URL = "http://127.0.0.1:5000/"

object RetrofitClient {

    private class HostFailoverInterceptor : Interceptor {

        @Volatile
        private var workingHost: String? = null

        override fun intercept(chain: Interceptor.Chain): Response {
            val original = chain.request()

            // Se prueba primero el host que ya funcionó antes (si lo hay).
            val hostsToTry = workingHost
                ?.let { listOf(it) + CANDIDATE_HOSTS.filter { host -> host != it } }
                ?: CANDIDATE_HOSTS

            var lastError: IOException? = null

            for (host in hostsToTry) {
                val request = original.newBuilder()
                    .url(original.url.newBuilder().host(host).port(BACKEND_PORT).build())
                    .build()
                try {
                    val response = chain.proceed(request)
                    workingHost = host
                    return response
                } catch (e: IOException) {
                    // Host inalcanzable (sin ruta, conexión rechazada, timeout):
                    // se intenta con el siguiente candidato.
                    lastError = e
                }
            }

            throw lastError ?: IOException(
                "No se pudo conectar con el backend en ninguna de las " +
                    "direcciones: ${CANDIDATE_HOSTS.joinToString()} (puerto $BACKEND_PORT)"
            )
        }
    }

    // Registra en Logcat cada petición y respuesta (útil para depurar y para
    // evidenciar en las capturas qué viaja realmente por la red).
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(HostFailoverInterceptor())
        .addInterceptor(loggingInterceptor)
        // Timeout de conexión corto: si un host no responde, se pasa rápido
        // al siguiente candidato en vez de dejar la UI colgada.
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
