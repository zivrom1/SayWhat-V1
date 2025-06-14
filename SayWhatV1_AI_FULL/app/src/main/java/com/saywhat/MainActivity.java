
package com.saywhat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import org.json.JSONObject;
import org.tensorflow.lite.Interpreter;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Scanner;

public class MainActivity extends AppCompatActivity {

    private MediaRecorder recorder;
    private Interpreter tflite;
    private TextView translationOutput;
    private boolean isRecording = false;
    private final String modelPath = "PetSoundClassifier.tflite";
    private final String labelPath = "labels.json";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        translationOutput = findViewById(R.id.translation_output);
        Button recordBtn = findViewById(R.id.record_btn);

        // Ask for mic permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        }

        try {
            tflite = new Interpreter(loadModelFile(modelPath));
        } catch (Exception e) {
            translationOutput.setText("Error loading model.");
            e.printStackTrace();
        }

        recordBtn.setOnClickListener(v -> {
            if (!isRecording) {
                startRecording();
                recordBtn.setText("Stop");
            } else {
                stopRecording();
                runModelOnAudio();  // Translate after recording
                recordBtn.setText("Record");
            }
        });
    }

    private void startRecording() {
        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        recorder.setOutputFile(getExternalFilesDir(null).getAbsolutePath() + "/sound.3gp");
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        try {
            recorder.prepare();
            recorder.start();
            isRecording = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void stopRecording() {
        if (recorder != null) {
            recorder.stop();
            recorder.release();
            recorder = null;
        }
        isRecording = false;
    }

    private void runModelOnAudio() {
        float[][] inputFeature = new float[1][128]; // dummy input for example
        float[][] output = new float[1][3];

        // Fake inference call
        tflite.run(inputFeature, output);
        int maxIdx = 0;
        float maxVal = output[0][0];
        for (int i = 1; i < output[0].length; i++) {
            if (output[0][i] > maxVal) {
                maxVal = output[0][i];
                maxIdx = i;
            }
        }

        try {
            JSONObject labelMap = new JSONObject(loadJSONFromAsset(labelPath));
            String message = labelMap.optString(String.valueOf(maxIdx), "Unknown sound");
            translationOutput.setText("Translation: " + message);
        } catch (Exception e) {
            translationOutput.setText("Error reading labels.");
        }
    }

    private MappedByteBuffer loadModelFile(String filename) throws IOException {
        FileInputStream inputStream = new FileInputStream(getAssets().openFd(filename).getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = getAssets().openFd(filename).getStartOffset();
        long declaredLength = getAssets().openFd(filename).getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private String loadJSONFromAsset(String filename) throws IOException {
        Scanner scanner = new Scanner(getAssets().open(filename));
        StringBuilder builder = new StringBuilder();
        while (scanner.hasNextLine()) {
            builder.append(scanner.nextLine());
        }
        scanner.close();
        return builder.toString();
    }
}
