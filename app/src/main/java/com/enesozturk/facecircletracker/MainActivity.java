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
    private ImageView directionArrow, faceGuide;
    private TextView directionText;
    private CircularProgressView circleProgress;
    private ExecutorService cameraExecutor;

    private final String[] directions = {"center", "right", "rightUp", "rightDown", "left", "leftUp", "leftDown", "up", "down"};
    private int currentDirectionIndex = 0;
    private boolean[] directionStates = new boolean[9];
    private boolean faceCompleted = false;

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) startCamera();
                else Toast.makeText(this, "Kamera izni reddedildi", Toast.LENGTH_SHORT).show();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.previewView);
        directionArrow = findViewById(R.id.directionArrow);
        directionText = findViewById(R.id.directionText);
        circleProgress = findViewById(R.id.circleProgress);
        faceGuide = findViewById(R.id.faceGuide);
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
                        if (faceCompleted) return;

                        Rect scaledBox = scaleBoundingBox(boundingBox, imageW, imageH,
                                previewView.getWidth(), previewView.getHeight());

                        if (!isFaceInsideCircle(scaledBox)) {
                            directionText.setText("Lütfen yüzünüzü çemberin içine yerleştiriniz.");
                            directionArrow.setVisibility(View.GONE);
                            return;
                        }

                        String target = directions[currentDirectionIndex];
                        boolean correct = false;
                        int icon = R.drawable.ic_arrow_up;
                        String text = "";

                        switch (target) {
                            case "center":
                                correct = Math.abs(rotY) < 10 && Math.abs(rotX) < 10;
                                icon = R.drawable.ic_arrow_up;
                                text = "Lütfen düz bakınız";
                                break;
                            case "right":
                                correct = rotY < -30;
                                icon = R.drawable.ic_arrow_right;
                                text = "Lütfen sağa bakınız";
                                break;
                            case "rightUp":
                                correct = rotY < -30 && rotX > 15;
                                icon = R.drawable.ic_arrow_right_up;
                                text = "Lütfen sağ üst bakınız";
                                break;
                            case "rightDown":
                                correct = rotY < -30 && rotX < -15;
                                icon = R.drawable.ic_arrow_right_down;
                                text = "Lütfen sağ alt bakınız";
                                break;
                            case "left":
                                correct = rotY > 30;
                                icon = R.drawable.ic_arrow_left;
                                text = "Lütfen sola bakınız";
                                break;
                            case "leftUp":
                                correct = rotY > 30 && rotX > 15;
                                icon = R.drawable.ic_arrow_left_up;
                                text = "Lütfen sol üst bakınız";
                                break;
                            case "leftDown":
                                correct = rotY > 30 && rotX < -15;
                                icon = R.drawable.ic_arrow_left_down;
                                text = "Lütfen sol alt bakınız";
                                break;
                            case "up":
                                correct = rotX > 15;
                                icon = R.drawable.ic_arrow_up;
                                text = "Lütfen yukarı bakınız";
                                break;
                            case "down":
                                correct = rotX < -15;
                                icon = R.drawable.ic_arrow_down;
                                text = "Lütfen aşağı bakınız";
                                break;
                        }

                        if (correct) {
                            directionStates[currentDirectionIndex] = true;
                            currentDirectionIndex++;
                            circleProgress.setProgressAnimated((int) ((currentDirectionIndex / 9f) * 100));
                        }

                        if (currentDirectionIndex >= 9) {
                            faceCompleted = true;
                            directionText.setText("Yüz başarıyla algılandı ✅");
                            directionArrow.setVisibility(View.GONE);
                            circleProgress.setVisibility(View.GONE);
                            faceGuide.setVisibility(View.GONE);

                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("✅ Başarılı")
                                    .setMessage("Yüz başarıyla algılandı.")
                                    .setCancelable(false)
                                    .setPositiveButton("Tamam", (dialog, which) -> dialog.dismiss())
                                    .show();
                        } else {
                            directionArrow.setVisibility(View.VISIBLE);
                            directionArrow.setImageResource(icon);
                            directionText.setText(text);
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

    private Rect scaleBoundingBox(Rect box, int imageW, int imageH, int viewW, int viewH) {
        float scaleX = (float) viewW / imageW;
        float scaleY = (float) viewH / imageH;

        int left = (int) (box.left * scaleX);
        int top = (int) (box.top * scaleY);
        int right = (int) (box.right * scaleX);
        int bottom = (int) (box.bottom * scaleY);

        return new Rect(left, top, right, bottom);
    }

    private boolean isFaceInsideCircle(Rect faceBox) {
        int[] location = new int[2];
        circleProgress.getLocationOnScreen(location);
        int cx = location[0] + circleProgress.getWidth() / 2;
        int cy = location[1] + circleProgress.getHeight() / 2;
        int radius = circleProgress.getWidth() / 2;

        int fx = faceBox.centerX();
        int fy = faceBox.centerY();

        double distance = Math.sqrt(Math.pow(fx - cx, 2) + Math.pow(fy - cy, 2));
        return distance < radius * 0.8;
    }

    private void startGlowAnimation() {
        int startColor = 0xFFCCCCCC;
        int endColor = 0xFF00FFFF;

        ValueAnimator glowAnimator = ValueAnimator.ofObject(new ArgbEvaluator(), startColor, endColor, startColor);
        glowAnimator.setDuration(2000);
        glowAnimator.setRepeatCount(ValueAnimator.INFINITE);
        glowAnimator.setRepeatMode(ValueAnimator.REVERSE);
        glowAnimator.addUpdateListener(animation -> {
            int color = (int) animation.getAnimatedValue();
            circleProgress.setBorderColor(color);
        });
        glowAnimator.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}
