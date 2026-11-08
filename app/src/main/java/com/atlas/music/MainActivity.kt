package com.atlas.music

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.atlas.music.di.AtlasSettings
import com.atlas.music.di.ServiceLocator
import com.atlas.music.ui.AtlasMusicApp
import com.atlas.music.ui.theme.AtlasTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val notificationsLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
        val audioLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                // The rationale screen replaces the app; user can still grant from Settings.
            }
        }

        // Best-effort notification permission request; playback works without it.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val hasAudioPermission = rememberSaveable {
                mutableStateOf(
                    ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
                )
            }
            val settings by ServiceLocator.settings.settings.collectAsState(initial = AtlasSettings())

            AtlasTheme(settings) {
                if (hasAudioPermission.value) {
                    LaunchedEffect(Unit) { handleExternalIntent(intent) }
                    AtlasMusicApp()
                } else {
                    PermissionGate(
                        onGrant = { audioLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO) },
                        onRecheck = {
                            hasAudioPermission.value = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleExternalIntent(intent)
    }

    private fun handleExternalIntent(intent: Intent?) {
        val data = intent?.data
        if (intent?.action == Intent.ACTION_VIEW && data != null) {
            val uri = data
            val title = uri.lastPathSegment?.substringBeforeLast('.') ?: "Unknown track"
            ServiceLocator.playbackConnection.playExternalUri(uri, title)
            // Consume so rotation doesn't replay the track.
            intent.action = null
        }
    }
}

@Composable
private fun PermissionGate(onGrant: () -> Unit, onRecheck: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(96.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "Atlas Music",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                "Ad-less Music. Your library, your rules.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("One permission, one purpose", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Atlas needs access to your audio files to index them. Nothing is uploaded, no account is required, and you can revoke this any time in Settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = onGrant, modifier = Modifier.height(52.dp)) {
                Text("Grant music access", style = MaterialTheme.typography.labelLarge)
            }
            TextButton(onClick = onRecheck) {
                Text("I already granted it — check again")
            }
        }
    }
}
