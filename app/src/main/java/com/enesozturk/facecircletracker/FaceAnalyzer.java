package com.enesozturk.facecircletracker;

import android.graphics.Rect;
import android.media.Image;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

public class FaceAnalyzer implements ImageAnalysis.Analyzer {

    public interface FaceListener {
        void onFaceData(float rotY, float rotX, Rect boundingBox, int imageWidth, int imageHeight);
    }

    private final FaceDetector detector;
    private final FaceListener listener;

    public FaceAnalyzer(FaceListener listener) {
        this.listener = listener;

        FaceDetectorOptions options =
                new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                        .build();

        detector = FaceDetection.getClient(options);
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    @Override
    public void analyze(@NonNull ImageProxy imageProxy) {
        Image mediaImage = imageProxy.getImage();
        if (mediaImage != null) {
            int imageWidth = imageProxy.getWidth();
            int imageHeight = imageProxy.getHeight();

            InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

            detector.process(image)
                    .addOnSuccessListener(faces -> {
                        if (!faces.isEmpty()) {
                            Face face = faces.get(0);
                            float rotY = face.getHeadEulerAngleY();
                            float rotX = face.getHeadEulerAngleX();
                            Rect boundingBox = face.getBoundingBox();
                            listener.onFaceData(rotY, rotX, boundingBox, imageWidth, imageHeight);
                        }
                        imageProxy.close();
                    })
                    .addOnFailureListener(e -> {
                        Log.e("FaceAnalyzer", "Yüz algılama hatası: ", e);
                        imageProxy.close();
                    });
        } else {
            imageProxy.close();
        }
    }
}
