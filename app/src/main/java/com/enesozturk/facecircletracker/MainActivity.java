package com.enesozturk.facecircletracker;

import android.Manifest;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private PreviewView previewView;
    private ImageView faceGuide;
    private TextView directionText;
    private CircularProgressView circleProgress;
    private ExecutorService cameraExecutor;

    // eşik değerleri
    private static final float YAW_SIDE = 30f;  // rotY
    private static final float PITCH_UP = 15f;  // rotX

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) startCamera();
                else Toast.makeText(this, "Kamera izni reddedildi", Toast.LENGTH_SHORT).show();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView   = findViewById(R.id.previewView);
        faceGuide     = findViewById(R.id.faceGuide);
        directionText = findViewById(R.id.directionText);
        circleProgress= findViewById(R.id.circleProgress);

        cameraExecutor = Executors.newSingleThreadExecutor();

        startGlowAnimation();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        } else {
            startCamera();
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                androidx.camera.core.Preview preview = new androidx.camera.core.Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, new FaceAnalyzer((rotY, rotX, boundingBox, imageW, imageH) -> {
                    runOnUiThread(() -> {
                        if (circleProgress.isAllCompleted()) return;

                        // yüz çember içinde mi?
                        Rect scaledBox = scaleBoundingBox(boundingBox, imageW, imageH,
                                previewView.getWidth(), previewView.getHeight());
                        if (!isFaceInsideCircle(scaledBox)) {
                            directionText.setText("Yüzünüzü çemberin içine yerleştiriniz");
                            return;
                        } else {
                            directionText.setText("Eksik dilimleri doldurun");
                        }

                        // Merkez: düz bakış
                        if (Math.abs(rotY) < 10 && Math.abs(rotX) < 10) {
                            circleProgress.completeCenter();
                        }

                        // Hedef yön — segment indeksini bul
                        Integer idx = classifyDirection(rotY, rotX);
                        if (idx != null) {
                            circleProgress.completeDirection(idx);
                        }

                        if (circleProgress.isAllCompleted()) {
                            directionText.setText("Yüz başarıyla algılandı ✅");
                            faceGuide.setVisibility(View.GONE);
                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("✅ Başarılı")
                                    .setMessage("Tüm dilimler doldu. Yüz başarıyla algılandı.")
                                    .setCancelable(false)
                                    .setPositiveButton("Tamam", (d, w) -> d.dismiss())
                                    .show();
                        }
                    });
                }));

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(this, "Kamera başlatma hatası", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    // rotY (yaw): - sağ, + sol   — rotX (pitch): + yukarı, - aşağı
    // Segment indexleri: up(0), rightUp(1), right(2), rightDown(3), down(4),
    // leftDown(5), left(6), leftUp(7)
    private Integer classifyDirection(float rotY, float rotX) {
        boolean right = rotY < -YAW_SIDE;
        boolean left  = rotY >  YAW_SIDE;
        boolean up    = rotX >  PITCH_UP;
        boolean down  = rotX < -PITCH_UP;

        if (right && up)  return 1; // rightUp
        if (right && down) return 3; // rightDown
        if (left  && up)   return 7; // leftUp
        if (left  && down) return 5; // leftDown

        if (right) return 2; // right
        if (left)  return 6; // left
        if (up)    return 0; // up
        if (down)  return 4; // down

        return null;
    }

    private Rect scaleBoundingBox(Rect box, int imageW, int imageH, int viewW, int viewH) {
        float scaleX = (float) viewW / imageW;
        float scaleY = (float) viewH / imageH;
        return new Rect(
                (int) (box.left * scaleX),
                (int) (box.top * scaleY),
                (int) (box.right * scaleX),
                (int) (box.bottom * scaleY)
        );
    }

    private boolean isFaceInsideCircle(Rect faceBox) {
        int[] loc = new int[2];
        circleProgress.getLocationOnScreen(loc);
        int cx = loc[0] + circleProgress.getWidth() / 2;
        int cy = loc[1] + circleProgress.getHeight() / 2;
        int radius = circleProgress.getWidth() / 2;

        int fx = faceBox.centerX();
        int fy = faceBox.centerY();

        double dist = Math.hypot(fx - cx, fy - cy);
        return dist < radius * 0.80;
    }

    private void startGlowAnimation() {
        int startColor = 0xFF66CCFF; // açık mavi
        int endColor   = 0xFF00FFFF; // cam göbeği

        ValueAnimator glow = ValueAnimator.ofObject(new ArgbEvaluator(), startColor, endColor, startColor);
        glow.setDuration(2000);
        glow.setRepeatCount(ValueAnimator.INFINITE);
        glow.setRepeatMode(ValueAnimator.REVERSE);
        glow.addUpdateListener(a -> circleProgress.setBorderColor((int) a.getAnimatedValue()));
        glow.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) cameraExecutor.shutdown();
    }
}
