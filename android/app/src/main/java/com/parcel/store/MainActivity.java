package com.parcel.store;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(ParcelPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
