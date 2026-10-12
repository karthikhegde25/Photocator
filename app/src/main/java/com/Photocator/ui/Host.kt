package com.Photocator.ui

import android.net.Uri

/** Things only the activity can do (system pickers, settings screens, permission prompts). */
interface Host {
    /** Runs the permission sequence: camera, then media, then location. */
    fun requestPermissions()
    fun openAppSettings()
    fun openLocationSettings()
    /** Opens the image picker and, for a valid PNG/JPEG, pushes the Add Geotag screen. */
    fun pickPhotoToTag()
    /** Opens the image picker for a watermark; [onPicked] gets the uri or null. */
    fun pickWatermark(onPicked: (Uri?) -> Unit)
    fun viewPhoto(uri: Uri?)
    fun openUrl(url: String)
    /** Asks for access to videos, then calls [onDone]. */
    fun requestVideoAccess(onDone: () -> Unit)
    /** Opens the system share sheet for these photos or videos ([mime] is "image/jpeg" or "video/mp4"). */
    fun shareMedia(uris: List<Uri>, mime: String)
    /** Shows a system permission prompt ([sender]) and reports whether the user allowed it. */
    fun launchSender(sender: android.content.IntentSender, onDone: (Boolean) -> Unit)
    /** Asks the system for permission to delete [uris] (Android 11+), then calls [onDone]. */
    fun confirmDelete(uris: List<Uri>, onDone: () -> Unit)
}
