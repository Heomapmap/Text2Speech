package com.example.text2speech;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        SwitchCompat switchProximity = findViewById(R.id.sensorSwitch);
        ImageButton btnBack = findViewById(R.id.btnBack);

        // Đọc trạng thái đã lưu
        SharedPreferences sharedPreferences = getSharedPreferences("AppSettings", MODE_PRIVATE);
        boolean isSensorEnabled = sharedPreferences.getBoolean("sensor_enabled", true);
        switchProximity.setChecked(isSensorEnabled);

        // Lưu trạng thái khi thay đổi
        switchProximity.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sharedPreferences.edit().putBoolean("sensor_enabled", isChecked).apply();
        });

        btnBack.setOnClickListener(v -> finish());
    }
}