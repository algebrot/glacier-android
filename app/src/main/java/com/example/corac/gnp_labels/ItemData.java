package com.example.corac.gnp_labels;

// Based off of 'Display device location' sample code
// https://github.com/Esri/arcgis-runtime-samples-android/tree/master/java/display-device-location


public class ItemData {

    private final String text;
    private final Integer imageId;

    public ItemData(String text, Integer imageId) {
        this.text = text;
        this.imageId = imageId;
    }

    public String getText() {
        return text;
    }

    public Integer getImageId() {
        return imageId;
    }
}
