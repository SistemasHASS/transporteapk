package com.example.transporteapk;

import android.content.pm.PackageManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.util.Log;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.common.InputImage;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.media.AudioManager;
import android.media.ToneGenerator;

public class ScannerActivity extends AppCompatActivity {
    private PreviewView previewView;
    private Camera camera;
    private ExecutorService cameraExecutor;
    public static WebView webViewRef; // referencia al WebView principal
    private static final int REQUEST_CAMERA_PERMISSION = 1001;
    private ToneGenerator toneGenerator;
    private TextView tvContador, tvUltimoCodigo;
    private int contador = 0;
    private final Set<String> codigosEscaneados = new HashSet<>();
    private boolean linternaEncendida = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 100); // 100 = volumen
        setContentView(R.layout.activity_scanner);

        previewView = findViewById(R.id.previewView);
        tvContador = findViewById(R.id.tvContador);
        tvUltimoCodigo = findViewById(R.id.tvUltimoCodigo);
        cameraExecutor = Executors.newSingleThreadExecutor();

        // 🔹 Botón para cerrar la cámara
        Button btnCerrar = findViewById(R.id.btnCerrar);
        btnCerrar.setOnClickListener(v -> {
            if (cameraExecutor != null && !cameraExecutor.isShutdown()) {
                cameraExecutor.shutdown();
            }
            finish(); // Vuelve al WebView
        });

        // 🔹 Botón para encender/apagar linterna
        Button btnLinterna = findViewById(R.id.btnLinterna);
        btnLinterna.setOnClickListener(v -> toggleLinterna(btnLinterna));

        // 🔹 Verificar permisos de cámara
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{android.Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION
            );
        }
    }

    private void toggleLinterna(Button btnLinterna) {
        if (camera != null && camera.getCameraInfo().hasFlashUnit()) {
            linternaEncendida = !linternaEncendida;
            camera.getCameraControl().enableTorch(linternaEncendida);
            btnLinterna.setText(linternaEncendida ? "Apagar Linterna" : "Encender Linterna");
        } else {
            Toast.makeText(this, "Tu dispositivo no tiene linterna", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION &&
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis imageAnalysis =
                        new ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build();

                imageAnalysis.setAnalyzer(cameraExecutor, this::processImageProxy);

                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build();

                cameraProvider.unbindAll();
                camera = cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, imageAnalysis);

                // Guardamos referencia en el bridge para controlar torch
                CamaraBridge.setCamera(camera);

            } catch (ExecutionException | InterruptedException e) {
                Log.e("ScannerActivity", "Error al iniciar cámara", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void processImageProxy(ImageProxy image) {
        @SuppressWarnings("UnsafeOptInUsageError")
        InputImage inputImage = InputImage.fromMediaImage(
                image.getImage(), image.getImageInfo().getRotationDegrees());

        BarcodeScanning.getClient().process(inputImage)
                .addOnSuccessListener(this::handleBarcodes)
                .addOnCompleteListener(task -> image.close());
    }

    private void handleBarcodes(List<Barcode> barcodes) {
        for (Barcode barcode : barcodes) {
            String valor = barcode.getRawValue();
            if (valor != null) {
                procesarCodigo(valor);
            }
        }
    }

    // 🔹 Procesa un código y evita duplicados
    private void procesarCodigo(String codigo) {
        if (codigosEscaneados.contains(codigo)) {
            Log.d("ScannerActivity", "⚠️ Código ya escaneado, ignorado: " + codigo);
            return;
        }

        // Guardar como nuevo
        codigosEscaneados.add(codigo);
        contador++;

        runOnUiThread(() -> {
            tvUltimoCodigo.setText("Último QR: " + codigo);
            tvContador.setText("Contador: " + contador);
            Toast.makeText(this, "QR leído: " + codigo, Toast.LENGTH_SHORT).show();
            if (toneGenerator != null) {
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150);
            }
        });

        Log.d("ScannerActivity", "Nuevo código leído: " + codigo);

        // 🔹 Enviar al WebView si existe
        if (webViewRef != null) {
            runOnUiThread(() -> webViewRef.evaluateJavascript(
                    "window.onDniLeido && window.onDniLeido(\"" + codigo + "\")", null));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        if (toneGenerator != null) {
            toneGenerator.release();
            toneGenerator = null;
        }
    }
}
