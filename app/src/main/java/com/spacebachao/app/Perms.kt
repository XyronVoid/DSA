package com.spacebachao.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object Perms {
    fun required(): Array<String> = buildList {
        if (Build.VERSION.SDK_INT >= 33) {
            add(Manifest.permission.READ_MEDIA_IMAGES)
            add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT == 29) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        add(Manifest.permission.ACCESS_MEDIA_LOCATION)
    }.toTypedArray()

    fun hasMediaAccess(context: Context): Boolean {
        val needed = if (Build.VERSION.SDK_INT >= 33) {
            listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val write = if (Build.VERSION.SDK_INT == 29) listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE) else emptyList()
        return (needed + write).all { granted(context, it) }
    }

    fun hasLocation(context: Context): Boolean = granted(context, Manifest.permission.ACCESS_MEDIA_LOCATION)

    private fun granted(context: Context, p: String) =
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
}
