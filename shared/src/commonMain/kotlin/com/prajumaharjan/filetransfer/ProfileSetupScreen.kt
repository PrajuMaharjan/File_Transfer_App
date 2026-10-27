package com.prajumaharjan.filetransfer

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ProfileSetupScreen(
    initial: Profile?,
    onDone: (Profile) -> Unit,
    onCancel: (() -> Unit)? = null
){
    var name by remember{ mutableStateOf(initial?.name ?: "") }
    var color by remember{ mutableStateOf(initial?.avatar ?: AvatarColor.BLUE) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ){
        Text(
            if(initial == null) "Create your profile" else "Edit profile",
            style = MaterialTheme.typography.headlineSmall
        )
        Avatar(name,color,96.dp)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(Profile.MAX_NAME) },
            label = { Text("Display name") },
            singleLine = true
        )
        AvatarColor.entries.chunked(5).forEach { rowColors ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)){
                rowColors.forEach { c ->
                    val selected = c == color
                    Avatar(
                        name = name,
                        color = c,
                        size = 44.dp,
                        modifier = Modifier.border(3.dp, if(selected) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                                            .clickable{ color = c}
                    )
                }
            }
        }
        Button( enabled = name.isNotBlank(),
                onClick = {
                    val p = initial?.copy(name = Profile.cleanName(name), avatar = color)
                        ?: Profile.create(name, color)
                    onDone(p)
                }
        ){ Text("Save") }
        if(onCancel != null){
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}