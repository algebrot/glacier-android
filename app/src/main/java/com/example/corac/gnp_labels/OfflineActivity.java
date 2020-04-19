package com.example.corac.gnp_labels;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.esri.arcgisruntime.concurrent.Job;
import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.data.Geodatabase;
import com.esri.arcgisruntime.data.GeodatabaseFeatureTable;
import com.esri.arcgisruntime.data.ServiceFeatureTable;
import com.esri.arcgisruntime.geometry.Envelope;
import com.esri.arcgisruntime.layers.FeatureLayer;
import com.esri.arcgisruntime.loadable.LoadStatus;
import com.esri.arcgisruntime.mapping.ArcGISMap;
import com.esri.arcgisruntime.mapping.Basemap;
import com.esri.arcgisruntime.mapping.view.Graphic;
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay;
import com.esri.arcgisruntime.mapping.view.MapView;
import com.esri.arcgisruntime.symbology.SimpleLineSymbol;
import com.esri.arcgisruntime.tasks.geodatabase.GenerateGeodatabaseJob;
import com.esri.arcgisruntime.tasks.geodatabase.GenerateGeodatabaseParameters;
import com.esri.arcgisruntime.tasks.geodatabase.GeodatabaseSyncTask;

import java.io.File;
import java.util.concurrent.ExecutionException;

// Based off of 'Create an offline map' guide
// https://developers.arcgis.com/android/latest/guide/create-an-offline-map.htm#ESRI_SECTION3_4C70B6AE44A0468D9DFF99719233447C

public class OfflineActivity extends AppCompatActivity {

    private final String TAG = OfflineActivity.class.getSimpleName();

    private MapView mMapView;
    private ServiceFeatureTable table0, table1;

