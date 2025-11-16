// EnhancedSplashScreen.kt
package com.weproz.superreader

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.weproz.superreader.ui.theme.AppTheme
import com.weproz.superreader.ui.theme.SuperReaderTheme


@Composable
fun EnhancedSplashScreen(appTheme: AppTheme) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            val logoRes = when (appTheme) {
                AppTheme.LIGHT -> R.drawable.ic_app_logo_light
                AppTheme.DARK -> R.drawable.ic_app_logo_dark
                AppTheme.SEPIA -> R.drawable.ic_app_logo_sepia
            }

            Image(
                painter = painterResource(id = logoRes),
                contentDescription = "Super Reader Logo",
                modifier = Modifier.size(120.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(id = R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Your Reading Companion",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}

@Preview
@Composable
fun EnhancedSplashScreenPreview() {
    SuperReaderTheme(appTheme = AppTheme.LIGHT) {
        EnhancedSplashScreen(appTheme = AppTheme.LIGHT)
    }
}