package com.nexaflow.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File

/**
 * The «Save locally» entry inside the backup share sheet.
 *
 * The user opens Export &amp; share, the system share sheet appears with this
 * app listed as one of the targets («الحفظ محلياً»). Picking it lands here:
 * the cached backup file is written into wherever the user picks through the
 * SAF document picker, then this activity finishes without leaving UI behind.
 */
class SaveBackupActivity : ComponentActivity() {

    companion object {
        const val ACTION_SAVE_BACKUP = "com.nexaflow.app.action.SAVE_BACKUP"
        const val EXTRA_FILE_PATH = "extra_file_path"
    }

    private val createDocument =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) {
                val ok = runCatching {
                    contentResolver.openOutputStream(uri)?.use { out ->
                        openBackupSource()?.use { it.copyTo(out) }
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
        val filePath = intent?.getStringExtra(EXTRA_FILE_PATH)
        if (filePath != null) {
            val file = File(filePath)
            if (file.exists()) return file.inputStream()
        }
        val streamUri = intent?.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
        if (streamUri != null) {
            return runCatching { contentResolver.openInputStream(streamUri) }.getOrNull()
        }
        return null
    }
}
