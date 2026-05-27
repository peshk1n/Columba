package com.github.peshk1n.columba.ui.test;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.*;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.github.peshk1n.columba.R;
import com.github.peshk1n.columba.core.TestRunner;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class TestTransferActivity extends AppCompatActivity {

    private static final int PICK_FILE = 1001;
    private static final int SAVE_FILE = 1002;

    private Uri selectedFileUri;

    private TextView fileNameView;
    private ProgressBar progressBar;
    private TextView metricsView;
    private Spinner channelSpinner;
    private EditText delayInput;

    private File receivedFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_test_transfer);

        fileNameView = findViewById(R.id.fileName);
        progressBar = findViewById(R.id.progressBar);
        metricsView = findViewById(R.id.metrics);
        channelSpinner = findViewById(R.id.channelType);
        delayInput = findViewById(R.id.delayInput);

        Button pickBtn = findViewById(R.id.pickFileBtn);
        Button startBtn = findViewById(R.id.startBtn);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{
                        "Clean",
                        "Loss",
                        "Loss + Corruption"
                }
        );

        channelSpinner.setAdapter(adapter);

        pickBtn.setOnClickListener(v -> pickFile());
        startBtn.setOnClickListener(v -> startTransfer());
    }

    private void pickFile() {

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);

        intent.setType("*/*");

        startActivityForResult(intent, PICK_FILE);
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            @Nullable Intent data
    ) {

        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FILE &&
                resultCode == Activity.RESULT_OK &&
                data != null) {

            selectedFileUri = data.getData();

            fileNameView.setText(
                    getFileName(selectedFileUri)
            );
        }
        if (requestCode == SAVE_FILE &&
                resultCode == Activity.RESULT_OK &&
                data != null) {

            try {

                Uri uri = data.getData();

                if (uri == null || receivedFile == null)
                    return;

                OutputStream os =
                        getContentResolver().openOutputStream(uri);

                FileInputStream fis =
                        new FileInputStream(receivedFile);

                byte[] buffer = new byte[4096];

                int n;

                while ((n = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, n);
                }

                os.flush();

                os.close();
                fis.close();

                Toast.makeText(
                        this,
                        "File saved",
                        Toast.LENGTH_SHORT
                ).show();

            } catch (Exception e) {
                e.printStackTrace();

                Toast.makeText(
                        this,
                        "Save failed",
                        Toast.LENGTH_SHORT
                ).show();
            }
        }
    }

    private String getFileName(Uri uri) {

        Cursor cursor =
                getContentResolver().query(
                        uri,
                        null,
                        null,
                        null,
                        null
                );

        if (cursor != null) {

            int nameIndex =
                    cursor.getColumnIndex(
                            OpenableColumns.DISPLAY_NAME
                    );

            cursor.moveToFirst();

            String name =
                    cursor.getString(nameIndex);

            cursor.close();

            return name;
        }

        return "unknown";
    }

    private void startTransfer() {

        if (selectedFileUri == null)
            return;

        new Thread(() -> {

            try {

                File inputFile =
                        copyToInternalStorage(
                                selectedFileUri
                        );

                TestRunner runner =
                        new TestRunner();

                float loss = 0f;
                float corruption = 0f;

                String mode =
                        (String) channelSpinner.getSelectedItem();

                if (mode.equals("Loss")) {

                    loss = 0.02f;

                } else if (mode.equals("Loss + Corruption")) {

                    loss = 0.02f;
                    corruption = 0.005f;
                }

                int delay = parseDelay();

                runner.startSimulation(
                        inputFile.getAbsolutePath(),
                        getFilesDir().getAbsolutePath(),
                        loss,
                        corruption,
                        delay,

                        new com.github.peshk1n.columba.core.SimulationCallback() {

                            @Override
                            public void onUpdate(
                                    float progress,
                                    int sent,
                                    int lost,
                                    int corrupted
                            ) {

                                runOnUiThread(() -> {

                                    progressBar.setMax(100);

                                    progressBar.setProgress(
                                            (int) (progress * 100)
                                    );

                                    metricsView.setText(
                                            "Progress: " +
                                                    (int) (progress * 100) +
                                                    "%\n" +

                                                    "Sent: " + sent +

                                                    "\nLost: " + lost +

                                                    "\nCorrupted: " + corrupted
                                    );
                                });
                            }

                            @Override
                            public void onComplete() {

                                runOnUiThread(() -> {

                                    Toast.makeText(
                                            TestTransferActivity.this,
                                            "Transfer complete",
                                            Toast.LENGTH_SHORT
                                    ).show();

                                    receivedFile = new File(
                                            getFilesDir(),
                                            "receiver/input_file"
                                    );

                                    if (!receivedFile.exists()) {

                                        Toast.makeText(
                                                TestTransferActivity.this,
                                                "Received file not found",
                                                Toast.LENGTH_LONG
                                        ).show();

                                        return;
                                    }

                                    saveReceivedFile();
                                });
                            }
                        }
                );

            } catch (Exception e) {

                e.printStackTrace();

                runOnUiThread(() ->
                        Toast.makeText(
                                TestTransferActivity.this,
                                "Transfer failed",
                                Toast.LENGTH_SHORT
                        ).show()
                );
            }
        }).start();
    }

    private void saveReceivedFile() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("*/*");

        String originalName = getFileName(selectedFileUri);
        String newName;

        if (originalName != null && originalName.contains(".")) {
            int dotIndex = originalName.lastIndexOf(".");
            String nameWithoutExtension = originalName.substring(0, dotIndex);
            String extension = originalName.substring(dotIndex);
            newName = nameWithoutExtension + " received" + extension;
        } else {

            newName = originalName + " received";
        }
        intent.putExtra(Intent.EXTRA_TITLE, newName);

        startActivityForResult(intent, SAVE_FILE);
    }

    private int parseDelay() {

        try {

            return Integer.parseInt(
                    delayInput.getText().toString()
            );

        } catch (Exception e) {

            return 0;
        }
    }

    private File copyToInternalStorage(Uri uri)
            throws Exception {

        InputStream is =
                getContentResolver()
                        .openInputStream(uri);

        File file =
                new File(
                        getFilesDir(),
                        "input_file"
                );

        FileOutputStream fos =
                new FileOutputStream(file);

        byte[] buffer = new byte[4096];

        int n;

        while ((n = is.read(buffer)) != -1) {

            fos.write(buffer, 0, n);
        }

        fos.flush();

        fos.close();
        is.close();

        return file;
    }
}