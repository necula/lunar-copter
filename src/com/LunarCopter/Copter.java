package com.LunarCopter;

import android.app.Activity;
import android.os.Bundle;
import android.view.Window;


public class Copter extends Activity {
    
       @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(new CopterView(this));
    }
}