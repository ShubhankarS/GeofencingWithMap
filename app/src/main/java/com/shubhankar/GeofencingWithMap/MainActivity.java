package com.shubhankar.GeofencingWithMap;

import android.content.Intent;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.shubhankar.GeofencingWithMap.R;

public class MainActivity extends FragmentActivity {

    Button chooseFences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        chooseFences = (Button) findViewById(R.id.choose_fences);

        chooseFences.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent goToMap = new Intent(MainActivity.this, MapsActivity.class);
                startActivity(goToMap);
            }
        });


    }

}
