package com.example.lmsnowplaying.composable

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

// Shuffle icons that the built-in icon set doesn't have, drawn after the
// Material skin's: "not shuffled" (two parallel arrows) and "shuffle
// releases" (crossed arrows with a record at the tail of one of them).

private fun ImageVector.Builder.path(d: String, fillType: PathFillType = PathFillType.NonZero): ImageVector.Builder =
    addPath(
        pathData = PathParser().parsePathString(d).toNodes(),
        pathFillType = fillType,
        fill = SolidColor(Color.Black),
    )

// The repeat loop with a diagonal slash through it: no repeat.
val RepeatOffIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "RepeatOff",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
        .path("M7,7h10v3l4,-4 -4,-4v3L5,5v6h2L7,7zM17,17L7,17v-3l-4,4 4,4v-3h12v-6h-2v4z")
        .path("M4.2,2.8L21.2,19.8 19.8,21.2 2.8,4.2z")
        .build()
}

// Two stacked arrows pointing right: songs play in order.
val ShuffleOffIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ShuffleOff",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
        .path("M16.01,6H4v2h12.01v3L20,7l-3.99,-4v3z")
        .path("M16.01,16H4v2h12.01v3L20,17l-3.99,-4v3z")
        .build()
}

// Crossed shuffle arrows, with a record (ring and dot) in place of the
// tail of the up-right arrow: whole releases are shuffled.
val ShuffleReleasesIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ShuffleReleases",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
        // arrow from top-left down to bottom-right
        .path("M10.59,9.17L5.41,4 4,5.41l5.17,5.17 1.42,-1.41zM14.83,13.41l-1.41,1.41 3.13,3.13L14.5,20L20,20v-5.5l-2.04,2.04 -3.13,-3.13z")
        // shortened arrow from the record up to top-right
        .path("M14.5,4l2.04,2.04L9.5,13.08 10.91,14.5 17.96,7.46 20,9.5L20,4h-5.5z")
        // the record: ring (even-odd) and centre dot
        .path("M7.9,13.1a3,3 0 1,0 0.01,0zM7.9,14.1a2,2 0 1,0 0.01,0z", PathFillType.EvenOdd)
        .path("M7.9,15.1a1,1 0 1,0 0.01,0z")
        .build()
}
