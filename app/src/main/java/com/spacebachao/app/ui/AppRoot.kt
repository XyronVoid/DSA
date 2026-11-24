package com.spacebachao.app.ui

import android.app.Activity
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spacebachao.app.AppViewModel
import com.spacebachao.app.Perms
import com.spacebachao.app.ui.theme.LocalApple

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

    // Android 11+: the system "Allow this app to modify these files?" popup
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

    val job = state.job
    val paywall = state.paywall
    BackHandler(enabled = paywall != null) { vm.dismissPaywall() }
    BackHandler(enabled = paywall == null && screen != Screen.HOME && job == null) { screen = Screen.HOME }
    // Back must not close the app while compressing
    BackHandler(enabled = job?.running == true) { }
    BackHandler(enabled = job != null && !job.running && paywall == null) { vm.dismissJob() }

    // Soft colourful background behind glass; text colour set for light and dark mode
    AppBackground {
        CompositionLocalProvider(LocalContentColor provides LocalApple.current.label) {
            when {
                !state.hasPermission -> PermissionScreen(onGrant = { permissionLauncher.launch(Perms.required()) })
                paywall != null -> ProScreen(state, paywall, vm)
                job != null -> JobScreen(
                    job = job,
                    binDays = state.effectiveBinDays,
                    binSize = state.binSize,
                    onCancel = vm::cancelJob,
                    onDone = vm::dismissJob,
                    onEmptyBin = vm::emptyBin,
                    onGetPro = { vm.openPro() },
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
}
