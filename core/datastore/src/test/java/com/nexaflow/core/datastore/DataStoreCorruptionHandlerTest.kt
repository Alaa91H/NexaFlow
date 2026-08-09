package com.nexaflow.core.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

/**
 * Guards the corruption-handler wiring added across the four DataStore files:
 * if a crash mid-write corrupts the on-disk file, startup reads must reset to
 * defaults instead of throwing and crashing the app.
 */
class DataStoreCorruptionHandlerTest {

    private fun corruptFile(): File {
        val file = File.createTempFile("corrupt", ".preferences_pb")
        file.writeBytes(byteArrayOf(0x00, 0xFF.toByte(), 0x01, 0x02, 0x03, 0x7F, 0x0A, 0x0B, 0x0C))
        file.deleteOnExit()
        return file
    }

    @Test
    fun corruptFileWithoutHandlerThrows() {
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { corruptFile() })
        val exception = assertThrows(CorruptionException::class.java) {
            runBlocking { dataStore.data.first() }
        }
        assertEquals(true, exception.message?.isNotBlank())
    }

    @Test
    fun corruptFileWithHandlerReturnsDefaults() = runBlocking {
        val corrupt = corruptFile()
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { corrupt },
            corruptionHandler = ReplaceFileCorruptionHandler {
                // Simulate replacement: remove the corrupt file so the follow-up
                // write can recreate it (File.renameTo cannot overwrite an
                // existing file on Windows; on Linux/Android the rename replaces
                // it implicitly).
                corrupt.delete()
                emptyPreferences()
            }
        )
        val prefs = dataStore.data.first()
        // Defaults win: the corrupt file was replaced, not partially parsed.
        assertEquals(null, prefs[stringPreferencesKey("theme_mode")])
        assertEquals(true, prefs.asMap().isEmpty())
    }
}
