package net.marllex.waselak.feature.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.marllex.waselak.feature.auth.biometric.BiometricAuthenticator
import net.marllex.waselak.feature.auth.biometric.BiometricResult
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import net.marllex.waselak.feature.auth.generated.resources.Res
import net.marllex.waselak.feature.auth.generated.resources.*
import waselak.core.core_ui.generated.resources.Res as CoreRes
import waselak.core.core_ui.generated.resources.waslek_logo

@Composable
fun BiometricGateScreen(
    biometricAuth: BiometricAuthenticator,
    onSuccess: () -> Unit,
) {
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Resolve string resources at composition time for use in coroutines
    val authReason = stringResource(Res.string.biometric_subtitle)
    val cancelledMsg = stringResource(Res.string.biometric_cancelled)

    fun doAuthenticate() {
        scope.launch {
            errorMessage = null
            when (val result = biometricAuth.authenticate(authReason)) {
                is BiometricResult.Success -> onSuccess()
                is BiometricResult.Cancelled -> errorMessage = cancelledMsg
                is BiometricResult.Error -> errorMessage = result.message
                is BiometricResult.NotAvailable -> onSuccess()
            }
        }
    }

    // Auto-trigger biometric on first composition
    LaunchedEffect(Unit) {
        when (val result = biometricAuth.authenticate(authReason)) {
            is BiometricResult.Success -> onSuccess()
            is BiometricResult.Cancelled -> errorMessage = cancelledMsg
            is BiometricResult.Error -> errorMessage = result.message
            is BiometricResult.NotAvailable -> onSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp),
        ) {
            // App logo
            Image(
                painter = painterResource(CoreRes.drawable.waslek_logo),
                contentDescription = "Waslek",
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp)),
                contentScale = ContentScale.Crop,
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = stringResource(Res.string.verify_identity),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(Res.string.biometric_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Fingerprint icon button
            IconButton(
                onClick = { doAuthenticate() },
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                    ),
            ) {
                Icon(
                    imageVector = Icons.Filled.Fingerprint,
                    contentDescription = stringResource(Res.string.tap_to_retry),
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(Res.string.tap_to_retry),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Error message
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
