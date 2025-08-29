package com.sd_project.photo_app_sd

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import java.io.ByteArrayOutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private fun showCameraPermissionDialog() {
        val dialog = android.app.Dialog(this)
        dialog.setContentView(androidx.appcompat.widget.AppCompatTextView(this).apply {
            text = "\uD83D\uDD12 Permissão de Câmera Necessária\n\nEste app precisa da sua permissão para acessar a câmera.\n\nClique em Permitir para continuar."
            setTextColor(android.graphics.Color.parseColor("#00fff7"))
            textSize = 20f
            setPadding(60, 60, 60, 60)
            setBackgroundColor(android.graphics.Color.parseColor("#B39DDB"))
            background.alpha = 220
            typeface = android.graphics.Typeface.MONOSPACE
            gravity = android.view.Gravity.CENTER
        })
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCancelable(false)
        dialog.show()

        // Botão estilizado para permitir
        val button = android.widget.Button(this).apply {
            text = "Permitir"
            setTextColor(android.graphics.Color.parseColor("#ffe600"))
            setBackgroundColor(android.graphics.Color.parseColor("#7C4DFF"))
            background.alpha = 180
            textSize = 18f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(40, 20, 40, 20)
            setOnClickListener {
                dialog.dismiss()
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
        (dialog.findViewById<androidx.appcompat.widget.AppCompatTextView>(android.R.id.content)?.parent as? android.widget.FrameLayout)?.addView(button)
    }
    
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
    // Exibir popup estilizado ao abrir o app
    showCameraPermissionDialog()
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
                    sendPhotoViaSocket(serverAddress, photoUri!!)
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
    
    private fun sendPhotoViaSocket(serverAddress: String, photoUri: Uri) {
        binding.tvStatus.text = "Preparando envio para $serverAddress..."
        
        Thread {
            try {
                // Parse do endereço do servidor
                val parts = serverAddress.split(":")
                if (parts.size != 2) {
                    runOnUiThread {
                        binding.tvStatus.text = "❌ Formato inválido. Use 'host:port'"
                    }
                    return@Thread
                }
                
                val host = parts[0]
                val port = parts[1].toIntOrNull() ?: 5000
                
                runOnUiThread {
                    binding.tvStatus.text = "Processando imagem..."
                }
                
                // Carregar e processar a imagem
                val inputStream = contentResolver.openInputStream(photoUri)
                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                
                if (originalBitmap == null) {
                    runOnUiThread {
                        binding.tvStatus.text = "❌ Erro ao processar imagem"
                    }
                    return@Thread
                }
                
                // Redimensionar imagem conforme requisito (largura máxima 1280px)
                val resizedBitmap = if (originalBitmap.width > 1280) {
                    val ratio = 1280.0 / originalBitmap.width
                    val newHeight = (originalBitmap.height * ratio).toInt()
                    Bitmap.createScaledBitmap(originalBitmap, 1280, newHeight, true)
                } else {
                    originalBitmap
                }
                
                // Converter para JPEG com qualidade 80
                val byteArrayOutputStream = ByteArrayOutputStream()
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream)
                val imageBytes = byteArrayOutputStream.toByteArray()
                
                runOnUiThread {
                    binding.tvStatus.text = "Conectando ao servidor $host:$port..."
                }
                
                // Estabelecer conexão com o servidor
                val socket = Socket(host, port)
                val outputStream = socket.getOutputStream()
                
                // Enviar o tamanho da imagem (4 bytes)
                val imageSize = imageBytes.size
                val sizeBuffer = ByteBuffer.allocate(4).putInt(imageSize).array()
                outputStream.write(sizeBuffer)
                outputStream.flush()
                
                // Enviar os bytes da imagem
                runOnUiThread {
                    binding.tvStatus.text = "Enviando imagem (${imageSize / 1024} KB)..."
                }
                
                // Enviar em chunks para ter feedback de progresso
                val chunkSize = 4096
                var bytesSent = 0
                
                while (bytesSent < imageSize) {
                    val remaining = imageSize - bytesSent
                    val currentChunkSize = minOf(chunkSize, remaining)
                    
                    outputStream.write(imageBytes, bytesSent, currentChunkSize)
                    bytesSent += currentChunkSize
                    
                    val progress = (bytesSent.toFloat() / imageSize) * 100
                    val progressText = String.format("%.1f%%", progress)
                    
                    runOnUiThread {
                        binding.tvStatus.text = "Enviando: $progressText"
                    }
                }
                
                outputStream.flush()
                socket.close()
                
                runOnUiThread {
                    binding.tvStatus.text = "✅ Foto enviada com sucesso!"
                    Toast.makeText(this, "Foto enviada com sucesso!", Toast.LENGTH_SHORT).show()
                }
                
            } catch (e: Exception) {
                Log.e("MainActivity", "Erro ao enviar foto via socket", e)
                runOnUiThread {
                    binding.tvStatus.text = "❌ Erro: ${e.message}"
                    Toast.makeText(this, "Erro ao enviar: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}