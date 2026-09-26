package id.web.izs.nettools.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.web.izs.nettools.R
import kotlin.math.roundToInt

private val CuratedColors = listOf(
    "#000000", "#0D1117", "#141A24", "#202327", "#21262D", "#2B2D31",
    "#383A40", "#424242", "#757575", "#BDBDBD", "#E0E0E0", "#FFFFFF",
    "#B71C1C", "#E53935", "#EF9A9A", "#E65100", "#FB8C00", "#FFCC80",
    "#F9A825", "#FDD835", "#FFF59D", "#33691E", "#66BB6A", "#A5D6A7",
    "#00695C", "#4DB6AC", "#01579B", "#64B5F6", "#90CAF9",
    "#4527A0", "#9575CD", "#AD1457", "#F48FB1", "#5D4037", "#BCAAA4"
)

private fun roleColor(
    role: String,
    theme: String,
    scheme: androidx.compose.material3.ColorScheme,
    overrides: Map<String, String>
): Color = when (role) {
    "background" -> scheme.background
    "surface" -> scheme.surface
    "surfaceContainer" -> scheme.surfaceContainer
    "surfaceContainerHighest" -> scheme.surfaceContainerHighest
    "primary" -> scheme.primary
    "onPrimary" -> scheme.onPrimary
    "primaryContainer" -> scheme.primaryContainer
    "onPrimaryContainer" -> scheme.onPrimaryContainer
    "terminal" -> overrides["terminal"]?.let { hexToColor(it) } ?: defaultTerminalBg(theme, scheme)
    else -> scheme.surface
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ColorsScreen(vm: NetToolsViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val settings = state.settings
    val overrides = settings.customColors
    val scheme = remember(settings.theme, overrides) {
        baseScheme(settings.theme).withOverrides(overrides)
    }
    var editingRole by remember { mutableStateOf<Pair<String, String>?>(null) }
    var name by remember(settings.schemeName) { mutableStateOf(settings.schemeName) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.colors_title)) },
                navigationIcon = {
                    IconButton(onClick = {
                        focusManager.clearFocus()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(12.dp)
                .verticalScroll(rememberScrollState())
                .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) },
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.colors_overrides_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Live preview strip in the effective colors.
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier.weight(1f).height(64.dp)
                        .clip(RoundedCornerShape(12.dp)).background(scheme.background),
                    contentAlignment = Alignment.Center
                ) { Text("Aa", color = scheme.primary, fontWeight = FontWeight.Bold) }
                Box(
                    modifier = Modifier.weight(1f).height(64.dp)
                        .clip(RoundedCornerShape(12.dp)).background(scheme.surfaceContainer),
                    contentAlignment = Alignment.Center
                ) { Text("Aa", color = scheme.onSurface, fontWeight = FontWeight.Bold) }
                Box(
                    modifier = Modifier.weight(1f).height(64.dp)
                        .clip(RoundedCornerShape(12.dp)).background(scheme.primary),
                    contentAlignment = Alignment.Center
                ) { Text("Aa", color = scheme.onPrimary, fontWeight = FontWeight.Bold) }
            }
            // Named scheme: edit the name, Save stores it. Same name = overwrite, new name = new scheme.
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.colors_scheme_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        focusManager.clearFocus()
                        vm.saveScheme(name)
                    }),
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        focusManager.clearFocus()
                        vm.saveScheme(name)
                    },
                    enabled = name.isNotBlank()
                ) {
                    Icon(Icons.Filled.Save, contentDescription = stringResource(R.string.colors_save_scheme))
                }
            }
            if (settings.colorSchemes.isNotEmpty()) {
                Text(
                    stringResource(R.string.colors_saved_schemes, settings.colorSchemes.size),
                    style = MaterialTheme.typography.titleMedium
                )
                settings.colorSchemes.keys.sorted().forEach { saved ->
                    val active = saved == settings.schemeName
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { vm.applyScheme(saved) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            saved,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (active) FontWeight.Bold else null,
                            color = if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { vm.deleteScheme(saved) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.colors_delete_scheme, saved),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.colors_sections, CustomColorRoles.size),
                style = MaterialTheme.typography.titleMedium
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                CustomColorRoles.forEach { (key, label) ->
                    val fill = roleColor(key, settings.theme, scheme, overrides)
                    val custom = overrides.containsKey(key)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { editingRole = key to label }
                    ) {
                        Box(
                            modifier = Modifier.size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(fill)
                                .then(
                                    if (custom) Modifier.border(
                                        2.dp,
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(12.dp)
                                    ) else Modifier
                                )
                        )
                        Text(
                            label.split(" ").first(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.colors_tap_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = { vm.resetCustomColors() },
                enabled = overrides.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.colors_reset_all)) }
        }
    }

    editingRole?.let { (key, label) ->
        val customized = overrides.containsKey(key)
        AlertDialog(
            onDismissRequest = { editingRole = null },
            title = { Text(label) },
            text = {
                ColorPickerContent(
                    currentHex = overrides[key] ?: colorToHex(roleColor(key, settings.theme, scheme, overrides)),
                    onPick = { vm.setCustomColor(key, it) }
                )
            },
            confirmButton = {
                TextButton(onClick = { editingRole = null }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = if (customized) {
                {
                    TextButton(onClick = {
                        vm.clearCustomColor(key)
                        editingRole = null
                    }) { Text(stringResource(R.string.defaults)) }
                }
            } else null
        )
    }
}

/** Preset swatch grid (tap = apply, dialog stays open) + manual hex field (live apply).
 *  Tapping the preview box opens the full HSV picker in a new dialog. */
@Composable
private fun ColorPickerContent(
    currentHex: String,
    onPick: (String) -> Unit
) {
    var hex by remember(currentHex) { mutableStateOf(currentHex) }
    var hexError by remember { mutableStateOf(false) }
    var fullPicker by remember { mutableStateOf(false) }
    fun pick(h: String) {
        hex = h
        hexError = false
        onPick(h)
    }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val preview = hexToColor(hex)
            // Tappable preview: opens the full color picker. The badge icon
            // signals that this is a button, not a static swatch.
            Box(
                modifier = Modifier.size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(preview ?: MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { preview?.let { fullPicker = true } },
                contentAlignment = Alignment.BottomEnd
            ) {
                Icon(
                    Icons.Filled.Palette,
                    contentDescription = stringResource(R.string.colors_open_picker),
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(20.dp).padding(2.dp)
                )
            }
            Text(
                if (hexError) stringResource(R.string.colors_invalid_hex) else hex.uppercase(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            CuratedColors.forEach { c ->
                val col = hexToColor(c) ?: return@forEach
                Box(
                    modifier = Modifier.size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(col)
                        .clickable {
                            val norm = colorToHex(col)
                            pick(norm)
                        }
                )
            }
        }
        OutlinedTextField(
            value = hex,
            onValueChange = { t ->
                hex = t
                val norm = normalizeHex(t)
                hexError = norm == null
                if (norm != null) onPick(norm)
            },
            label = { Text(stringResource(R.string.colors_hex_label)) },
            singleLine = true,
            isError = hexError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { hexToColor(hex)?.let { onPick(colorToHex(it)) } }),
            modifier = Modifier.fillMaxWidth()
        )
    }
    if (fullPicker) {
        FullColorPickerDialog(
            currentHex = hex,
            onPick = ::pick,
            onDismiss = { fullPicker = false }
        )
    }
}

/** Full color picker in its own dialog: Hue/Saturation/Brightness sliders
 *  with a live preview. Sliding only updates the local preview (cheap);
 *  the setting applies once on Done, so dragging stays smooth. Dismissing
 *  without Done discards the change. */
@Composable
private fun FullColorPickerDialog(
    currentHex: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // Seed sliders from the current color (android HSV; Compose has no direct getter).
    val seed = remember(currentHex) {
        val argb = normalizeHex(currentHex)?.removePrefix("#")?.toLong(16)?.toInt()
            ?: android.graphics.Color.BLACK
        FloatArray(3).also { android.graphics.Color.colorToHSV(argb, it) }
    }
    var h by remember(seed) { mutableFloatStateOf(seed[0]) }
    var s by remember(seed) { mutableFloatStateOf(seed[1]) }
    var v by remember(seed) { mutableFloatStateOf(seed[2]) }
    val preview = Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.colors_pick_color)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(preview)
                )
                Text(
                    colorToHex(preview).uppercase(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HsvSlider(R.string.colors_hue, "${h.roundToInt()}°", h, 0f, 360f) { h = it }
                HsvSlider(R.string.colors_saturation, "${(s * 100).roundToInt()}%", s, 0f, 1f) { s = it }
                HsvSlider(R.string.colors_brightness, "${(v * 100).roundToInt()}%", v, 0f, 1f) { v = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onPick(colorToHex(preview))
                onDismiss()
            }) { Text(stringResource(R.string.done)) }
        }
    )
}

@Composable
private fun HsvSlider(
    labelRes: Int,
    valueText: String,
    value: Float,
    min: Float,
    max: Float,
    onChange: (Float) -> Unit
) {
    Column {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(labelRes), style = MaterialTheme.typography.bodySmall)
            Text(valueText, style = MaterialTheme.typography.bodySmall)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = min..max,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
