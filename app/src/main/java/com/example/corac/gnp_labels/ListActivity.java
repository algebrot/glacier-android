package com.example.corac.gnp_labels;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

// based off of Feature layer update attributes sample code
// https://github.com/Esri/arcgis-runtime-samples-android/tree/master/java/feature-layer-update-attributes


public class ListActivity extends AppCompatActivity {

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            //setContentView(R.layout.comment_types_listview);
            //final String[] damageTypes = getResources().getStringArray(R.array.damage_types);

            //ListView listView = (ListView) findViewById(R.id.listview);

            /*listView.setAdapter(new ArrayAdapter<>(this, R.layout.comment_types, damageTypes));

            listView.setTextFilterEnabled(true);

            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                public void onItemClick(AdapterView<?> parent, View view,
                                        int position, long id) {
                    Intent myIntent = new Intent();
                    myIntent.putExtra("typdamage", damageTypes[position]); //Optional parameters
                    setResult(100, myIntent);
                    finish();
                }
            });*/

        }

        @Override
        public void onBackPressed() {
        }
    }
