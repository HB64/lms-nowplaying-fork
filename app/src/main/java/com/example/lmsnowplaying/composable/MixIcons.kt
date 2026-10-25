package com.example.lmsnowplaying.composable

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.dp

// The Auto Mix plugin icons (from the SVG files in the Icons folder).

private fun ImageVector.Builder.fillPath(d: String): ImageVector.Builder =
    addPath(
        pathData = PathParser().parsePathString(d).toNodes(),
        fill = SolidColor(Color.Black),
    )

private fun newIcon(name: String) = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
)

val MipsterIcon: ImageVector by lazy {
    newIcon("Mipster")
        .fillPath("M11.95 1.55L21.05 1.55A0.94 0.94 0 0 1 21.98 2.48L21.98 4.12A0.94 0.94 0 0 1 21.05 5.06L11.95 5.06A0.94 0.94 0 0 1 11.02 4.12L11.02 2.48A0.94 0.94 0 0 1 11.95 1.55zM11.02 7.27Q11.02 6.42 11.95 6.42L14.62 6.42zM16.73 6.42L21.05 6.42A0.94 0.94 0 0 1 21.98 7.36L21.98 9.05A0.94 0.94 0 0 1 21.05 9.98L16.73 9.98zM11.95 11.3L13.64 11.3L13.64 14.86L11.95 14.86A0.94 0.94 0 0 1 11.02 13.92L11.02 12.23A0.94 0.94 0 0 1 11.95 11.3zM16.73 11.3L21.05 11.3A0.94 0.94 0 0 1 21.98 12.23L21.98 13.92A0.94 0.94 0 0 1 21.05 14.86L16.73 14.86zM6.23 8.77L16.08 7.03L16.08 19.22L14.3 19.22L14.3 9.42L8.02 10.31L8.02 20.53L6.23 20.53zM2.16 21.46A2.44 1.69 -20 1 1 6.75 19.79A2.44 1.69 -20 1 1 2.16 21.46zM10.27 20.1A2.44 1.69 -20 1 1 14.85 18.43A2.44 1.69 -20 1 1 10.27 20.1z")
        .build()
}

