package com.blackchapel.recipai.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class Ico {
    Arrow,
    Bolt,
    Book,
    Calendar,
    Camera,
    Check,
    Chef,
    Clip,
    Clock,
    Cloud,
    CloudUp,
    Cmd,
    Curve,
    Dots,
    Down,
    Enter,
    Expand,
    Flame,
    Folder,
    Gear,
    Grid,
    Grip,
    Heading,
    Heart,
    HeartFill,
    Image,
    Info,
    Left,
    Link,
    List,
    Lock,
    Minus,
    Monitor,
    Moon,
    Pause,
    Pencil,
    Play,
    Plus,
    Reset,
    Right,
    Search,
    Share,
    Shield,
    Spark,
    Sun,
    Tag,
    Timer,
    Trash,
    Trend,
    Up,
    Upload,
    Users,
    Vibrate,
    Volume,
    X,
}

@Composable
fun RIcon(ico: Ico, modifier: Modifier = Modifier, size: Dp = 20.dp, tint: Color = LocalContentColor.current) {
    Box(modifier.size(size))
}
