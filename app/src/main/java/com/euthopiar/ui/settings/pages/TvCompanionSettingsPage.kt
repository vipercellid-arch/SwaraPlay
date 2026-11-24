package com.euthopiar.ui.settings.pages

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.euthopiar.ui.components.LocalSnackbarController
import com.euthopiar.ui.settings.components.OpenSettingsSectionHeader
import com.euthopiar.ui.settings.components.SettingsScaffold
import com.euthopiar.ui.tv.TvCompanionPanel

@Composable
fun TvCompanionSettingsPage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarController = LocalSnackbarController.current

    SettingsScaffold(
        title = "Euthopiar TV",
        onBack = onBack,
        modifier = modifier
    ) { _ ->
        OpenSettingsSectionHeader(title = "TV Companion & Remote")

        Spacer(modifier = Modifier.height(14.dp))

        TvCompanionPanel(
            onShowMessage = { msg -> snackbarController.show(msg) }
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}
