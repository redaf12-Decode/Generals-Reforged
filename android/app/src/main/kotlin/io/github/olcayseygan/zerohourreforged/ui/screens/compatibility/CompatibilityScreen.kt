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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.olcayseygan.zerohourreforged.AndroidRequirements
import io.github.olcayseygan.zerohourreforged.CompatibilityEvaluator
import io.github.olcayseygan.zerohourreforged.DeviceCapabilities
import io.github.olcayseygan.zerohourreforged.R
import io.github.olcayseygan.zerohourreforged.ui.components.ApplicationTopBar
import io.github.olcayseygan.zerohourreforged.ui.components.CheckRow
import io.github.olcayseygan.zerohourreforged.ui.components.CompatibilityCheckList
import io.github.olcayseygan.zerohourreforged.ui.components.CompatibilitySummaryCard
import io.github.olcayseygan.zerohourreforged.ui.components.InformationRow
import io.github.olcayseygan.zerohourreforged.ui.components.SectionContainer
import io.github.olcayseygan.zerohourreforged.ui.components.StatusChip
import io.github.olcayseygan.zerohourreforged.ui.theme.ZeroHourTheme
import io.github.olcayseygan.zerohourreforged.ui.theme.spacing

/** Matches the launcher: a reading column, so a tablet does not stretch a paragraph. */
private val CONTENT_MAX_WIDTH = 640.dp

/**
 * The System Requirements & Device Compatibility screen.
 *
 * <p>It renders the three pure classes it is given - [AndroidRequirements],
 * [DeviceCapabilities] and [CompatibilityEvaluator] - and evaluates nothing itself. The verdict and
 * every check come from [CompatibilityEvaluator.evaluate], so this screen and the documentation
 * cannot disagree.
 *
 * <p>Five sections, in the order a player reads them: the verdict, the checks behind it, then the
 * application's requirements, what the platform reported, and finally what could not be
 * established. Putting the unverified facts last and in their own section is deliberate - they are
 * the part a reader is most likely to mistake for a footnote, and a named section is harder to skim
 * past than a footnote.
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = CONTENT_MAX_WIDTH)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = MaterialTheme.spacing.screenEdge,
                        vertical = MaterialTheme.spacing.screenVertical,
                    ),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sectionGap),
            ) {
                CompatibilitySummaryCard(
                    tone = CompatibilityPresentation.resultTone(report.result),
                    headline = verdictText(report.result),
                    summary = stringResource(R.string.compat_result_summary),
                )

                SectionContainer(title = stringResource(R.string.compat_group_checks)) {
                    CompatibilityCheckList(rows = report.checks.map { it.toRow() })
                }

                SectionContainer(title = stringResource(R.string.compat_group_app)) {
                    RequirementsGroup()
                }

                SectionContainer(title = stringResource(R.string.compat_group_device)) {
                    DeviceGroup(device)
                }

                SectionContainer(title = stringResource(R.string.compat_group_unverified)) {
                    UnverifiedGroup()
                }

                Text(
                    text = stringResource(R.string.compat_performance_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * One evaluator check as a row, using the evaluator's own strings.
 *
 * <p>Neither the name nor the detail is rewritten here: both come from
 * [CompatibilityEvaluator.Check], which is the only place that knows why a check came out as it did.
 * This only chooses the word for its state.
 */
