package com.xiaqui.androidtemplate

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = "Hello Android"
            textSize = 28f
            gravity = android.view.Gravity.CENTER
        })
    }
}
