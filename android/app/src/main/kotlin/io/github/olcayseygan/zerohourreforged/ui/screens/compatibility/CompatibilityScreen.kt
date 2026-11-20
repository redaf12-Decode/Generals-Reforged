/*
**      Command & Conquer Generals Zero Hour(tm)
**      Copyright 2025 Electronic Arts Inc.
**
**      This program is free software: you can redistribute it and/or modify
**      it under the terms of the GNU General Public License as published by
**      the Free Software Foundation, either version 3 of the License, or
**      (at your option) any later version.
**
**      This program is distributed in the hope that it will be useful,
**      but WITHOUT ANY WARRANTY; without even the implied warranty of
**      MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
**      GNU General Public License for more details.
**
**      You should have received a copy of the GNU General Public License
**      along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package io.github.olcayseygan.zerohourreforged.ui.screens.compatibility

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.olcayseygan.zerohourreforged.AndroidRequirements
import io.github.olcayseygan.zerohourreforged.CompatibilityEvaluator
import io.github.olcayseygan.zerohourreforged.DeviceCapabilities
import io.github.olcayseygan.zerohourreforged.R
import io.github.olcayseygan.zerohourreforged.ui.components.ApplicationTopBar
import io.github.olcayseygan.zerohourreforged.ui.components.CompatibilityResultCard
import io.github.olcayseygan.zerohourreforged.ui.components.InformationRow
import io.github.olcayseygan.zerohourreforged.ui.components.ResultRow
import io.github.olcayseygan.zerohourreforged.ui.components.SectionContainer
import io.github.olcayseygan.zerohourreforged.ui.theme.ZeroHourTheme
import io.github.olcayseygan.zerohourreforged.ui.theme.spacing

/**
 * The System Requirements & Device Compatibility screen.
 *
 * <p>It renders the three pure classes it is given - [AndroidRequirements],
 * [DeviceCapabilities] and [CompatibilityEvaluator] - and evaluates nothing itself. The verdict and
 * every check come from [CompatibilityEvaluator.evaluate], so this screen and the documentation
 * cannot disagree.
 *
 * <p>Layout is the verdict card followed by two [SectionContainer]s: the application's requirements
 * and what the platform reported. Each container is one tonal step above the screen background, so
 * the grouping reads in both schemes without any shadow.
 *
 * @param onNavigateBack when non-null, the top bar shows a back arrow that calls this.
 */
