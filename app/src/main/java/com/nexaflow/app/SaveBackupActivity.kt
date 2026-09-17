package com.nexaflow.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.IntentCompat

/**
 * The «Save locally» entry inside the backup share sheet.
 *
 * The user opens Export &amp; share, the system share sheet appears with this
 * app listed as one of the targets («الحفظ محلياً»). Picking it lands here:
 * the cached backup file is written into wherever the user picks through the
 * SAF document picker, then this activity finishes without leaving UI behind.
 */
class SaveBackupActivity : ComponentActivity() {

    private val createDocument =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) {
                val ok = runCatching {
                    requireNotNull(contentResolver.openOutputStream(uri)).use { out ->
                        requireNotNull(openBackupSource()).use { input ->
                            out.write(com.nexaflow.data.backup.BackupLimits.read(input).toByteArray(Charsets.UTF_8))
                        }
                    }
                }.isSuccess
                Toast.makeText(
                    this,
                    if (ok) R.string.backup_saved else R.string.backup_save_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No UI: jump straight into the SAF picker so the app never flashes.
        createDocument.launch("nexaflow_backup.json")
    }

    /**
     * The bytes to write: our cached backup file (pinned share-sheet row) or,
     * when another app shared a JSON file into us via the intent-filter, the
     * shared stream itself.
     */
    private fun openBackupSource(): java.io.InputStream? {
        val streamUri = intent?.let {
            IntentCompat.getParcelableExtra(it, Intent.EXTRA_STREAM, android.net.Uri::class.java)
        }
        if (intent?.action == Intent.ACTION_SEND && streamUri?.scheme == "content" &&
            intent?.type in setOf("application/json", "application/octet-stream")) {
            return runCatching { contentResolver.openInputStream(streamUri) }.getOrNull()
        }
        return null
    }
}
