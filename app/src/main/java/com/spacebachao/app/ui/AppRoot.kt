@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.spacebachao.app.ui

import android.app.Activity
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spacebachao.app.AppViewModel
import com.spacebachao.app.Perms

private enum class Screen { HOME, BIN, SETTINGS }

@Composable
fun AppRoot(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { vm.onPermissionsResult() }

    val writeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result -> vm.onWriteRequestResult(result.resultCode == Activity.RESULT_OK) }

    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> vm.addPdfs(uris) }

    // Android 11+: "ye files badalne do?" wala system popup
    LaunchedEffect(state.writeRequest) {
        val uris = state.writeRequest ?: return@LaunchedEffect
        vm.onWriteRequestLaunched()
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                val pendingIntent = MediaStore.createWriteRequest(context.contentResolver, uris)
                writeLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
            } catch (e: Exception) {
                vm.onWriteRequestResult(false)
            }
        } else {
            vm.onWriteRequestResult(true)
        }
    }

    LaunchedEffect(state.message) {
        val msg = state.message ?: return@LaunchedEffect
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        vm.clearMessage()
    }

    BackHandler(enabled = screen != Screen.HOME && state.job == null) { screen = Screen.HOME }
    // Kaam chalte waqt back se app band na ho
    BackHandler(enabled = state.job?.running == true) { }
    BackHandler(enabled = state.job != null && state.job?.running == false) { vm.dismissJob() }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val job = state.job
        when {
            !state.hasPermission -> PermissionScreen(onGrant = { permissionLauncher.launch(Perms.required()) })
            job != null -> JobScreen(
                job = job,
                binDays = state.binDays,
                binSize = state.binSize,
                onCancel = vm::cancelJob,
                onDone = vm::dismissJob,
                onEmptyBin = vm::emptyBin,
            )
            screen == Screen.BIN -> BinScreen(state, vm, onBack = { screen = Screen.HOME })
            screen == Screen.SETTINGS -> SettingsScreen(state, vm, onBack = { screen = Screen.HOME })
            else -> HomeScreen(
                state = state,
                vm = vm,
                onPickPdf = { pdfLauncher.launch(arrayOf("application/pdf")) },
                onOpenBin = { screen = Screen.BIN },
                onOpenSettings = { screen = Screen.SETTINGS },
            )
        }
    }
}