@Composable
fun CompatibilityScreen(
    device: DeviceCapabilities,
    report: CompatibilityEvaluator.Report,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            ApplicationTopBar(
                title = stringResource(R.string.compat_title),
                onNavigateBack = onNavigateBack,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = MaterialTheme.spacing.screenEdge,
                    vertical = MaterialTheme.spacing.screenVertical,
                ),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sectionGap),
        ) {
            CompatibilityResultCard(
                tone = CompatibilityPresentation.resultTone(report.result),
                headline = verdictText(report.result),
                summary = stringResource(R.string.compat_result_summary),
                rows = report.checks.map { check ->
                    ResultRow(
                        tone = CompatibilityPresentation.checkTone(check),
                        title = check.name,
                        detail = check.detail,
                    )
                },
            )

            SectionContainer(title = stringResource(R.string.compat_group_app)) {
                RequirementsGroup()
            }

            SectionContainer(title = stringResource(R.string.compat_group_device)) {
                DeviceGroup(device)
            }

            Text(
                text = stringResource(R.string.compat_performance_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun verdictText(result: CompatibilityEvaluator.Result): String = stringResource(
    when (result) {
        CompatibilityEvaluator.Result.COMPATIBLE -> R.string.compat_result_compatible
        CompatibilityEvaluator.Result.PARTIALLY_COMPATIBLE -> R.string.compat_result_partial
        CompatibilityEvaluator.Result.UNSUPPORTED -> R.string.compat_result_unsupported
        CompatibilityEvaluator.Result.UNABLE_TO_VERIFY -> R.string.compat_result_unable
    },
)

/** The application's fixed requirements, read from [AndroidRequirements] so they cannot drift. */
@Composable
private fun RequirementsGroup() {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        InformationRow(
            label = stringResource(R.string.compat_label_android),
            value = stringResource(R.string.compat_value_android, AndroidRequirements.MIN_ANDROID_NAME),
            stacked = true,
        )
        InformationRow(
            label = stringResource(R.string.compat_label_cpu),
            value = stringResource(R.string.compat_value_cpu, AndroidRequirements.SUPPORTED_ABI),
            stacked = true,
        )
        InformationRow(
            label = stringResource(R.string.compat_label_graphics),
            value = stringResource(
                R.string.compat_value_graphics,
                AndroidRequirements.REQUIRED_VULKAN_NAME,
                CompatibilityPresentation.join(AndroidRequirements.REQUIRED_EXTENSIONS),
                CompatibilityPresentation.join(AndroidRequirements.REQUIRED_FEATURES),
            ),
            stacked = true,
        )
        InformationRow(
            label = stringResource(R.string.compat_label_optional_vulkan),
            value = stringResource(
                R.string.compat_value_optional_vulkan,
                CompatibilityPresentation.join(AndroidRequirements.OPTIONAL_FEATURES),
            ),
            stacked = true,
        )
        InformationRow(
            label = stringResource(R.string.compat_label_gpus),
            value = AndroidRequirements.GPU_FAMILIES,
            stacked = true,
        )
        InformationRow(
            label = stringResource(R.string.compat_label_ram),
            value = stringResource(
                R.string.compat_value_ram,
                CompatibilityPresentation.gb(AndroidRequirements.MIN_RAM_BYTES),
                CompatibilityPresentation.gb(AndroidRequirements.RECOMMENDED_RAM_BYTES),
            ),
            stacked = true,
        )
        InformationRow(
            label = stringResource(R.string.compat_label_storage),
            value = AndroidRequirements.STORAGE_NOTE,
            stacked = true,
        )
        InformationRow(
            label = stringResource(R.string.compat_label_min_processor),
            value = AndroidRequirements.MIN_SOC_PROVISIONAL,
            stacked = true,
        )
        InformationRow(
            label = stringResource(R.string.compat_label_recommended_processor),
            value = AndroidRequirements.RECOMMENDED_SOC_PROVISIONAL,
            stacked = true,
        )
    }
}

/** What the platform reported about this device, with unverified values shown as such. */
@Composable
private fun DeviceGroup(device: DeviceCapabilities) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        InformationRow(
            label = stringResource(R.string.compat_label_manufacturer),
            value = CompatibilityPresentation.orUnknown(device.manufacturer),
        )
        InformationRow(
            label = stringResource(R.string.compat_label_model),
            value = CompatibilityPresentation.orUnknown(device.model),
        )
        InformationRow(
            label = stringResource(R.string.compat_label_android_version),
            value = androidVersion(device),
        )
        InformationRow(
            label = stringResource(R.string.compat_label_abis),
            value = device.supportedAbis?.takeIf { it.isNotEmpty() }?.let {
                CompatibilityPresentation.join(it)
            } ?: CompatibilityPresentation.orUnknown(null),
        )
        InformationRow(
            label = stringResource(R.string.compat_label_total_ram),
            value = if (device.totalRamBytes > 0) {
                stringResource(R.string.compat_value_gb, CompatibilityPresentation.gb(device.totalRamBytes))
            } else {
                stringResource(R.string.compat_value_not_reported)
            },
        )
        InformationRow(
            label = stringResource(R.string.compat_label_vulkan),
            value = vulkanSummary(device),
            stacked = true,
        )
        Text(
            text = stringResource(R.string.compat_vulkan_unverified_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun androidVersion(device: DeviceCapabilities): String {
    val release = if (device.androidRelease.isNullOrEmpty()) {
        stringResource(R.string.compat_value_unknown)
    } else {
        device.androidRelease
    }
    return if (device.sdkInt > 0) {
        stringResource(R.string.compat_value_android_version, release, device.sdkInt)
    } else {
        release
    }
}

@Composable
private fun vulkanSummary(device: DeviceCapabilities): String = when (device.vulkanSupported) {
    DeviceCapabilities.FieldState.PRESENT -> buildString {
        append(stringResource(R.string.compat_vulkan_present))
        append(' ')
        if (device.vulkanHardwareVersion > 0) {
            append(
                stringResource(
                    R.string.compat_vulkan_api_version,
                    AndroidRequirements.formatVulkanVersion(device.vulkanHardwareVersion),
                ),
            )
        } else {
            append(stringResource(R.string.compat_vulkan_api_version_unknown))
        }
        if (device.vulkanHardwareLevel >= 0) {
            append(' ')
            append(stringResource(R.string.compat_vulkan_hardware_level, device.vulkanHardwareLevel))
        }
    }
    DeviceCapabilities.FieldState.ABSENT -> stringResource(R.string.compat_vulkan_absent)
    DeviceCapabilities.FieldState.UNKNOWN -> stringResource(R.string.compat_value_not_reported)
}

@Preview(showBackground = true)
@Composable
private fun CompatibilityScreenPreview() {
    val device = DeviceCapabilities().apply {
        manufacturer = "Google"
        model = "Pixel"
        androidRelease = "15"
        sdkInt = 35
        supportedAbis = arrayOf("arm64-v8a")
        totalRamBytes = 8L * 1024 * 1024 * 1024
        vulkanSupported = DeviceCapabilities.FieldState.PRESENT
        vulkanHardwareVersion = AndroidRequirements.packVulkanVersion(1, 1, 0)
        vulkanHardwareLevel = 1
    }
    ZeroHourTheme {
        CompatibilityScreen(
            device = device,
            report = CompatibilityEvaluator.evaluate(device),
            modifier = Modifier.fillMaxSize(),
        )
    }
}
