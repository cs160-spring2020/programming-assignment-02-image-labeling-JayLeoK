package com.example.imagelabeler;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.objects.FirebaseVisionObject;
import com.google.firebase.ml.vision.objects.FirebaseVisionObjectDetector;
import com.google.firebase.ml.vision.objects.FirebaseVisionObjectDetectorOptions;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    static final int REQUEST_IMAGE_CAPTURE = 1;
    static final int REQUEST_TAKE_PHOTO = 1;
    // Storage Permissions
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
    private ImageView imageView;
    private Spinner colorSpinner;
    private File imageFile;
    private String fileName;
    private Uri imageUri;
    //firebase
    private FirebaseVisionObjectDetector objectDetector;
    private List<FirebaseVisionObject> detected;

    /**
     * Checks if the app has permission to write to device storage
     * <p>
     * If the app does not has permission then the user will be prompted to grant permissions
     *
     * @param activity
     */
    public static void verifyStoragePermissions(Activity activity) {
        // Check if we have write permission
        int write = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (write != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        colorSpinner = (Spinner) findViewById(R.id.colors);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.color_options, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        colorSpinner.setAdapter(adapter);

        FirebaseVisionObjectDetectorOptions options =
                new FirebaseVisionObjectDetectorOptions.Builder()
                        .setDetectorMode(FirebaseVisionObjectDetectorOptions.SINGLE_IMAGE_MODE)
                        .enableMultipleObjects()
                        .enableClassification()  // Optional
                        .build();
        objectDetector = FirebaseVision.getInstance().getOnDeviceObjectDetector(options);


        if (ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA}, 100);
        }
        verifyStoragePermissions(MainActivity.this);

        imageView = (ImageView) findViewById(R.id.imageView);
        Button photoButton = (Button) findViewById(R.id.photoButton);
        photoButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                dispatchTakePictureIntent();
            }
        });
    }

    private void takePicture() {
        Intent captureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (captureIntent.resolveActivity(getPackageManager()) != null) {
            Date date = new Date();
            DateFormat df = new SimpleDateFormat("-mm-ss");

            String newPicFile = df.format(date) + ".jpg";
            String outPath = "/sdcard/" + newPicFile;
            imageFile = new File(outPath);

            fileName = imageFile.toString();
            Uri outuri = FileProvider.getUriForFile(MainActivity.this,
                    "com.example.imagelabeler.provider", imageFile);
            captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, outuri);
            startActivityForResult(captureIntent, 2);
        }
    }

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        fileName = image.getAbsolutePath();
        return image;
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the File where the photo should go
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                // Error occurred while creating the File
                Log.e("error", ex.getMessage());
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {

                Uri photoURI = FileProvider.getUriForFile(MainActivity.this,
                        "com.example.imagelabeler.provider", photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                imageUri = photoURI;
                startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && resultCode == RESULT_OK) {
            Bundle extras = data.getExtras();
            try {
                Bitmap bmp = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);

                detectObjects(bmp);
                Bitmap bmp_Copy = bmp.copy(Bitmap.Config.ARGB_8888, true);

                Canvas canvas = new Canvas(bmp_Copy);
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(7);
                paint.setTextSize(50);
                paint.setTypeface(Typeface.create("Arial", Typeface.NORMAL));

                paint.setColor(getSelectedColor());
                if (detected != null) {
                    for (FirebaseVisionObject obj : detected) {
                        String label = getLabel(obj);
                        Rect boundary = obj.getBoundingBox();
                        canvas.drawText(label, boundary.left + 20, boundary.top - 20, paint);
                        canvas.drawRect(boundary, paint);
                    }
                }

                imageView.setImageBitmap(bmp_Copy);

            } catch (NullPointerException n) {
                Toast.makeText(this, "bitmap null", Toast.LENGTH_LONG).show();
            } catch (IOException i) {
                Log.e("error", i.getMessage());
            }
        }
    }

    public int getSelectedColor() {
        String colorSelected = colorSpinner.getSelectedItem().toString();
        if (colorSelected.equals("Red")) {
            return Color.RED;
        } else if (colorSelected.equals("Blue")) {
            return Color.BLUE;
        } else {
            return Color.GREEN;
        }
    }

    public String getLabel(FirebaseVisionObject obj) {
        int category = obj.getClassificationCategory();
        if (category == 1) {
            return "Home Good";
        } else if (category == 2) {
            return "Fashion Good";
        } else if (category == 3) {
            return "Food";
        } else if (category == 4) {
            return "Place";
        } else if (category == 5) {
            return "Plant";
        } else {
            return "Unknown";
        }
    }

    public void detectObjects(Bitmap bitmap) {
        FirebaseVisionImage image = FirebaseVisionImage.fromBitmap(bitmap);
        objectDetector.processImage(image)
                .addOnSuccessListener(
                        new OnSuccessListener<List<FirebaseVisionObject>>() {
                            @Override
                            public void onSuccess(List<FirebaseVisionObject> detectedObjects) {
                                detected = detectedObjects;
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Log.e("Exception", "Exception error:" + e.getMessage());
                            }
                        });

    }

}
