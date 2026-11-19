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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.olcayseygan.zerohourreforged.AndroidRequirements
import io.github.olcayseygan.zerohourreforged.CompatibilityEvaluator
import io.github.olcayseygan.zerohourreforged.DeviceCapabilities
import io.github.olcayseygan.zerohourreforged.R
import io.github.olcayseygan.zerohourreforged.ui.components.CheckRow
import io.github.olcayseygan.zerohourreforged.ui.components.FactRow
import io.github.olcayseygan.zerohourreforged.ui.components.NoteText
import io.github.olcayseygan.zerohourreforged.ui.components.SectionHeader
import io.github.olcayseygan.zerohourreforged.ui.components.VerdictBanner
import io.github.olcayseygan.zerohourreforged.ui.theme.ZeroHourTheme

/**
 * The System Requirements & Device Compatibility screen as Compose.
 *
 * <p>It renders the three pure classes it is given - [AndroidRequirements],
 * [DeviceCapabilities] and [CompatibilityEvaluator] - and evaluates nothing itself. The verdict and
 * every check come from [CompatibilityEvaluator.evaluate], so this screen and the documentation
 * cannot disagree.
 */
@Composable
fun CompatibilityScreen(
    device: DeviceCapabilities,
    report: CompatibilityEvaluator.Report,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
        Text(
            text = stringResource(R.string.compat_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(16.dp))

        SectionHeader(stringResource(R.string.compat_group_result))
        Spacer(Modifier.height(8.dp))
        VerdictBanner(
            text = verdictText(report.result),
            state = CompatibilityPresentation.resultState(report.result),
        )
        Spacer(Modifier.height(4.dp))
        report.checks.forEach { check ->
            CheckRow(
                state = CompatibilityPresentation.checkState(check),
                title = check.name,
                detail = check.detail,
            )
        }

        Spacer(Modifier.height(20.dp))
        SectionHeader(stringResource(R.string.compat_group_app))
        Spacer(Modifier.height(8.dp))
        RequirementsGroup()

        Spacer(Modifier.height(20.dp))
        SectionHeader(stringResource(R.string.compat_group_device))
        Spacer(Modifier.height(8.dp))
        DeviceGroup(device)

        Spacer(Modifier.height(16.dp))
        NoteText(stringResource(R.string.compat_performance_note))
        Spacer(Modifier.height(8.dp))
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
    Column {
        FactRow("Android", "${AndroidRequirements.MIN_ANDROID_NAME} or newer.")
        FactRow("CPU", "${AndroidRequirements.SUPPORTED_ABI} (64-bit ARM) only.")
        FactRow(
            "Graphics",
            "${AndroidRequirements.REQUIRED_VULKAN_NAME} with " +
                "${CompatibilityPresentation.join(AndroidRequirements.REQUIRED_EXTENSIONS)} and the " +
                "features ${CompatibilityPresentation.join(AndroidRequirements.REQUIRED_FEATURES)}. " +
                "No OpenGL ES fallback.",
        )
        FactRow(
            "Optional Vulkan features",
            "Dropped if absent: " +
                CompatibilityPresentation.join(AndroidRequirements.OPTIONAL_FEATURES) + ".",
        )
        FactRow("GPUs", AndroidRequirements.GPU_FAMILIES)
        FactRow(
            "RAM",
            "${CompatibilityPresentation.gb(AndroidRequirements.MIN_RAM_BYTES)} GB recommended as a " +
                "floor, ${CompatibilityPresentation.gb(AndroidRequirements.RECOMMENDED_RAM_BYTES)} GB " +
                "recommended (provisional; no verified figure).",
        )
        FactRow("Storage", AndroidRequirements.STORAGE_NOTE)
        FactRow("Minimum processor", AndroidRequirements.MIN_SOC_PROVISIONAL)
        FactRow("Recommended processor", AndroidRequirements.RECOMMENDED_SOC_PROVISIONAL)
    }
}

/** What the platform reported about this device, with unverified values shown as such. */
@Composable
private fun DeviceGroup(device: DeviceCapabilities) {
    Column {
        FactRow("Manufacturer", CompatibilityPresentation.orUnknown(device.manufacturer))
        FactRow("Model", CompatibilityPresentation.orUnknown(device.model))
        FactRow(
            "Android version",
            (if (device.androidRelease.isNullOrEmpty()) "unknown" else device.androidRelease) +
                (if (device.sdkInt > 0) " (API ${device.sdkInt})" else ""),
        )
        FactRow(
            "CPU ABIs",
            device.supportedAbis?.takeIf { it.isNotEmpty() }?.let {
                CompatibilityPresentation.join(it)
            } ?: CompatibilityPresentation.orUnknown(null),
        )
        FactRow(
            "Total RAM",
            if (device.totalRamBytes > 0) {
                "${CompatibilityPresentation.gb(device.totalRamBytes)} GB"
            } else {
                "not reported"
            },
        )
        FactRow("Vulkan", vulkanSummary(device))
        NoteText(
            "Vulkan physical-device name, vendor/device IDs and per-extension/per-feature support: " +
                "unable to verify - the Android platform API does not expose them; the engine checks " +
                "the mandatory features when it creates the device.",
        )
    }
}

@Composable
private fun vulkanSummary(device: DeviceCapabilities): String = when (device.vulkanSupported) {
    DeviceCapabilities.FieldState.PRESENT -> buildString {
        append("Vulkan hardware reported.")
        if (device.vulkanHardwareVersion > 0) {
            append(" Advertised API version: ")
            append(AndroidRequirements.formatVulkanVersion(device.vulkanHardwareVersion))
            append('.')
        } else {
            append(" Advertised API version: not exposed by the platform API.")
        }
        if (device.vulkanHardwareLevel >= 0) {
            append(" Hardware level: ")
            append(device.vulkanHardwareLevel)
            append('.')
        }
    }
    DeviceCapabilities.FieldState.ABSENT -> "No Vulkan hardware reported."
    DeviceCapabilities.FieldState.UNKNOWN -> "Not reported."
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