    private TextView mProgressTextView;
    private RelativeLayout mProgressLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_offline);

        // define permission to request
        String[] reqPermission = new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE };
        int requestCode = 2;
        // For API level 23+ request permission at runtime
        if (ContextCompat.checkSelfPermission(OfflineActivity.this, reqPermission[0]) != PackageManager.PERMISSION_GRANTED) {
            // request permission
            ActivityCompat.requestPermissions(OfflineActivity.this, reqPermission, requestCode);
        }

        // create a map view and add a map
        mMapView = (MapView) findViewById(R.id.mapView);
        final ArcGISMap map = new ArcGISMap(Basemap.Type.LIGHT_GRAY_CANVAS_VECTOR, 48.6596, -113.7870, 9);
        mMapView.setMap(map);

        // layer 0, facilities
        table0 = new ServiceFeatureTable(getResources().getString(R.string.layer0_url));
        final FeatureLayer featureLayer0 = new FeatureLayer(table0);
        map.getOperationalLayers().add(featureLayer0);

        // layer 1, trails
        table1 = new ServiceFeatureTable(getResources().getString(R.string.layer1_url));
        final FeatureLayer featureLayer1 = new FeatureLayer(table1);
        map.getOperationalLayers().add(featureLayer1);

        // create a graphics overlay and symbol to mark the extent
        final GraphicsOverlay graphicsOverlay = new GraphicsOverlay();
        mMapView.getGraphicsOverlays().add(graphicsOverlay);
        final SimpleLineSymbol boundarySymbol = new SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.RED, 5);

        // inflate button and progress layout
        final Button genGeodatabaseButton = (Button) findViewById(R.id.genGeodatabaseButton);
        mProgressLayout = (RelativeLayout) findViewById(R.id.progressLayout);
        final ProgressBar progressBar = (ProgressBar) findViewById(R.id.taskProgressBar);
        mProgressTextView = (TextView) findViewById(R.id.progressTextView);

        // create a geodatabase sync task
        final GeodatabaseSyncTask geodatabaseSyncTask = new GeodatabaseSyncTask(getString(R.string.glacier_sync));
        geodatabaseSyncTask.loadAsync();
        geodatabaseSyncTask.addDoneLoadingListener(() -> {

            // generate the geodatabase sync task
            genGeodatabaseButton.setOnClickListener(v -> {

                // show the progress layout
                progressBar.setProgress(0);
                mProgressLayout.setVisibility(View.VISIBLE);

                // clear any previous operational layers and graphics if button clicked more than once
                map.getOperationalLayers().clear();
                graphicsOverlay.getGraphics().clear();

                // show the extent used as a graphic
                Envelope extent = mMapView.getVisibleArea().getExtent();
                Graphic boundary = new Graphic(extent, boundarySymbol);
                graphicsOverlay.getGraphics().add(boundary);

                // create generate geodatabase parameters for the current extent
                final ListenableFuture<GenerateGeodatabaseParameters> defaultParameters = geodatabaseSyncTask
                        .createDefaultGenerateGeodatabaseParametersAsync(extent);
                Log.i(TAG, "Parameters created: " + defaultParameters);

                defaultParameters.addDoneListener(new Runnable() {
                    @Override public void run() {
                        try {
                            // set parameters and don't include attachments
                            GenerateGeodatabaseParameters parameters = defaultParameters.get();
                            parameters.setReturnAttachments(false);

                            // define the local path where the geodatabase will be stored
                            final String localGeodatabasePath =
                                    getCacheDir().toString() + File.separator + getString(R.string.file_name);

                            // create and start the job
                            final GenerateGeodatabaseJob generateGeodatabaseJob = geodatabaseSyncTask
                                    .generateGeodatabaseAsync(parameters, localGeodatabasePath);
                            generateGeodatabaseJob.start();
                            mProgressTextView.setText(getString(R.string.progress_started));

                            // update progress
                            generateGeodatabaseJob.addProgressChangedListener(new Runnable() {
                                @Override public void run() {
                                    progressBar.setProgress(generateGeodatabaseJob.getProgress());
                                    mProgressTextView.setText(getString(R.string.progress_fetching));
                                }
                            });

                            // get geodatabase when done
                            generateGeodatabaseJob.addJobDoneListener(new Runnable() {
                                @Override public void run() {
                                    mProgressLayout.setVisibility(View.INVISIBLE);
                                    if (generateGeodatabaseJob.getStatus() == Job.Status.SUCCEEDED) {
                                        final Geodatabase geodatabase = generateGeodatabaseJob.getResult();
                                        geodatabase.loadAsync();
                                        geodatabase.addDoneLoadingListener(new Runnable() {
                                            @Override public void run() {
                                                if (geodatabase.getLoadStatus() == LoadStatus.LOADED) {
                                                    mProgressTextView.setText(getString(R.string.progress_done));
                                                    for (GeodatabaseFeatureTable geodatabaseFeatureTable : geodatabase
                                                            .getGeodatabaseFeatureTables()) {
                                                        geodatabaseFeatureTable.loadAsync();
                                                        map.getOperationalLayers().add(new FeatureLayer(geodatabaseFeatureTable));
                                                    }
                                                    genGeodatabaseButton.setVisibility(View.GONE);
                                                    Log.i(TAG, "Local geodatabase stored at: " + localGeodatabasePath);
                                                } else {
                                                    Log.e(TAG, "Error loading geodatabase: " + geodatabase.getLoadError().getMessage());
                                                }
                                            }
                                        });
                                        // unregister since we're not syncing
                                        ListenableFuture unregisterGeodatabase = geodatabaseSyncTask
                                                .unregisterGeodatabaseAsync(geodatabase);
                                        unregisterGeodatabase.addDoneListener(new Runnable() {
                                            @Override public void run() {
                                                Log.i(TAG, "Geodatabase unregistered since we wont be editing it in this sample.");
                                                Toast.makeText(OfflineActivity.this,
                                                        "Geodatabase unregistered since we wont be editing it in this sample.",
                                                        Toast.LENGTH_LONG).show();
                                            }
                                        });
                                    } else if (generateGeodatabaseJob.getError() != null) {
                                        Log.e(TAG, "Error generating geodatabase: " + generateGeodatabaseJob.getError().getMessage());
                                    } else {
                                        Log.e(TAG, "Unknown Error generating geodatabase");
                                    }
                                }
                            });
                        } catch (InterruptedException | ExecutionException e) {
                            Log.e(TAG, "Error generating geodatabase parameters : " + e.getMessage());
                        }
                    }
                });
            });
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        mMapView.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mMapView.resume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mMapView.dispose();
    }
}
