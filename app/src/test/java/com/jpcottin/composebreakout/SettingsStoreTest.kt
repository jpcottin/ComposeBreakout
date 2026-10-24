package com.jpcottin.composebreakout

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.jpcottin.composebreakout.ui.settingsCorruptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** A settings file that cannot be parsed is replaced, so edits still go through. */
class SettingsStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun aDamagedFileIsReplacedAndEditsGoThrough() = runTest {
        val file = tmp.newFile("settings.preferences_pb").apply { writeBytes(byteArrayOf(0x7F, 0x00, 0x42, 0x13)) }
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val store = PreferenceDataStoreFactory.create(corruptionHandler = settingsCorruptionHandler, scope = scope) { file }
        val key = booleanPreferencesKey("sound")
        store.edit { it[key] = false }
        assertEquals(false, store.data.first()[key])
        scope.cancel()
    }
}
