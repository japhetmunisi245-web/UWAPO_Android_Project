package com.uwapo.app

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.uwapo.app.ui.UWAPORoot
import com.uwapo.app.ui.theme.UWAPOTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = (application as UWAPOApplication).database
        setContent {
            val systemInDark = isSystemInDarkTheme()
            var darkTheme by rememberSaveable { mutableStateOf(systemInDark) }
            UWAPOTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    UWAPORoot(
                        dao = db.uwapoDao(),
                        isDarkTheme = darkTheme,
                        onToggleTheme = { darkTheme = !darkTheme }
                    )
                }
            }
        }
    }
}
