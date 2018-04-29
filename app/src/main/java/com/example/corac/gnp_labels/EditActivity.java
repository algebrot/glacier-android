package com.example.corac.gnp_labels;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.data.ArcGISFeature;
import com.esri.arcgisruntime.data.FeatureEditResult;
import com.esri.arcgisruntime.data.ServiceFeatureTable;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.geometry.SpatialReferences;
import com.esri.arcgisruntime.layers.FeatureLayer;
import com.esri.arcgisruntime.loadable.LoadStatus;
import com.esri.arcgisruntime.mapping.ArcGISMap;
import com.esri.arcgisruntime.mapping.Basemap;
import com.esri.arcgisruntime.mapping.GeoElement;
import com.esri.arcgisruntime.mapping.Viewpoint;
import com.esri.arcgisruntime.mapping.view.Callout;
import com.esri.arcgisruntime.mapping.view.DefaultMapViewOnTouchListener;
import com.esri.arcgisruntime.mapping.view.IdentifyLayerResult;
import com.esri.arcgisruntime.mapping.view.MapView;
import java.util.List;


public class EditActivity extends AppCompatActivity {

    private static final String TAG = EditActivity.class.getSimpleName();

    private Callout mCallout;
    private FeatureLayer mFeatureLayer;
    private ArcGISFeature mSelectedArcGISFeature;
    private MapView aMapView;
    private android.graphics.Point mClickPoint;
    private ServiceFeatureTable mServiceFeatureTable;
    private Snackbar mSnackbarSuccess;
    private Snackbar mSnackbarFailure;
    private String mSelectedArcGISFeatureAttributeValue;
    private boolean mFeatureUpdated;
    private View mCoordinatorLayout;
    private ProgressDialog mProgressDialog;
    private ArcGISMap map;
    private ServiceFeatureTable table0, table1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit);

       // mCoordinatorLayout = findViewById(R.id.snackbarPosition);

        // inflate MapView from layout
        aMapView = (MapView) findViewById(R.id.mapView);

        map = new ArcGISMap(Basemap.Type.LIGHT_GRAY_CANVAS_VECTOR, 48.6596, -113.7870, 9);
        aMapView.setMap(map);

        // get callout, set content and show
        //mCallout = aMapView.getCallout();


        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setTitle(getResources().getString(R.string.progress_title));
        mProgressDialog.setMessage(getResources().getString(R.string.progress_message));

        // layer 0, facilities
        table0 = new ServiceFeatureTable(getResources().getString(R.string.layer0_url));
        final FeatureLayer featureLayer0 = new FeatureLayer(table0);

        // layer 1, trails
        table1 = new ServiceFeatureTable(getResources().getString(R.string.layer1_url));
        final FeatureLayer featureLayer1 = new FeatureLayer(table1);

        // create feature layer with its service feature table
        // create the service feature table
        //mServiceFeatureTable = new ServiceFeatureTable(getResources().getString(R.string.sample_service_url));
        // create the feature layer using the service feature table
        //mFeatureLayer = new FeatureLayer(mServiceFeatureTable);

        // set the color that is applied to a selected feature.
        featureLayer0.setSelectionColor(Color.CYAN);
        featureLayer1.setSelectionColor(Color.CYAN);
        // set the width of selection color
        featureLayer0.setSelectionWidth(5);
        featureLayer1.setSelectionWidth(5);

        map.getOperationalLayers().add(featureLayer0);
        map.getOperationalLayers().add(featureLayer1);


        // set an on touch listener to listen for click events
        /*aMapView.setOnTouchListener(new DefaultMapViewOnTouchListener(this, aMapView) {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {

                // get the point that was clicked and convert it to a point in map coordinates
                mClickPoint = new android.graphics.Point((int) e.getX(), (int) e.getY());

                // clear any previous selection
                mFeatureLayer.clearSelection();
                mSelectedArcGISFeature = null;
                mCallout.dismiss();

                // identify the GeoElements in the given layer
                final ListenableFuture<IdentifyLayerResult> identifyFuture = mMapView
                        .identifyLayerAsync(mFeatureLayer, mClickPoint, 5, false, 1);

                // add done loading listener to fire when the selection returns
                identifyFuture.addDoneListener(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            // call get on the future to get the result
                            IdentifyLayerResult layerResult = identifyFuture.get();
                            List<GeoElement> resultGeoElements = layerResult.getElements();

                            if (resultGeoElements.size() > 0) {
                                if (resultGeoElements.get(0) instanceof ArcGISFeature) {
                                    mSelectedArcGISFeature = (ArcGISFeature) resultGeoElements.get(0);
                                    // highlight the selected feature
                                    mFeatureLayer.selectFeature(mSelectedArcGISFeature);
                                    // show callout with the value for the attribute "typdamage" of the selected feature
                                    mSelectedArcGISFeatureAttributeValue = (String) mSelectedArcGISFeature.getAttributes()
                                            .get("Comment");
                                    showCallout(mSelectedArcGISFeatureAttributeValue);
                                    Toast.makeText(getApplicationContext(), "Tap on the info button to change attribute value",
                                            Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                // none of the features on the map were selected
                                mCallout.dismiss();
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Select feature failed: " + e.getMessage());
                        }
                    }
                });
                return super.onSingleTapConfirmed(e);
            }
        });*/

       /* mSnackbarSuccess = Snackbar
                .make(mCoordinatorLayout, "Feature successfully updated", Snackbar.LENGTH_LONG)
                .setAction("UNDO", new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        String snackBarText = updateAttributes(mSelectedArcGISFeatureAttributeValue) ?
                                "Feature is restored!" :
                                "Feature restore failed!";
                        Snackbar snackbar1 = Snackbar.make(mCoordinatorLayout, snackBarText, Snackbar.LENGTH_SHORT);
                        snackbar1.show();
                    }
                });

        mSnackbarFailure = Snackbar.make(mCoordinatorLayout, "Feature update failed", Snackbar.LENGTH_LONG);
    }*/

        /**
         * Function to read the result from newly created activity
         */
   /* @Override
    protected void onActivityResult(int requestCode,
                                    int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == 100) {
            // display progress dialog while updating attribute callout
            mProgressDialog.show();
            updateAttributes(data.getStringExtra("typdamage"));
        }
    }*/

        /**
         * Applies changes to the feature, Service Feature Table, and server.
         */
   /* private boolean updateAttributes(final String comment) {

        // load the selected feature
        mSelectedArcGISFeature.loadAsync();

        // update the selected feature
        mSelectedArcGISFeature.addDoneLoadingListener(new Runnable() {
            @Override public void run() {
                if (mSelectedArcGISFeature.getLoadStatus() == LoadStatus.FAILED_TO_LOAD) {
                    Log.e(TAG, "Error while loading feature");
                }

                // update the Attributes map with the new selected value for "typdamage"
                mSelectedArcGISFeature.getAttributes().put("Comment", comment);*/

                /*try {
                    // update feature in the feature table
                    ListenableFuture<Void> mapViewResult = mServiceFeatureTable.updateFeatureAsync(mSelectedArcGISFeature);
                    mServiceFeatureTable.updateFeatureAsync(mSelectedArcGISFeature).addDoneListener(new Runnable() {
                    mapViewResult.addDoneListener(new Runnable() {
                        @Override
                        public void run() {
                            // apply change to the server
                            final ListenableFuture<List<FeatureEditResult>> serverResult = mServiceFeatureTable.applyEditsAsync();

                            serverResult.addDoneListener(new Runnable() {
                                @Override
                                public void run() {
                                    try {

                                        // check if server result successful
                                        List<FeatureEditResult> edits = serverResult.get();
                                        if (edits.size() > 0) {
                                            if (!edits.get(0).hasCompletedWithErrors()) {
                                                Log.e(TAG, "Feature successfully updated");
                                                mSnackbarSuccess.show();
                                                mFeatureUpdated = true;
                                            }
                                        } else {
                                            Log.e(TAG, "The attribute type was not changed");
                                            mSnackbarFailure.show();
                                            mFeatureUpdated = false;
                                        }
                                        if (mProgressDialog.isShowing()) {
                                            mProgressDialog.dismiss();
                                            // display the callout with the updated value
                                            showCallout((String) mSelectedArcGISFeature.getAttributes().get("typdamage"));
                                        }
                                    } catch (Exception e) {
                                        Log.e(TAG, "applying changes to the server failed: " + e.getMessage());
                                    }
                                }
                            });
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "updating feature in the feature table failed: " + e.getMessage());
                }
            }
        });
        return mFeatureUpdated;
    }*/

        /**
         * Displays Callout
         *
         * @param title the text to show in the Callout
         */
   /* private void showCallout(String title) {

        // create a text view for the callout
        RelativeLayout calloutLayout = new RelativeLayout(getApplicationContext());

        TextView calloutContent = new TextView(getApplicationContext());
        //calloutContent.setId(R.id.textview);
        calloutContent.setTextColor(Color.BLACK);
        calloutContent.setTextSize(18);
        calloutContent.setPadding(0, 10, 10, 0);

        calloutContent.setText(title);

        RelativeLayout.LayoutParams relativeParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        relativeParams.addRule(RelativeLayout.RIGHT_OF, calloutContent.getId());

        // create image view for the callout
        ImageView imageView = new ImageView(getApplicationContext());
        imageView
                .setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.locationdisplaynavigation));
        imageView.setLayoutParams(relativeParams);
        imageView.setOnClickListener(new ImageViewOnclickListener());

        calloutLayout.addView(calloutContent);
        calloutLayout.addView(imageView);

        mCallout.setGeoElement(mSelectedArcGISFeature, null);
        mCallout.setContent(calloutLayout);
        mCallout.show();
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

        /**
         * Defines the listener for the ImageView clicks
         */
    /*private class ImageViewOnclickListener implements View.OnClickListener {

        @Override public void onClick(View v) {
            Intent myIntent = new Intent(EditActivity.this, ListActivity.class);
            EditActivity.this.startActivityForResult(myIntent, 100);
        }
    }*/

    }
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
}