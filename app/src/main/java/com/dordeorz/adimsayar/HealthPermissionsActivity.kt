package com.dordeorz.adimsayar

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dordeorz.adimsayar.data.SettingsStore
import com.dordeorz.adimsayar.ui.theme.AdimSayarTheme
import com.dordeorz.adimsayar.ui.theme.isDarkTheme

class HealthPermissionsActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Locales.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = SettingsStore.get(applicationContext).settings.value
        setContent {
            AdimSayarTheme(darkTheme = isDarkTheme(settings.themeMode), dynamicColor = settings.dynamicColor) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.safeDrawingPadding().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(stringResource(R.string.wearable_privacy_title), style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.wearable_privacy_text), style = MaterialTheme.typography.bodyLarge)
                        Button(onClick = ::finish) { Text(stringResource(R.string.close)) }
                    }
                }
            }
        }
    }
}
