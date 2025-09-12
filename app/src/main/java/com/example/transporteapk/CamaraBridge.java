package com.example.transporteapk;

import android.app.Activity;
import android.content.Intent;
import android.webkit.JavascriptInterface;
import androidx.camera.core.Camera;

public class CamaraBridge {
    private Activity activity;
    private static Camera camera; // Cámara global para controlar torch

    public CamaraBridge(Activity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public void leerDnis() {
        Intent intent = new Intent(activity, ScannerActivity.class);
        activity.startActivity(intent);
    }

    @JavascriptInterface
    public void toggleTorch(boolean encender) {
        if (camera != null && camera.getCameraInfo().hasFlashUnit()) {
            camera.getCameraControl().enableTorch(encender);
        }
    }

    public static void setCamera(Camera cam) {
        camera = cam;
    }
}
