package com.prajumaharjan.filetransfer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Avatar(name: String,color: AvatarColor,size:Dp=40.dp,modifier: Modifier= Modifier){
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(Color(color.argb)),
        contentAlignment = Alignment.Center
    ){
        Text(
            text = name.trim().firstOrNull()?.uppercase() ?: "?",
            color = Color.White,
            fontSize = (size.value * 0.45f).sp
        )
    }
}