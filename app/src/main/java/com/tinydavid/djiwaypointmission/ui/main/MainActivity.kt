package com.tinydavid.djiwaypointmission.ui.main

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.tinydavid.djiwaypointmission.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var mBinding: ActivityMainBinding

    private val mViewModel: MainViewModel by viewModels()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding = ActivityMainBinding.inflate(layoutInflater)
        val view = mBinding.root
        setContentView(view)

    }

    private fun startActivity(context: Context, activity: Class<*>?) {
        // this will start the activity
        val intent = Intent(context, activity)
        context.startActivity(intent)
    }

    companion object {
        const val TAG = "MainActivity"
    }
}