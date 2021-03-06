package com.example.corac.gnp_labels;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.esri.arcgisruntime.concurrent.Job;
import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.data.Feature;
import com.esri.arcgisruntime.data.FeatureQueryResult;
import com.esri.arcgisruntime.data.Geodatabase;
import com.esri.arcgisruntime.data.QueryParameters;
import com.esri.arcgisruntime.data.ServiceFeatureTable;
import com.esri.arcgisruntime.geometry.Envelope;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.layers.FeatureLayer;
import com.esri.arcgisruntime.loadable.LoadStatus;
import com.esri.arcgisruntime.mapping.ArcGISMap;
import com.esri.arcgisruntime.mapping.Basemap;
import com.esri.arcgisruntime.mapping.popup.PopupAttachment;
import com.esri.arcgisruntime.mapping.view.Callout;
import com.esri.arcgisruntime.mapping.view.Camera;
import com.esri.arcgisruntime.mapping.view.DefaultMapViewOnTouchListener;
import com.esri.arcgisruntime.mapping.view.Graphic;
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay;
import com.esri.arcgisruntime.mapping.view.LocationDisplay;
import com.esri.arcgisruntime.mapping.view.MapView;
import com.esri.arcgisruntime.symbology.SimpleLineSymbol;
import com.esri.arcgisruntime.tasks.geodatabase.GeodatabaseSyncTask;
import com.esri.arcgisruntime.tasks.offlinemap.GenerateOfflineMapJob;
import com.esri.arcgisruntime.tasks.offlinemap.GenerateOfflineMapParameters;
import com.esri.arcgisruntime.tasks.offlinemap.GenerateOfflineMapResult;
import com.esri.arcgisruntime.tasks.offlinemap.OfflineMapTask;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

// todos:
// 1. fix offline thing
// 2. nav thing
// 3. add map key
// 4. clean up map s.t. attrs can be turned on an off

public class MainActivity extends AppCompatActivity {

    // existing map references
    private MapView aMapView;
    private ArcGISMap map;

    // spinner references
    private LocationDisplay mLocationDisplay;
    private Spinner mSpinner;

    // offline references
    private static final String TAG = MainActivity.class.getSimpleName();
    private Button mTakeMapOfflineButton;
    private GraphicsOverlay mGraphicsOverlay;
    private Graphic mDownloadArea;

