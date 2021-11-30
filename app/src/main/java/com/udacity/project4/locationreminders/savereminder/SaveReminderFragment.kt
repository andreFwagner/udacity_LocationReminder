package com.udacity.project4.locationreminders.savereminder

import android.Manifest
import android.annotation.TargetApi
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import com.google.android.gms.location.*
import com.google.android.material.snackbar.Snackbar
import com.udacity.project4.R
import com.udacity.project4.base.BaseFragment
import com.udacity.project4.base.NavigationCommand
import com.udacity.project4.databinding.FragmentSaveReminderBinding
import com.udacity.project4.locationreminders.geofence.GeofenceBroadcastReceiver
import com.udacity.project4.locationreminders.reminderslist.ReminderDataItem
import com.udacity.project4.utils.setDisplayHomeAsUpEnabled
import org.koin.android.ext.android.inject

const val GEOFENCE_RADIUS_IN_METERS = 100f
private const val TAG = "SaveReminderFragment"
private const val REQUEST_FOREGROUND_AND_BACKGROUND_PERMISSION_RESULT_CODE = 33
private const val REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE = 34
private const val REQUEST_TURN_DEVICE_LOCATION_ON = 29
private const val LOCATION_PERMISSION_INDEX = 0
private const val BACKGROUND_LOCATION_PERMISSION_INDEX = 1
private const val ACTION_GEOFENCE_EVENT = "SaveReminderFragment.project4.action.ACTION_GEOFENCE_EVENT"

class SaveReminderFragment : BaseFragment() {
    // Get the view model this time as a single to be shared with the another fragment
    override val _viewModel: SaveReminderViewModel by inject()
    private lateinit var binding: FragmentSaveReminderBinding

    private lateinit var contxt: Context

    private lateinit var geofencingClient: GeofencingClient

    private lateinit var newReminder: ReminderDataItem

    private var resultCode = 0

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(contxt, GeofenceBroadcastReceiver::class.java)
        intent.action = ACTION_GEOFENCE_EVENT
        PendingIntent.getBroadcast(contxt, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private val runningQOrLater =
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q

    /*
    Solution for AppCrash after losing context
    https://knowledge.udacity.com/questions/595798
     */
    override fun onAttach(context: Context) {
        super.onAttach(context)
        contxt = context
    }

    override fun onResume() {
        super.onResume()
        contxt = requireContext()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding =
            DataBindingUtil.inflate(inflater, R.layout.fragment_save_reminder, container, false)

        setDisplayHomeAsUpEnabled(true)

        binding.viewModel = _viewModel
        geofencingClient = LocationServices.getGeofencingClient(contxt)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = this
        binding.selectLocation.setOnClickListener {
            //            Navigate to another fragment to get the user location
            _viewModel.navigationCommand.value =
                NavigationCommand.To(SaveReminderFragmentDirections.actionSaveReminderFragmentToSelectLocationFragment())
        }

        binding.saveReminder.setOnClickListener {
            val title = _viewModel.reminderTitle.value
            val description = _viewModel.reminderDescription.value ?: ""
            val location = _viewModel.reminderSelectedLocationStr.value ?: ""
            val latitude = _viewModel.latitude.value
            val longitude = _viewModel.longitude.value

            newReminder = ReminderDataItem(
                title,
                description,
                location,
                latitude,
                longitude
            )

            checkPermissionsAndStartGeofencing()
        }
    }

    private fun addNewGeofence() {
        if (_viewModel.validateAndSaveReminder(newReminder)) {
            val geofence = Geofence.Builder()
                .setRequestId(newReminder.id)
                .setCircularRegion(newReminder.latitude!!, newReminder.longitude!!, GEOFENCE_RADIUS_IN_METERS)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .build()

            val geofencingRequest = GeofencingRequest.Builder()
                .setInitialTrigger(Geofence.GEOFENCE_TRANSITION_ENTER)
                .addGeofence(geofence)
                .build()

            geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent)?.run {
                addOnSuccessListener {
                    Log.e("Add Geofence", geofence.requestId)
                }
                addOnFailureListener {
                    // Failed to add geofences.
                    Toast.makeText(
                        contxt, R.string.geofences_not_added,
                        Toast.LENGTH_SHORT
                    ).show()
                    if ((it.message != null)) {
                        Log.w(TAG, it.message.toString())
                    }
                }
            }
            _viewModel.onClear()
        }
    }

    private fun checkPermissionsAndStartGeofencing() {
        if (foregroundAndBackgroundLocationPermissionApproved()) {
            addNewGeofence()
        } else {
            requestForegroundAndBackgroundLocationPermissions()
        }
    }

    @TargetApi(29)
    private fun foregroundAndBackgroundLocationPermissionApproved(): Boolean {
        val foregroundLocationApproved = (
            PackageManager.PERMISSION_GRANTED ==
                ActivityCompat.checkSelfPermission(
                    contxt,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        val backgroundPermissionApproved =
            if (runningQOrLater) {
                PackageManager.PERMISSION_GRANTED ==
                    ActivityCompat.checkSelfPermission(
                        contxt, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    )
            } else {
                true
            }
        return foregroundLocationApproved && backgroundPermissionApproved
    }

    @TargetApi(29)
    private fun requestForegroundAndBackgroundLocationPermissions() {
        if (foregroundAndBackgroundLocationPermissionApproved())
            return

        // Else request the permission
        // this provides the result[LOCATION_PERMISSION_INDEX]
        var permissionsArray = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

        resultCode = when {
            runningQOrLater -> {
                // this provides the result[BACKGROUND_LOCATION_PERMISSION_INDEX]
                permissionsArray += Manifest.permission.ACCESS_BACKGROUND_LOCATION
                REQUEST_FOREGROUND_AND_BACKGROUND_PERMISSION_RESULT_CODE
            }
            else -> REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE
        }

        Log.d(TAG, "Request foreground only location permission")
        requestPermissions(
            permissionsArray,
            resultCode
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == resultCode && grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            addNewGeofence()
        } else {
            Snackbar.make(
                binding.saveReminderFragment,
                R.string.location_required_error, Snackbar.LENGTH_INDEFINITE
            ).setAction(android.R.string.ok) {
                requestForegroundAndBackgroundLocationPermissions()
            }.show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // make sure to clear the view model after destroy, as it's a single view model.
        _viewModel.onClear()
    }
}
