package com.sohum.bandlog.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sohum.bandlog.util.AppUpdater
import kotlinx.coroutines.launch

class UpdateViewModel(app: Application) : AndroidViewModel(app) {

    var available by mutableStateOf<AppUpdater.Update?>(null); private set
    var downloading by mutableStateOf(false); private set
    var progress by mutableFloatStateOf(0f); private set
    var needsPermission by mutableStateOf(false); private set
    var checking by mutableStateOf(false); private set
    var upToDate by mutableStateOf(false); private set
    private var checked = false

    fun checkOnce() {
        if (checked) return
        checked = true
        check()
    }

    /** Manual check (from the Settings button). Sets [upToDate] when nothing newer is published. */
    fun check() {
        viewModelScope.launch {
            checking = true; upToDate = false
            val u = AppUpdater.check()
            available = u
            upToDate = (u == null)
            checking = false
        }
    }

    fun dismiss() { available = null }

    fun update(context: Context) {
        val u = available ?: return
        // Android 8+ requires the one-time "install unknown apps" grant before we can install.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            needsPermission = true
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            return
        }
        needsPermission = false
        viewModelScope.launch {
            downloading = true; progress = 0f
            try {
                val file = AppUpdater.download(context, u) { progress = it }
                AppUpdater.install(context, file)
            } catch (_: Exception) {
                // leave the dialog open so the user can retry
            } finally {
                downloading = false
            }
        }
    }
}

/** Launch-time "Update available" dialog. Renders nothing unless a newer build is published. */
@Composable
fun UpdateDialog(vm: UpdateViewModel) {
    val update = vm.available ?: return
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = { if (!vm.downloading) vm.dismiss() },
        title = { Text("Update available") },
        text = {
            Column {
                Text("Version ${update.versionName} is ready to install.")
                if (update.notes.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(update.notes, fontSize = 13.sp)
                }
                if (vm.downloading) {
                    Spacer(Modifier.height(14.dp))
                    LinearProgressIndicator({ vm.progress }, Modifier.fillMaxWidth())
                    Text("Downloading… ${(vm.progress * 100).toInt()}%", fontSize = 12.sp)
                } else if (vm.needsPermission) {
                    Spacer(Modifier.height(10.dp))
                    Text("Turn on “Allow from this source” for Locked In, come back, and tap Update again.", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.update(context) }, enabled = !vm.downloading) {
                Text(if (vm.downloading) "Downloading…" else "Update")
            }
        },
        dismissButton = {
            if (!vm.downloading) TextButton(onClick = { vm.dismiss() }) { Text("Later") }
        },
    )
}
