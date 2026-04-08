package com.example.text2speech;

import android.os.Bundle;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.example.text2speech.R;

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