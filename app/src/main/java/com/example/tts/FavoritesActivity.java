package com.example.tts;

import android.os.Bundle;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.example.tts.R;

public class FavoritesActivity extends AppCompatActivity {

    private LinearLayout textTab;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorites);

        textTab = findViewById(R.id.textTab);
        textTab.setOnClickListener(v -> {
            finish();
        });
    }
}