    // request codes from Android
    private int requestCode = 2;
    String[] reqPermissions = new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission
            .ACCESS_COARSE_LOCATION};

    // popups
    private Callout aCallout;
    private ServiceFeatureTable table0, table1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // get the Spinner from layout
        mSpinner = (Spinner) findViewById(R.id.spinner);

        // inflate MapView from layout
        aMapView = (MapView) findViewById(R.id.mapView);

        // access button to take the map offline and disable it until map is loaded
        mTakeMapOfflineButton = findViewById(R.id.action_download);
        mTakeMapOfflineButton.setEnabled(false);

        map = new ArcGISMap(Basemap.Type.LIGHT_GRAY_CANVAS_VECTOR, 48.6596, -113.7870, 9);
        aMapView.setMap(map);

        // create a graphics overlay for the map view
        mGraphicsOverlay = new GraphicsOverlay();
        aMapView.getGraphicsOverlays().add(mGraphicsOverlay);

        // create a graphic to show a box around the extent we want to download
        mDownloadArea = new Graphic();
        mGraphicsOverlay.getGraphics().add(mDownloadArea);
        SimpleLineSymbol simpleLineSymbol = new SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.RED, 2);
        mDownloadArea.setSymbol(simpleLineSymbol);

        // update the download area box whenever the viewpoint changes
        aMapView.addViewpointChangedListener(viewpointChangedEvent -> {
            if (map.getLoadStatus() == LoadStatus.LOADED) {
                // upper left corner of the area to take offline
                android.graphics.Point minScreenPoint = new android.graphics.Point(200, 200);
                // lower right corner of the downloaded area
                android.graphics.Point maxScreenPoint = new android.graphics.Point(aMapView.getWidth() - 200,
                        aMapView.getHeight() - 200);
                // convert screen points to map points
                Point minPoint = aMapView.screenToLocation(minScreenPoint);
                Point maxPoint = aMapView.screenToLocation(maxScreenPoint);
                // use the points to define and return an envelope
                if (minPoint != null && maxPoint != null) {
                    Envelope envelope = new Envelope(minPoint, maxPoint);
                    mDownloadArea.setGeometry(envelope);
                    // enable the take map offline button only after the map is loaded
                    mTakeMapOfflineButton.setEnabled(true);
                }
            }
        });

        // create a progress dialog to show download progress
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("Generate offline map job");
        progressDialog.setMessage("Taking map offline...");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setIndeterminate(false);
        progressDialog.setProgress(0);

        mTakeMapOfflineButton.setOnClickListener(v -> {
            progressDialog.show();

            // delete any offline map already in the cache
            String tempDirectoryPath = getExternalCacheDir() + File.separator + "offlineMap";
            deleteDirectory(new File(tempDirectoryPath));

            // specify the extent, min scale, and max scale as parameters
            double minScale = aMapView.getMapScale();
            double maxScale = aMapView.getMap().getMaxScale();
            // minScale must always be larger than maxScale
            if (minScale <= maxScale) {
                minScale = maxScale + 1;
            }
            GenerateOfflineMapParameters generateOfflineMapParameters = new GenerateOfflineMapParameters(
                    mDownloadArea.getGeometry(), minScale, maxScale);
            // set job to cancel on any errors
            generateOfflineMapParameters.setContinueOnErrors(true);

            // create an offline map offlineMapTask with the map
            OfflineMapTask offlineMapTask = new OfflineMapTask(aMapView.getMap());

            // create an offline map job with the download directory path and parameters and start the job
            GenerateOfflineMapJob job = offlineMapTask.generateOfflineMap(generateOfflineMapParameters, tempDirectoryPath);

            // replace the current map with the result offline map when the job finishes
            job.addJobDoneListener(() -> {
                if (job.getStatus() == Job.Status.SUCCEEDED) {
                    GenerateOfflineMapResult result = job.getResult();
                    aMapView.setMap(result.getOfflineMap());
                    mGraphicsOverlay.getGraphics().clear();
                    mTakeMapOfflineButton.setEnabled(false);
                    Toast.makeText(this, "Now displaying offline map.", Toast.LENGTH_LONG).show();
                } else {
                    String error = "Error in generate offline map job: " + job.getError().getAdditionalMessage();
                    Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                    Log.e(TAG, error);
                }
                progressDialog.dismiss();
            });

            // show the job's progress with the progress dialog
            job.addProgressChangedListener(() -> progressDialog.setProgress(job.getProgress()));

            // start the job
            job.start();
        });

        // get the MapView's LocationDisplay
        mLocationDisplay = aMapView.getLocationDisplay();

        // listen to changes in the status of the location data source.
        mLocationDisplay.addDataSourceStatusChangedListener(new LocationDisplay.DataSourceStatusChangedListener() {
            @Override
            public void onStatusChanged(LocationDisplay.DataSourceStatusChangedEvent dataSourceStatusChangedEvent) {

                // if LocationDisplay started OK, then continue.
                if (dataSourceStatusChangedEvent.isStarted())
                    return;

                // no error is reported, then continue.
                if (dataSourceStatusChangedEvent.getError() == null)
                    return;

                // if an error is found, handle the failure to start
                // check permissions to see if failure may be due to lack of permissions
                boolean permissionCheck1 = ContextCompat.checkSelfPermission(MainActivity.this, reqPermissions[0]) ==
                        PackageManager.PERMISSION_GRANTED;
                boolean permissionCheck2 = ContextCompat.checkSelfPermission(MainActivity.this, reqPermissions[1]) ==
                        PackageManager.PERMISSION_GRANTED;

                if (!(permissionCheck1 && permissionCheck2)) {
                    // if permissions are not already granted, request permission from the user
                    ActivityCompat.requestPermissions(MainActivity.this, reqPermissions, requestCode);
                } else {
                    // report other unknown failure types to the user - for example, location services may not
                    // be enabled on the device
                    String message = String.format("Error in DataSourceStatusChangedListener: %s", dataSourceStatusChangedEvent
                            .getSource().getLocationDataSource().getError().getMessage());
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();

                    // update UI to reflect that the location display did not actually start
                    mSpinner.setSelection(0, true);
                }
            }
        });

        // populate the list for the Location display options for the spinner's Adapter
        ArrayList<ItemData> list = new ArrayList<>();
        list.add(new ItemData("Stop", R.drawable.locationdisplaydisabled));
        list.add(new ItemData("On", R.drawable.locationdisplayon));
        list.add(new ItemData("Re-Center", R.drawable.locationdisplayrecenter));
        list.add(new ItemData("Navigation", R.drawable.locationdisplaynavigation));
        list.add(new ItemData("Compass", R.drawable.locationdisplayheading));

        SpinnerAdapter adapter = new com.example.corac.gnp_labels.SpinnerAdapter(this, R.layout.activity_spinner, R.id.txt, list);
        mSpinner.setAdapter(adapter);
        mSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

                switch (position) {
                    case 0:
                        // Stop Location Display
                        if (mLocationDisplay.isStarted())
                            mLocationDisplay.stop();
                        break;
                    case 1:
                        // Start Location Display
                        if (!mLocationDisplay.isStarted())
                            mLocationDisplay.startAsync();
                        break;
                    case 2:
                        // AutoPanMode - Default: In this mode, the MapView attempts to keep the location symbol on-screen by
                        // re-centering the location symbol when the symbol moves outside a "wander extent" and while the location symbol
                        // may move freely within the wander extent, as soon as the symbol exits the wander extent, the MapView
                        // re-centers the map on the symbol
                        mLocationDisplay.setAutoPanMode(LocationDisplay.AutoPanMode.RECENTER);
                        if (!mLocationDisplay.isStarted())
                            mLocationDisplay.startAsync();
                        break;
                    case 3:
                        // Start Navigation Mode
                        // This mode is best suited for in-vehicle navigation
                        mLocationDisplay.setAutoPanMode(LocationDisplay.AutoPanMode.NAVIGATION);
                        if (!mLocationDisplay.isStarted())
                            mLocationDisplay.startAsync();
                        break;
                    case 4:
                        // Start Compass Mode
                        // This mode is better suited for waypoint navigation when the user is walking
                        mLocationDisplay.setAutoPanMode(LocationDisplay.AutoPanMode.COMPASS_NAVIGATION);
                        if (!mLocationDisplay.isStarted())
                            mLocationDisplay.startAsync();
                        break;
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) { }

        });

        // get the callout that shows attributes
        aCallout = aMapView.getCallout();
        // layer 0, facilities
        table0 = new ServiceFeatureTable(getResources().getString(R.string.layer0_url));
        final FeatureLayer featureLayer0 = new FeatureLayer(table0);
        map.getOperationalLayers().add(featureLayer0);

        // get the callout that shows attributes
        aCallout = aMapView.getCallout();
        // layer 1, trails
        table1 = new ServiceFeatureTable(getResources().getString(R.string.layer1_url));
        final FeatureLayer featureLayer1 = new FeatureLayer(table1);
        map.getOperationalLayers().add(featureLayer1);

        // set an on touch listener to listen for click events
        aMapView.setOnTouchListener(new DefaultMapViewOnTouchListener(this, aMapView) {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                // get the point that was clicked and convert it to a point in map coordinates
                final Point clickPoint = aMapView.screenToLocation(new android.graphics.Point(Math.round(e.getX()), Math.round(e.getY())));
                // create a selection tolerance
                int tolerance = 10;
                double mapTolerance = tolerance * aMapView.getUnitsPerDensityIndependentPixel();
                // use tolerance to create an envelope to query
                Envelope envelope = new Envelope(clickPoint.getX() - mapTolerance, clickPoint.getY() - mapTolerance, clickPoint.getX() + mapTolerance, clickPoint.getY() + mapTolerance, map.getSpatialReference());
                QueryParameters query = new QueryParameters();
                query.setGeometry(envelope);

                // request all available attribute fields
                // popups
                final ListenableFuture<FeatureQueryResult> future_0 = table0.queryFeaturesAsync(query, ServiceFeatureTable.QueryFeatureFields.LOAD_ALL);
                final ListenableFuture<FeatureQueryResult> future_1 = table1.queryFeaturesAsync(query, ServiceFeatureTable.QueryFeatureFields.LOAD_ALL);

                // select features in a feature layer using the query
                // highlights
                final ListenableFuture<FeatureQueryResult> future_00 = featureLayer0.selectFeaturesAsync(query, FeatureLayer.SelectionMode.NEW);
                final ListenableFuture<FeatureQueryResult> future_11 = featureLayer1.selectFeaturesAsync(query, FeatureLayer.SelectionMode.NEW);

                // add done loading listener to fire when the selection returns
                future_00.addDoneListener(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            //call get on the future to get the result
                            FeatureQueryResult result = future_00.get();
                            // create an Iterator
                            Iterator<Feature> iterator = result.iterator();
                            Feature feature;
                            // cycle through selections
                            int counter = 0;
                            while (iterator.hasNext()) {
                                feature = iterator.next();
                                counter++;
                                Log.d(getResources().getString(R.string.app_name),
                                        "Selection #: " + counter + " Table name: " + feature.getFeatureTable().getTableName());
                            }
                            //Toast.makeText(getApplicationContext(), counter + " features selected", Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            Log.e(getResources().getString(R.string.app_name), "Select feature failed: " + e.getMessage());
                        }
                    }
                });

                // done listener for the other layer
                future_11.addDoneListener(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            //call get on the future to get the result
                            FeatureQueryResult result = future_11.get();
                            // create an Iterator
                            Iterator<Feature> iterator = result.iterator();
                            Feature feature;
                            // cycle through selections
                            int counter = 0;
                            while (iterator.hasNext()) {
                                feature = iterator.next();
                                counter++;
                                Log.d(getResources().getString(R.string.app_name),
                                        "Selection #: " + counter + " Table name: " + feature.getFeatureTable().getTableName());
                            }
                            //Toast.makeText(getApplicationContext(), counter + " features selected", Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            Log.e(getResources().getString(R.string.app_name), "Select feature failed: " + e.getMessage());
                        }
                    }
                });

                /*
                //print all available attr fields in logcat
                System.out.println(future_0.toString());
                Log.d("FUTURE0", "see ^");
                // example return: com.esri.arcgisruntime.data.ServiceFeatureTable$4@36c3a76
                System.out.println(future_1.toString());
                Log.d("FUTURE1", "see ^");
                */

                // add done loading listener to fire when the selection returns
                future_0.addDoneListener(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            //call get on the future to get the result
                            FeatureQueryResult result = future_0.get();
                            // create an Iterator
                            Iterator<Feature> iterator = result.iterator();
                            // create a TextView to display field values
                            TextView calloutContent = new TextView(getApplicationContext());
                            calloutContent.setTextColor(Color.BLACK);
                            calloutContent.setSingleLine(false);
                            calloutContent.setVerticalScrollBarEnabled(true);
                            calloutContent.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
                            calloutContent.setMovementMethod(new ScrollingMovementMethod());
                            calloutContent.setLines(5);
                            // cycle through selections
                            int counter = 0;
                            Feature feature;
                            while (iterator.hasNext()) {
                                feature = iterator.next();
                                // create a Map of all available attributes as name value pairs
                                Map<String, Object> attr = feature.getAttributes();
                                Set<String> keys = attr.keySet();
                                keys.remove("OBJECTID");
                                keys.remove("GlobalID");
                                keys.remove("RuleID_1");
                                keys.remove("Shape__Length");
                                for (String key : keys) {
                                    Object value = attr.get(key);
                                    //don't append the null ones or those unnecesary for user to view
                                    if(value.toString() != null | value.toString() != " " | value.toString() != "null") {
                                        // append name value pairs to TextView
                                        calloutContent.append(key + " | " + value + "\n");
                                    }
                                }
                                counter++;
                                // center the mapview on selected feature
                                Envelope envelope = feature.getGeometry().getExtent();
                                aMapView.setViewpointGeometryAsync(envelope, 200);
                                // show CallOut
                                aCallout.setLocation(clickPoint);
                                aCallout.setContent(calloutContent);
                                aCallout.show();
                            }
                        } catch (Exception e) {
                            Log.e(getResources().getString(R.string.app_name), "Select feature failed: " + e.getMessage());
                        }
                    }
                });
                // add done loading listener to fire when the selection returns
                future_1.addDoneListener(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            //call get on the future to get the result
                            FeatureQueryResult result = future_1.get();
                            // create an Iterator
                            Iterator<Feature> iterator = result.iterator();
                            // create a TextView to display field values
                            TextView calloutContent = new TextView(getApplicationContext());
                            calloutContent.setTextColor(Color.BLACK);
                            calloutContent.setSingleLine(false);
                            calloutContent.setVerticalScrollBarEnabled(true);
                            calloutContent.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
                            calloutContent.setMovementMethod(new ScrollingMovementMethod());
                            calloutContent.setLines(5);
                            // cycle through selections
                            int counter = 0;
                            Feature feature;
                            while (iterator.hasNext()) {
                                feature = iterator.next();
                                // create a Map of all available attributes as name value pairs
                                Map<String, Object> attr = feature.getAttributes();
                                Set<String> keys = attr.keySet();
                                // hide
                                keys.remove("OBJECTID");
                                keys.remove("GlobalID");
                                keys.remove("RuleID_1");
                                keys.remove("Shape__Length");
                                for (String key : keys) {
                                    Object value = attr.get(key);
                                    // no nulls
                                    if(value.toString() != null | value.toString() != " " | value.toString() != "null") {
                                        // append name value pairs to TextView
                                        calloutContent.append(key + " | " + value + "\n");
                                    }
                                }
                                counter++;
                                // center the mapview on selected feature
                                Envelope envelope = feature.getGeometry().getExtent();
                                aMapView.setViewpointGeometryAsync(envelope, 200);
                                // show CallOut
                                aCallout.setLocation(clickPoint);
                                aCallout.setContent(calloutContent);
                                aCallout.show();
                            }
                        } catch (Exception e) {
                            Log.e(getResources().getString(R.string.app_name), "Select feature failed: " + e.getMessage());
                        }
                    }
                });
                return super.onSingleTapConfirmed(e);
            }
        });

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        // If request is cancelled, the result arrays are empty
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // Location permission was granted: This would have been triggered in response to failing to start the
            // LocationDisplay, so try starting this again
            mLocationDisplay.startAsync();
        } else {
            // If permission was denied, show toast to inform user what was chosen and if LocationDisplay is started again,
            // request permission UX will be shown again, option should be shown to allow never showing the UX again
            // The alternative would be to disable functionality so request is not shown again
            Toast.makeText(MainActivity.this, getResources().getString(R.string.location_permission_denied), Toast
                    .LENGTH_SHORT).show();

            // Update UI to reflect that the location display did not actually start
            mSpinner.setSelection(0, true);
        }
    }

    /*@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }*/

    /*@Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case :
                //Intent intent = new Intent(MainActivity.this, OfflineActivity.class);
                //startActivity(intent);
                //return true;
            default:
                // If we got here, the user's action was not recognized
                // Invoke the superclass to handle it
                return super.onOptionsItemSelected(item);
        }
    }*/

    @Override
    protected void onPause() {
        super.onPause();
        aMapView.pause();
    }
    @Override
    protected void onResume() {
        super.onResume();
        aMapView.resume();
    }

    /**
     * Recursively deletes all files in the given directory.
     *
     * @param file to delete
     */
    private static void deleteDirectory(File file) {
        if (file.isDirectory())
            for (File subFile : file.listFiles()) {
                deleteDirectory(subFile);
            }
        if (!file.delete()) {
            Log.e(TAG, "Failed to delete file: " + file.getPath());
        }
    }
}

