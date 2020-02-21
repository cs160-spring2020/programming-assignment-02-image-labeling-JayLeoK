package com.example.imagelabeler;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.provider.MediaStore;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class photoActivity extends AppCompatActivity{

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState, @Nullable PersistableBundle persistentState) {
        super.onCreate(savedInstanceState, persistentState);
        setContentView(R.layout.photo_activity);
        Intent intent = getIntent();
        String color = intent.getStringExtra("colorSelected");

        ImageView imageView = (ImageView) findViewById(R.id.image_view);
        if (ContextCompat.checkSelfPermission(photoActivity.this,
                Manifest.permission.CAMERA)!= photoActivity.this.getPackageManager().PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(photoActivity.this,new String[] {Manifest.permission.CAMERA}, 100);
        }
        Intent captureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(captureIntent,100);

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode==100){
            Toast.makeText(this, "Bitmap stuff", Toast.LENGTH_SHORT).show();
        }
    }
}
