package com.example.corac.gnp_labels;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Environment;
import android.support.annotation.NonNull;
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
import com.esri.arcgisruntime.data.SyncModel;
import com.esri.arcgisruntime.data.TileCache;
import com.esri.arcgisruntime.geometry.Envelope;
import com.esri.arcgisruntime.layers.ArcGISTiledLayer;
import com.esri.arcgisruntime.layers.FeatureLayer;
import com.esri.arcgisruntime.loadable.LoadStatus;
import com.esri.arcgisruntime.mapping.ArcGISMap;
import com.esri.arcgisruntime.mapping.Basemap;
import com.esri.arcgisruntime.mapping.Viewpoint;
import com.esri.arcgisruntime.mapping.popup.PopupAttachment;
import com.esri.arcgisruntime.mapping.view.Graphic;
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay;
import com.esri.arcgisruntime.mapping.view.MapView;
import com.esri.arcgisruntime.symbology.SimpleLineSymbol;
import com.esri.arcgisruntime.tasks.geodatabase.GenerateGeodatabaseJob;
import com.esri.arcgisruntime.tasks.geodatabase.GenerateGeodatabaseParameters;
import com.esri.arcgisruntime.tasks.geodatabase.GenerateLayerOption;
import com.esri.arcgisruntime.tasks.geodatabase.GeodatabaseSyncTask;

import java.io.File;
import java.util.concurrent.ExecutionException;

// based off of Create an offline map guide
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

        /*
        // use local tile package for the base map
        TileCache sanFrancisco = new TileCache(
                Environment.getExternalStorageDirectory() + getString(R.string.san_francisco_tpk));
        ArcGISTiledLayer tiledLayer = new ArcGISTiledLayer(sanFrancisco);*/

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

    /*
    // existing map references
    private static MapView aMapView;
    private ArcGISMap map;
    private ServiceFeatureTable table0, table1;

    // request codes from Android
    private int requestCode = 2;
    String[] reqPermissions = new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission
            .ACCESS_COARSE_LOCATION};

    // offline
    private RelativeLayout mProgressLayout;
    private TextView mProgressTextView;
    private GraphicsOverlay mGraphicsOverlay;

    protected static final String TAG = "CRGdb";
    static String mExportPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_offline);

        // inflate MapView from layout
        aMapView = (MapView) findViewById(R.id.mapView);
        map = new ArcGISMap(Basemap.Type.LIGHT_GRAY_CANVAS_VECTOR, 48.6596, -113.7870, 9);
        aMapView.setMap(map);
        // create a graphics overlay and symbol to mark the extent
        mGraphicsOverlay = new GraphicsOverlay();
        aMapView.getGraphicsOverlays().add(mGraphicsOverlay);

        mProgressLayout = (RelativeLayout) findViewById(R.id.progressLayout);
        final ProgressBar progressBar = (ProgressBar) findViewById(R.id.taskProgressBar);
        mProgressTextView = (TextView) findViewById(R.id.progressTextView);

        // get the callout that shows attributes
        //aCallout = aMapView.getCallout();
        // layer 0, facilities
        table0 = new ServiceFeatureTable(getResources().getString(R.string.layer0_url));
        final FeatureLayer featureLayer0 = new FeatureLayer(table0);
        map.getOperationalLayers().add(featureLayer0);
        // get the callout that shows attributes
        //aCallout = aMapView.getCallout();
        // layer 1, trails
        table1 = new ServiceFeatureTable(getResources().getString(R.string.layer1_url));
        final FeatureLayer featureLayer1 = new FeatureLayer(table1);
        map.getOperationalLayers().add(featureLayer1);

        // create a graphics overlay and symbol to mark the extent
        final GraphicsOverlay graphicsOverlay = new GraphicsOverlay();
        aMapView.getGraphicsOverlays().add(graphicsOverlay);
        final SimpleLineSymbol boundarySymbol = new SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.RED, 5);

        final Button startdownload = (Button) findViewById(R.id.startdownload);
        // create a geodatabase sync task
        final GeodatabaseSyncTask geodatabaseSyncTask = new GeodatabaseSyncTask(getString(R.string.glacier_sync));
        geodatabaseSyncTask.loadAsync();
        geodatabaseSyncTask.addDoneLoadingListener(() -> {

            // generate the geodatabase sync task
            startdownload.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {

                    // show the progress layout
                    progressBar.setProgress(0);
                    mProgressLayout.setVisibility(View.VISIBLE);

                    // clear any previous operational layers and graphics if button clicked more than once
                    map.getOperationalLayers().clear();
                    graphicsOverlay.getGraphics().clear();

                    // show the extent used as a graphic
                    Envelope extent = aMapView.getVisibleArea().getExtent();
                    Graphic boundary = new Graphic(extent, boundarySymbol);
                    graphicsOverlay.getGraphics().add(boundary);

                    // create generate geodatabase parameters for the current extent
                    final ListenableFuture<GenerateGeodatabaseParameters> defaultParameters = geodatabaseSyncTask
                            .createDefaultGenerateGeodatabaseParametersAsync(extent);
                    defaultParameters.addDoneListener(new Runnable() {
                        @Override public void run() {
                            try {
                                // set parameters and don't include attachments
                                GenerateGeodatabaseParameters parameters = defaultParameters.get();


                                // set the sync model to per layer
                                parameters.setSyncModel(SyncModel.PER_LAYER);

                                // define the layers and features to include
                                int facilities = 1;
                                int trails = 3;

                                // Clear and re-create the layer options
                                parameters.getLayerOptions().clear();
                                parameters.getLayerOptions().add(new GenerateLayerOption(facilities));
                                parameters.getLayerOptions().add(new GenerateLayerOption(trails));

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
                                generateGeodatabaseJob.addProgressChangedListener(() -> {
                                    progressBar.setProgress(generateGeodatabaseJob.getProgress());
                                    mProgressTextView.setText(getString(R.string.progress_fetching));
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
                                                        startdownload.setVisibility(View.GONE);
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
                }
            });
        });
    }*/

    /*@Override
    protected void onPause() {
        super.onPause();
        aMapView.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        aMapView.resume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        aMapView.dispose();
    }*/


}
