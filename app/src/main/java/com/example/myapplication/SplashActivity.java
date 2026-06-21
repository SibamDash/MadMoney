package com.example.myapplication;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            TextView tvVersion = findViewById(R.id.splashVersion);
            if (tvVersion != null) {
                tvVersion.setText("v" + versionName);
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            startActivity(new Intent(SplashActivity.this, MainActivity.class));
            finish();
        }, 1500);
    }
}
