package com.tinydavid.djiwaypointmission.ui.waypoint

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.annotations.IconFactory
import com.mapbox.mapboxsdk.annotations.Marker
import com.mapbox.mapboxsdk.annotations.MarkerOptions
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.maps.SupportMapFragment
import com.tinydavid.djiwaypointmission.DJIApplication
import com.tinydavid.djiwaypointmission.R
import com.tinydavid.djiwaypointmission.databinding.ActivityWaypointOneBinding
import dji.common.error.DJIError
import dji.common.mission.waypoint.*
import dji.sdk.mission.waypoint.WaypointMissionOperator
import dji.sdk.mission.waypoint.WaypointMissionOperatorListener
import dji.sdk.sdkmanager.DJISDKManager
import java.util.concurrent.ConcurrentHashMap

class WaypointOneActivity : AppCompatActivity(), MapboxMap.OnMapClickListener, OnMapReadyCallback,
    View.OnClickListener {

    private lateinit var mBinding: ActivityWaypointOneBinding

    private lateinit var mLocateButton: Button
    private lateinit var mAddButton: Button
    private lateinit var mClearButton: Button
    private lateinit var configButton: Button
    private lateinit var mUploadButton: Button
    private lateinit var mStartButton: Button
    private lateinit var mStopButton: Button


    private var isAdd = false
    private var droneLocationLat: Double = 15.0
    private var droneLocationLng: Double = 15.0
    private var droneMarker: Marker? = null
    private val markers: MutableMap<Int, Marker> = ConcurrentHashMap<Int, Marker>()
    private var mapboxMap: MapboxMap? = null

    private var altitude = 100f
    private var speed = 10f

    private val waypointList = mutableListOf<Waypoint>()
    private var instance: WaypointMissionOperator? = null
    private var finishedAction = WaypointMissionFinishedAction.NO_ACTION
    private var headingMode = WaypointMissionHeadingMode.AUTO

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding = ActivityWaypointOneBinding.inflate(layoutInflater)
        val view = mBinding.root

        Mapbox.getInstance(this, getString(R.string.mapbox_access_token))
        // this will get your mapbox instance using your access token
        setContentView(view)

        initUi() // initialize the UI

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.onCreate(savedInstanceState)
        mapFragment.getMapAsync(this)

        addListener() // will add a listener to the waypoint mission operator
    }

    override fun onMapReady(mapboxMap: MapboxMap) {
        this.mapboxMap = mapboxMap // initialize the map
        mapboxMap.addOnMapClickListener(this)
        mapboxMap.setStyle(Style.MAPBOX_STREETS) { // set the view of the map

        }
    }

    override fun onMapClick(point: LatLng): Boolean {
        if (isAdd) { // if the user is adding waypoints
            markWaypoint(point) // this will mark the waypoint visually
            val waypoint = Waypoint(
                point.latitude,
                point.longitude,
                point.altitude.toFloat()
            ) // this will create the waypoint object to be added to the mission

            if (waypointMissionBuilder == null) {
                waypointMissionBuilder = WaypointMission.Builder().also { builder ->
                    waypointList.add(waypoint) // add the waypoint to the list
                    builder.waypointList(waypointList).waypointCount(waypointList.size)
                }
            } else {
                waypointMissionBuilder?.let { builder ->
                    waypointList.add(waypoint)
                    builder.waypointList(waypointList).waypointCount(waypointList.size)
                }
            }
        } else {
            setResultToToast("Cannot Add Waypoint")
        }
        return true
    }

    private fun markWaypoint(point: LatLng) {
        val markerOptions = MarkerOptions()
            .position(point)
        mapboxMap?.let {
            val marker = it.addMarker(markerOptions)
            markers.put(markers.size, marker)
        }
    }

    override fun onResume() {
        super.onResume()
        initFlightController()
    }

    override fun onDestroy() {
        super.onDestroy()
        removeListener()
    }

    //Add Listener for WaypointMissionOperator
    private fun addListener() {
        getWaypointMissionOperator()?.addListener(eventNotificationListener)
    }

    private fun removeListener() {
        getWaypointMissionOperator()?.removeListener(eventNotificationListener)
    }

    private val eventNotificationListener: WaypointMissionOperatorListener =
        object : WaypointMissionOperatorListener {
            override fun onDownloadUpdate(downloadEvent: WaypointMissionDownloadEvent) {}
            override fun onUploadUpdate(uploadEvent: WaypointMissionUploadEvent) {}
            override fun onExecutionUpdate(executionEvent: WaypointMissionExecutionEvent) {}
            override fun onExecutionStart() {}
            override fun onExecutionFinish(error: DJIError?) {
                setResultToToast("Execution finished: " + if (error == null) "Success!" else error.description)
            }
        }

    private fun getWaypointMissionOperator(): WaypointMissionOperator? { // returns the mission operator
        if (instance == null) {
            if (DJISDKManager.getInstance().missionControl != null) {
                instance = DJISDKManager.getInstance().missionControl.waypointMissionOperator
            }
        }
        return instance
    }

    private fun initUi() {
        mLocateButton = findViewById(R.id.locate)
        mAddButton = findViewById(R.id.add)
        mClearButton = findViewById(R.id.clear)
        configButton = findViewById(R.id.config)
        mUploadButton = findViewById(R.id.upload)
        mStartButton = findViewById(R.id.start)
        mStopButton = findViewById(R.id.stop)

        mLocateButton.setOnClickListener(this)
        mAddButton.setOnClickListener(this)
        mClearButton.setOnClickListener(this)
        configButton.setOnClickListener(this)
        mUploadButton.setOnClickListener(this)
        mStartButton.setOnClickListener(this)
        mStopButton.setOnClickListener(this)
    }

    private fun initFlightController() {
        // this will initialize the flight controller with predetermined data
        DJIApplication.getFlightController()?.let { flightController ->
            flightController.setStateCallback { flightControllerState ->
                // set the latitude and longitude of the drone based on aircraft location
                droneLocationLat = flightControllerState.aircraftLocation.latitude
                droneLocationLng = flightControllerState.aircraftLocation.longitude
                runOnUiThread {
                    updateDroneLocation() // this will be called on the main thread
                    cameraUpdate()
                }
            }
        }
    }


    private fun updateDroneLocation() { // this will draw the aircraft as it moves
        //Log.i(TAG, "Drone Lat: $droneLocationLat - Drone Lng: $droneLocationLng")
        if (droneLocationLat.isNaN() || droneLocationLng.isNaN()) {
            return
        }

        val pos = LatLng(droneLocationLat, droneLocationLng)
        // the following will draw the aircraft on the screen
        val markerOptions = MarkerOptions()
            .position(pos)
            .icon(IconFactory.getInstance(this).fromResource(R.drawable.aircraft))
        runOnUiThread {
            droneMarker?.remove()
            if (checkGpsCoordination(droneLocationLat, droneLocationLng)) {
                droneMarker = mapboxMap?.addMarker(markerOptions)
            }
        }
    }

    private fun cameraUpdate() { // update where you're looking on the map
        if (droneLocationLat.isNaN() || droneLocationLng.isNaN()) {
            return
        }
        val pos = LatLng(droneLocationLat, droneLocationLng)
        val zoomLevel = 18.0
        val cameraUpdate = CameraUpdateFactory.newLatLngZoom(pos, zoomLevel)
        mapboxMap?.moveCamera(cameraUpdate)
    }

    private fun showSettingsDialog() {
        val wayPointSettings =
            layoutInflater.inflate(R.layout.dialog_waypointsetting, null) as LinearLayout

        val wpAltitudeTV = wayPointSettings.findViewById<View>(R.id.altitude) as TextView
        val speedRG = wayPointSettings.findViewById<View>(R.id.speed) as RadioGroup
        val actionAfterFinishedRG =
            wayPointSettings.findViewById<View>(R.id.actionAfterFinished) as RadioGroup
        val headingRG = wayPointSettings.findViewById<View>(R.id.heading) as RadioGroup

        speedRG.setOnCheckedChangeListener { _, checkedId -> // set the speed to the selected option
            Log.d(TAG, "Select speed")
            when (checkedId) {
                R.id.lowSpeed -> {
                    speed = 3.0f
                }
                R.id.MidSpeed -> {
                    speed = 5.0f
                }
                R.id.HighSpeed -> {
                    speed = 10.0f
                }
            }
        }

        actionAfterFinishedRG.setOnCheckedChangeListener { _, checkedId -> // set the action after finishing the mission
            Log.d(TAG, "Select finish action")

            when (checkedId) {
                R.id.finishNone -> {
                    finishedAction = WaypointMissionFinishedAction.NO_ACTION
                }
                R.id.finishGoHome -> {
                    finishedAction = WaypointMissionFinishedAction.GO_HOME
                }
                R.id.finishAutoLanding -> {
                    finishedAction = WaypointMissionFinishedAction.AUTO_LAND
                }
                R.id.finishToFirst -> {
                    finishedAction = WaypointMissionFinishedAction.GO_FIRST_WAYPOINT
                }
            }
        }

        headingRG.setOnCheckedChangeListener { _, checkedId -> // changes the heading

            Log.d(TAG, "Select heading")
            when (checkedId) {
                R.id.headingNext -> {
                    headingMode = WaypointMissionHeadingMode.AUTO
                }
                R.id.headingInitDirec -> {
                    headingMode = WaypointMissionHeadingMode.USING_INITIAL_DIRECTION
                }
                R.id.headingRC -> {
                    headingMode = WaypointMissionHeadingMode.CONTROL_BY_REMOTE_CONTROLLER
                }
                R.id.headingWP -> {
                    headingMode = WaypointMissionHeadingMode.USING_WAYPOINT_HEADING
                }

            }
        }

        AlertDialog.Builder(this) // creates the dialog
            .setTitle("")
            .setView(wayPointSettings)
            .setPositiveButton("Finish") { dialog, id ->
                val altitudeString = wpAltitudeTV.text.toString()
                altitude = nullToIntegerDefault(altitudeString).toInt().toFloat()
                Log.e(TAG, "altitude $altitude")
                Log.e(TAG, "speed $speed")
                Log.e(TAG, "mFinishedAction $finishedAction")
                Log.e(TAG, "mHeadingMode $headingMode")
                configWayPointMission()
            }
            .setNegativeButton("Cancel") { dialog, id -> dialog.cancel() }
            .create()
            .show()
    }

    private fun nullToIntegerDefault(value: String): String {
        var newValue = value
        if (!isIntValue(newValue)) newValue = "0"
        return newValue
    }

    private fun isIntValue(value: String): Boolean {
        try {
            val newValue = value.replace(" ", "")
            newValue.toInt()
        } catch (e: Exception) {
            return false
        }
        return true
    }

    private fun enableDisableAdd() { // toggle for adding or not
        if (!isAdd) {
            isAdd = true
            mAddButton.text = "Exit"
        } else {
            isAdd = false
            mAddButton.text = "Add"
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.locate -> { // will draw the drone and move camera to the position of the drone on the map
                updateDroneLocation()
                cameraUpdate()
            }
            R.id.add -> { // this will toggle the adding of the waypoints
                enableDisableAdd()
            }
            R.id.clear -> { // clear the waypoints on the map
                runOnUiThread {
                    mapboxMap?.clear()
                }
            }
            R.id.config -> { // this will show the settings
                showSettingsDialog()
            }
            R.id.upload -> { // this will upload the mission to the drone so that it can execute it
                uploadWaypointMission()
            }
            R.id.start -> { // this will let the drone start navigating to the waypoints
                startWaypointMission()
            }
            R.id.stop -> { // this will immediately stop the waypoint mission
                stopWaypointMission()
            }
            else -> {}
        }
    }


    private fun configWayPointMission() {
        if (waypointMissionBuilder == null) {
            waypointMissionBuilder = WaypointMission.Builder()
                .finishedAction(finishedAction) // initialize the mission builder if null
                .headingMode(headingMode)
                .autoFlightSpeed(speed)
                .maxFlightSpeed(speed)
                .flightPathMode(WaypointMissionFlightPathMode.NORMAL)
        }

        waypointMissionBuilder?.let { builder ->
            builder.finishedAction(finishedAction)
                .headingMode(headingMode)
                .autoFlightSpeed(speed)
                .maxFlightSpeed(speed)
                .flightPathMode(WaypointMissionFlightPathMode.NORMAL)

            if (builder.waypointList.size > 0) {
                for (i in builder.waypointList.indices) { // set the altitude of all waypoints to the user defined altitude
                    builder.waypointList[i].altitude = altitude
                }
                setResultToToast("Set Waypoint attitude successfully")
            }
            getWaypointMissionOperator()?.let { operator ->
                val error = operator.loadMission(builder.build()) // load the mission
                if (error == null) {
                    setResultToToast("loadWaypoint succeeded")
                } else {
                    setResultToToast("loadWaypoint failed " + error.description)
                }
            }
        }
    }

    private fun uploadWaypointMission() { // upload the mission
        getWaypointMissionOperator()!!.uploadMission { error ->
            if (error == null) {
                setResultToToast("Mission upload successfully!")
            } else {
                setResultToToast("Mission upload failed, error: " + error.description + " retrying...")
                getWaypointMissionOperator()?.retryUploadMission(null)
            }
        }
    }

    private fun startWaypointMission() { // start mission
        getWaypointMissionOperator()?.startMission { error ->
            setResultToToast("Mission Start: " + if (error == null) "Successfully" else error.description)
        }
    }

    private fun stopWaypointMission() { // stop mission
        getWaypointMissionOperator()?.stopMission { error ->
            setResultToToast("Mission Stop: " + if (error == null) "Successfully" else error.description)
        }
    }


    private fun setResultToToast(string: String) {
        runOnUiThread { Toast.makeText(this, string, Toast.LENGTH_SHORT).show() }
    }


    companion object {
        const val TAG = "WaypointOneActivity"

        private var waypointMissionBuilder: WaypointMission.Builder? = null
        // you will use this to add your waypoints

        fun checkGpsCoordination(latitude: Double, longitude: Double): Boolean {
            // this will check if your gps coordinates are valid
            return latitude > -90 && latitude < 90 && longitude > -180 && longitude < 180 && latitude != 0.0 && longitude != 0.0
        }
    }
}