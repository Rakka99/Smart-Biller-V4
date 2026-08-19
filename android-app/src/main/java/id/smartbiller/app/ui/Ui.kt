package id.smartbiller.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import id.smartbiller.app.ui.theme.Blue800
import id.smartbiller.app.ui.theme.Blue900

@Composable
fun GlassCard(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit){
    Column(modifier.background(Brush.linearGradient(listOf(Color.White.copy(.18f),Color.White.copy(.06f))),RoundedCornerShape(24.dp)).border(1.dp,Color.White.copy(.22f),RoundedCornerShape(24.dp)).padding(16.dp),content=content)
}

@Composable
fun AppBackground(content:@Composable BoxScope.()->Unit){
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Blue900,Blue800,Color(0xFF0A6FCC)))),content=content)
}