@Composable
private fun CompatibilityEvaluator.Check.toRow(): CheckRow = CheckRow(
    tone = CompatibilityPresentation.checkTone(this),
    stateLabel = CompatibilityPresentation.checkEvidence(this).label(),
    title = name,
    detail = detail,
)

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
        // The Vulkan floor, its extensions and its features are three rows, not one sentence: a
        // reader checking whether their device meets the requirement looks for the requirement's
        // name, and the extension list is what they compare against a driver report.
        InformationRow(
            label = stringResource(R.string.compat_label_graphics),
            value = stringResource(
                R.string.compat_value_vulkan_floor,
                AndroidRequirements.REQUIRED_VULKAN_NAME,
            ),
            stacked = true,
        )
        InformationRow(
            label = stringResource(R.string.compat_label_vulkan_extensions),
            value = CompatibilityPresentation.join(AndroidRequirements.REQUIRED_EXTENSIONS),
            stacked = true,
        )
        InformationRow(
            label = stringResource(R.string.compat_label_vulkan_features),
            value = CompatibilityPresentation.join(AndroidRequirements.REQUIRED_FEATURES),
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

/**
 * What the platform reported about this device.
 *
 * <p>A field the platform left empty reads "Not reported", never "unknown" or an em dash alone: the
 * difference between "this app cannot know" and "this device did not say" is the whole subject of
 * this screen, so the words are chosen per field rather than by a shared fallback.
 */
@Composable
private fun DeviceGroup(device: DeviceCapabilities) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        InformationRow(
            label = stringResource(R.string.compat_label_manufacturer),
            value = CompatibilityPresentation.orNull(device.manufacturer)
                ?: stringResource(R.string.compat_value_not_reported),
        )
        InformationRow(
            label = stringResource(R.string.compat_label_model),
            value = CompatibilityPresentation.orNull(device.model)
                ?: stringResource(R.string.compat_value_not_reported),
        )
        InformationRow(
            label = stringResource(R.string.compat_label_android_version),
            value = CompatibilityPresentation.orNull(device.androidRelease)
                ?: stringResource(R.string.compat_value_not_reported),
        )
        InformationRow(
            label = stringResource(R.string.compat_label_api_level),
            value = if (device.sdkInt > 0) {
                device.sdkInt.toString()
            } else {
                stringResource(R.string.compat_value_not_reported)
            },
        )
        InformationRow(
            label = stringResource(R.string.compat_label_abis),
            value = device.supportedAbis?.takeIf { it.isNotEmpty() }?.let {
                CompatibilityPresentation.join(it)
            } ?: stringResource(R.string.compat_value_not_reported),
            stacked = true,
        )
        InformationRow(
            label = stringResource(R.string.compat_label_total_ram),
            value = if (CompatibilityPresentation.ramEvidence(device) ==
                CompatibilityPresentation.Evidence.VERIFIED
            ) {
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
    }
}

/**
 * What could not be established, and why - the honesty section.
 *
 * <p>Every line names a fact, says it is not available, and says who can establish it instead. None
 * of it is inferred from the device's name: the GPU model in particular is never guessed from a
 * manufacturer or a SoC family, because a brand says nothing about which driver the device shipped
 * with or which extensions that driver advertises.
 */
@Composable
private fun UnverifiedGroup() {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large)) {
        UnverifiedNote(
            title = stringResource(R.string.compat_label_gpu_model),
            detail = stringResource(R.string.compat_unverified_gpu),
        )
        UnverifiedNote(
            title = stringResource(R.string.compat_label_vulkan_extensions),
            detail = stringResource(R.string.compat_unverified_extensions),
        )
        UnverifiedNote(
            title = stringResource(R.string.compat_label_vulkan_features),
            detail = stringResource(R.string.compat_unverified_features),
        )
        UnverifiedNote(
            title = stringResource(R.string.compat_label_vulkan_ids),
            detail = stringResource(R.string.compat_unverified_ids),
        )
    }
}

/** One "not available" statement: what cannot be known, then which component could know it. */
@Composable
private fun UnverifiedNote(title: String, detail: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.tight),
    ) {
        StatusChip(
            tone = CompatibilityPresentation.evidenceTone(EVIDENCE_NOT_AVAILABLE),
            label = EVIDENCE_NOT_AVAILABLE.label(),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val EVIDENCE_NOT_AVAILABLE = CompatibilityPresentation.Evidence.NOT_AVAILABLE

/**
 * What the platform reports about Vulkan, stated with its own gaps.
 *
 * <p>Three answers that must not be merged: present (with the version and level it reported, or
 * "not exposed" where it did not), absent (a reported and final no), and not reported (the platform
 * would not answer). None of them is a statement about whether the device is compatible - that is
 * [CompatibilityEvaluator]'s question, and is answered in the checks section.
 */
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
        append(' ')
        if (device.vulkanHardwareLevel >= 0) {
            append(stringResource(R.string.compat_vulkan_level, device.vulkanHardwareLevel))
        } else {
            append(stringResource(R.string.compat_vulkan_level_not_reported))
        }
    }
    DeviceCapabilities.FieldState.ABSENT -> stringResource(R.string.compat_vulkan_absent)
    DeviceCapabilities.FieldState.UNKNOWN -> stringResource(R.string.compat_value_not_reported)
}

/** A device that reports everything, the case the layout is tuned for. */
private fun reportingDevice(): DeviceCapabilities = DeviceCapabilities().apply {
    manufacturer = "Google"
    model = "Pixel 8"
    androidRelease = "15"
    sdkInt = 35
    supportedAbis = arrayOf("arm64-v8a")
    totalRamBytes = 8L * 1024 * 1024 * 1024
    vulkanSupported = DeviceCapabilities.FieldState.PRESENT
    vulkanHardwareVersion = AndroidRequirements.packVulkanVersion(1, 3, 0)
    vulkanHardwareLevel = 1
}

@Preview(name = "Partially compatible", showBackground = true)
@Composable
private fun CompatibilityScreenPreview() {
    val device = reportingDevice()
    ZeroHourTheme {
        CompatibilityScreen(device = device, report = CompatibilityEvaluator.evaluate(device))
    }
}

/** The worst realistic case: a platform that answered nothing. */
@Preview(name = "Unable to verify", showBackground = true)
@Composable
private fun CompatibilityScreenUnverifiablePreview() {
    val device = DeviceCapabilities()
    ZeroHourTheme {
        CompatibilityScreen(device = device, report = CompatibilityEvaluator.evaluate(device))
    }
}

/** A 32-bit device on an old release: a hard failure. */
@Preview(name = "Unsupported", showBackground = true)
@Composable
private fun CompatibilityScreenUnsupportedPreview() {
    val device = reportingDevice().apply {
        supportedAbis = arrayOf("armeabi-v7a", "armeabi")
        sdkInt = 26
        androidRelease = "8.0"
    }
    ZeroHourTheme {
        CompatibilityScreen(device = device, report = CompatibilityEvaluator.evaluate(device))
    }
}

