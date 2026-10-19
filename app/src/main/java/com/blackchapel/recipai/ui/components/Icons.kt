package com.blackchapel.recipai.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blackchapel.recipai.R

/** The line icons from the prototype, as vector drawables in res/drawable. */
enum class Ico(@DrawableRes val res: Int) {
    Arrow(R.drawable.ic_arrow),
    Bolt(R.drawable.ic_bolt),
    Book(R.drawable.ic_book),
    Calendar(R.drawable.ic_calendar),
    Camera(R.drawable.ic_camera),
    Check(R.drawable.ic_check),
    Chef(R.drawable.ic_chef),
    Clip(R.drawable.ic_clip),
    Clock(R.drawable.ic_clock),
    Cloud(R.drawable.ic_cloud),
    CloudUp(R.drawable.ic_cloud_up),
    Cmd(R.drawable.ic_cmd),
    Curve(R.drawable.ic_curve),
    Dots(R.drawable.ic_dots),
    Down(R.drawable.ic_down),
    Enter(R.drawable.ic_enter),
    Expand(R.drawable.ic_expand),
    Flame(R.drawable.ic_flame),
    Folder(R.drawable.ic_folder),
    Gear(R.drawable.ic_gear),
    Grid(R.drawable.ic_grid),
    Grip(R.drawable.ic_grip),
    Heading(R.drawable.ic_heading),
    Heart(R.drawable.ic_heart),
    HeartFill(R.drawable.ic_heart_fill),
    Image(R.drawable.ic_image),
    Info(R.drawable.ic_info),
    Left(R.drawable.ic_left),
    Link(R.drawable.ic_link),
    List(R.drawable.ic_list),
    Lock(R.drawable.ic_lock),
    Minus(R.drawable.ic_minus),
    Monitor(R.drawable.ic_monitor),
    Moon(R.drawable.ic_moon),
    Pause(R.drawable.ic_pause),
    Pencil(R.drawable.ic_pencil),
    Play(R.drawable.ic_play),
    Plus(R.drawable.ic_plus),
    Reset(R.drawable.ic_reset),
    Right(R.drawable.ic_right),
    Search(R.drawable.ic_search),
    Share(R.drawable.ic_share),
    Shield(R.drawable.ic_shield),
    Spark(R.drawable.ic_spark),
    Sun(R.drawable.ic_sun),
    Tag(R.drawable.ic_tag),
    Timer(R.drawable.ic_timer),
    Trash(R.drawable.ic_trash),
    Trend(R.drawable.ic_trend),
    Up(R.drawable.ic_up),
    Upload(R.drawable.ic_upload),
    Users(R.drawable.ic_users),
    Vibrate(R.drawable.ic_vibrate),
    Volume(R.drawable.ic_volume),
    X(R.drawable.ic_x),
}

/** A decorative icon; the button around it carries the label. */
@Composable
fun RIcon(ico: Ico, modifier: Modifier = Modifier, size: Dp = 20.dp, tint: Color = LocalContentColor.current) {
    Icon(painterResource(ico.res), contentDescription = null, modifier = modifier.size(size), tint = tint)
}