val SugarCubeIcon: ImageVector by lazy {
    val b = newIcon("SugarCube")
    b.group(rotate = 90f, pivotX = 12f, pivotY = 12f) {
        fillPath("m12 10.11c1.03 0 1.87 0.84 1.87 1.89 0 1-0.84 1.85-1.87 1.85s-1.87-0.85-1.87-1.85c0-1.05 0.84-1.89 1.87-1.89m-4.63 9.89c0.63 0.38 2.01-0.2 3.6-1.7-0.52-0.59-1.03-1.23-1.51-1.9-0.82-0.08-1.63-0.2-2.4-0.36-0.51 2.14-0.32 3.61 0.31 3.96m0.71-5.74-0.29-0.51c-0.11 0.29-0.22 0.58-0.29 0.86 0.27 0.06 0.57 0.11 0.88 0.16l-0.3-0.51m6.54-0.76 0.81-1.5-0.81-1.5c-0.3-0.53-0.62-1-0.91-1.47-0.54-0.03-1.11-0.03-1.71-0.03s-1.17 0-1.71 0.03c-0.29 0.47-0.61 0.94-0.91 1.47l-0.81 1.5 0.81 1.5c0.3 0.53 0.62 1 0.91 1.47 0.54 0.03 1.11 0.03 1.71 0.03s1.17 0 1.71-0.03c0.29-0.47 0.61-0.94 0.91-1.47m-2.62-6.72c-0.19 0.22-0.39 0.45-0.59 0.72h0.59 0.59c-0.2-0.27-0.4-0.5-0.59-0.72m0 10.44c0.19-0.22 0.39-0.45 0.59-0.72h-0.59-0.59c0.2 0.27 0.4 0.5 0.59 0.72m4.62-13.22c-0.62-0.38-2 0.2-3.59 1.7 0.52 0.59 1.03 1.23 1.51 1.9 0.82 0.08 1.63 0.2 2.4 0.36 0.51-2.14 0.32-3.61-0.32-3.96m-0.7 5.74 0.29 0.51c0.11-0.29 0.22-0.58 0.29-0.86-0.27-0.06-0.57-0.11-0.88-0.16l0.3 0.51m1.45-7.05c1.47 0.84 1.63 3.05 1.01 5.63 2.54 0.75 4.37 1.99 4.37 3.68s-1.83 2.93-4.37 3.68c0.62 2.58 0.46 4.79-1.01 5.63-1.46 0.84-3.45-0.12-5.37-1.95-1.92 1.83-3.91 2.79-5.38 1.95-1.46-0.84-1.62-3.05-1-5.63-2.54-0.75-4.37-1.99-4.37-3.68s1.83-2.93 4.37-3.68c-0.62-2.58-0.46-4.79 1-5.63 1.47-0.84 3.46 0.12 5.38 1.95 1.92-1.83 3.91-2.79 5.37-1.95m-0.29 9.31c0.34 0.75 0.64 1.5 0.89 2.26 2.1-0.63 3.28-1.53 3.28-2.26s-1.18-1.63-3.28-2.26c-0.25 0.76-0.55 1.51-0.89 2.26m-10.16 0c-0.34-0.75-0.64-1.5-0.89-2.26-2.1 0.63-3.28 1.53-3.28 2.26s1.18 1.63 3.28 2.26c0.25-0.76 0.55-1.51 0.89-2.26m9 2.26-0.3 0.51c0.31-0.05 0.61-0.1 0.88-0.16-0.07-0.28-0.18-0.57-0.29-0.86l-0.29 0.51m-2.89 4.04c1.59 1.5 2.97 2.08 3.59 1.7 0.64-0.35 0.83-1.82 0.32-3.96-0.77 0.16-1.58 0.28-2.4 0.36-0.48 0.67-0.99 1.31-1.51 1.9m-4.95-8.56 0.3-0.51c-0.31 0.05-0.61 0.1-0.88 0.16 0.07 0.28 0.18 0.57 0.29 0.86l0.29-0.51m2.89-4.04c-1.59-1.5-2.97-2.08-3.6-1.7-0.63 0.35-0.82 1.82-0.31 3.96 0.77-0.16 1.58-0.28 2.4-0.36 0.48-0.67 0.99-1.31 1.51-1.9z")
    }
    b.build()
}

val RandomFlowIcon: ImageVector by lazy {
    val b = newIcon("RandomFlow")
    b.group(
        scaleX = 0.07067f,
        scaleY = 0.07067f,
        translationX = -5.104f,
        translationY = -4.784f,
    ) {
        fillPath("M154,96 H366 A14,14 0 0 1 380,110 A14,14 0 0 1 366,124 H154 A14,14 0 0 1 140,110 A14,14 0 0 1 154,96 Z")
        fillPath("M330,190 H366 A14,14 0 0 1 380,204 A14,14 0 0 1 366,218 H330 A14,14 0 0 1 316,204 A14,14 0 0 1 330,190 Z")
        fillPath("M330,262 H366 A14,14 0 0 1 380,276 A14,14 0 0 1 366,290 H330 A14,14 0 0 1 316,276 A14,14 0 0 1 330,262 Z")
        fillPath("M330,334 H366 A14,14 0 0 1 380,348 A14,14 0 0 1 366,362 H330 A14,14 0 0 1 316,348 A14,14 0 0 1 330,334 Z")
        for (d in listOf("M118,204 C190,204 186,348 246,348", "M118,348 C190,348 186,204 246,204")) {
            addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 28f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        for (d in listOf("M246,178 L298,204 L246,230 Z", "M246,322 L298,348 L246,374 Z")) {
            addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = SolidColor(Color.Black),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 10f,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }
    b.build()
}
