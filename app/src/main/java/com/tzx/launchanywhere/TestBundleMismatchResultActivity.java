package com.tzx.launchanywhere;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

import java.util.Set;

/**
 * Created by Tanzhenxing
 * Date: 2023/4/3 19:24
 * Description:
 */
public class TestBundleMismatchResultActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);
        Button button = findViewById(R.id.button);
        Log.d("tanzhenxing33", "onCreate:" + getLocalClassName());
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        Log.d("tanzhenxing33", "Bundle.ClassLoader=" + extras.getClassLoader());
        Bundle extras2 = new Bundle();
        Log.d("tanzhenxing33", "extras2 Bundle.ClassLoader=" + extras2.getClassLoader());
        Set<String> strings = extras.keySet();
        for (String s : strings) {
            Object o = extras.get(s);
            String clsName = o != null ? o.getClass().getName() : "NULL";
            Log.d("tanzhenxing33", "TestBundleMismatchResultActivity key = " + s + " " + clsName);
        }
        Object result = extras.get("intent");
        if (result instanceof Intent) {
            Log.d("tanzhenxing33", "result != null," + result);
            button.setText(result.toString());
        }
    }
}
