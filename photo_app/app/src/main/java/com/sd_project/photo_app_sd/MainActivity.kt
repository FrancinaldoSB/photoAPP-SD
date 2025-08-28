package com.sd_project.photo_app_sd

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.sd_project.photo_app_sd.databinding.ActivityMainBinding
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private var photoUri: Uri? = null
    
    // Launcher para pedir permissões
    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            binding.tvStatus.text = "✅ Permissão da câmera concedida!"
            startCamera()
        } else {
            binding.tvStatus.text = "❌ Permissão da câmera negada"
            Toast.makeText(this, "Precisamos da permissão da câmera", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            // Configurar View Binding
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
            
            // Inicializar executor da câmera
            cameraExecutor = Executors.newSingleThreadExecutor()
            
            // Configurar botões
            binding.btnRequestPermissions.setOnClickListener {
                checkCameraPermission()
            }
            
            binding.btnTakePhoto.setOnClickListener {
                takePhoto()
            }
            
            binding.btnSendPhoto.setOnClickListener {
                val serverAddress = binding.editServerAddress.text.toString()
                if (serverAddress.isNotEmpty() && photoUri != null) {
                    sendPhoto(serverAddress, photoUri!!)
                } else {
                    Toast.makeText(this, 
                        "Por favor, informe o endereço do servidor e tire uma foto", 
                        Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            // Tratamento de erro para depuração
            Toast.makeText(this, "Erro ao iniciar: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
            
            // Fallback para layout simples em caso de erro
            setContentView(TextView(this).apply { 
                text = "Erro ao carregar layout: ${e.message}"
                gravity = android.view.Gravity.CENTER
                setPadding(20, 20, 20, 20)
            })
        }
    }
    
    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permissão já concedida
                binding.tvStatus.text = "✅ Permissão já estava concedida!"
                startCamera()
            }
            else -> {
                // Pedir permissão
                binding.tvStatus.text = "Pedindo permissão da câmera..."
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
    
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }
            
            imageCapture = ImageCapture.Builder().build()
            
            try {
                cameraProvider.unbindAll()
                
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
                
                binding.tvStatus.text = "Câmera iniciada com sucesso"
            } catch (e: Exception) {
                binding.tvStatus.text = "Falha ao iniciar câmera: ${e.message}"
                Log.e("MainActivity", "Erro na inicialização da câmera", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }
    
    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        
        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.getDefault())
            .format(System.currentTimeMillis())
        
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PhotoApp")
            }
        }
        
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
            .build()
        
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    photoUri = outputFileResults.savedUri
                    
                    val msg = "Foto salva com sucesso: ${photoUri.toString()}"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    binding.tvStatus.text = "✅ Foto capturada!"
                    
                    // Mostrar a foto capturada
                    binding.imagePreview.setImageURI(photoUri)
                    binding.imagePreview.visibility = android.view.View.VISIBLE
                }
                
                override fun onError(exception: ImageCaptureException) {
                    binding.tvStatus.text = "❌ Falha ao capturar foto: ${exception.message}"
                    Log.e("MainActivity", "Erro ao salvar foto", exception)
                }
            }
        )
    }
    
    private fun sendPhoto(serverAddress: String, photoUri: Uri) {
        binding.tvStatus.text = "Enviando foto para $serverAddress..."
        
        Thread {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                
                // Obter o arquivo da URI
                val inputStream = contentResolver.openInputStream(photoUri)
                val fileBytes = inputStream?.readBytes()
                inputStream?.close()
                
                if (fileBytes == null) {
                    runOnUiThread {
                        binding.tvStatus.text = "❌ Erro: Não foi possível ler o arquivo"
                    }
                    return@Thread
                }
                
                // Criar URL com a rota /upload conforme o servidor espera
                val baseUrl = if (serverAddress.endsWith("/")) {
                    serverAddress.substring(0, serverAddress.length - 1)
                } else {
                    serverAddress
                }
                
                val url = "http://$baseUrl/upload"
                Log.d("MainActivity", "Enviando para: $url")
                
                // Construir corpo multipart com campo 'file' conforme o servidor espera
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "file",
                        "foto.jpg",
                        RequestBody.create("image/jpeg".toMediaTypeOrNull(), fileBytes)
                    )
                    .build()
                
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()
                
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: "Sem resposta"
                
                Log.d("MainActivity", "Resposta: ${response.code} - $responseBody")
                
                runOnUiThread {
                    if (response.isSuccessful) {
                        binding.tvStatus.text = "✅ Foto enviada com sucesso!"
                        try {
                            // Tentar extrair o nome do arquivo da resposta JSON
                            val jsonResponse = org.json.JSONObject(responseBody)
                            val filename = jsonResponse.optString("filename", "")
                            if (filename.isNotEmpty()) {
                                binding.tvStatus.text = "✅ Foto enviada! Nome: $filename"
                            }
                        } catch (e: Exception) {
                            // Ignorar erro de parsing do JSON
                        }
                    } else {
                        binding.tvStatus.text = "❌ Erro ao enviar: ${response.code} - ${response.message}"
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Erro ao enviar foto", e)
                runOnUiThread {
                    binding.tvStatus.text = "❌ Falha na conexão: ${e.message}"
                }
            }
        }.start()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}