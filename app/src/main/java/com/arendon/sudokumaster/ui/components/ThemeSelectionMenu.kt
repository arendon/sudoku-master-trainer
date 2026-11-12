package com.arendon.sudokumaster.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.arendon.sudokumaster.ui.theme.ThemeMode

@Composable
fun ThemeSelectionButton(
    currentTheme: ThemeMode,
    onSelectTheme: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = Color.White
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            val icon = when (currentTheme) {
                ThemeMode.LIGHT -> Icons.Default.LightMode
                ThemeMode.DARK -> Icons.Default.DarkMode
                ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
            }
            Icon(
                imageVector = icon,
                contentDescription = "Change Theme (${currentTheme.title})",
                tint = iconTint
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Light") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.LightMode, contentDescription = null)
                },
                trailingIcon = {
                    if (currentTheme == ThemeMode.LIGHT) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = "Active")
                    }
                },
                onClick = {
                    expanded = false
                    onSelectTheme(ThemeMode.LIGHT)
                }
            )

            DropdownMenuItem(
                text = { Text("Dark") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.DarkMode, contentDescription = null)
                },
                trailingIcon = {
                    if (currentTheme == ThemeMode.DARK) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = "Active")
                    }
                },
                onClick = {
                    expanded = false
                    onSelectTheme(ThemeMode.DARK)
                }
            )

            DropdownMenuItem(
                text = { Text("System Default") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.BrightnessAuto, contentDescription = null)
                },
                trailingIcon = {
                    if (currentTheme == ThemeMode.SYSTEM) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = "Active")
                    }
                },
                onClick = {
                    expanded = false
                    onSelectTheme(ThemeMode.SYSTEM)
                }
            )
        }
    }
}
