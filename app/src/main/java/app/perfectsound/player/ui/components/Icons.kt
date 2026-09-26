package app.perfectsound.player.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope

/** Transport icons, drawn to fill the canvas they are given. */
object Icons {
    private fun DrawScope.triangle(left: Float, right: Float, color: Color) {
        drawPath(Path().apply {
            moveTo(left, 0f); lineTo(right, size.height / 2); lineTo(left, size.height); close()
        }, color)
    }

    private fun DrawScope.triangleLeft(left: Float, right: Float, color: Color) {
        drawPath(Path().apply {
            moveTo(right, 0f); lineTo(left, size.height / 2); lineTo(right, size.height); close()
        }, color)
    }

    val Play: DrawScope.(Color) -> Unit = { c -> triangle(size.width * 0.15f, size.width * 0.9f, c) }

    val Pause: DrawScope.(Color) -> Unit = { c ->
        val w = size.width * 0.28f
        drawRect(c, Offset(size.width * 0.12f, 0f), Size(w, size.height))
        drawRect(c, Offset(size.width * 0.88f - w, 0f), Size(w, size.height))
    }

    val Stop: DrawScope.(Color) -> Unit = { c ->
        val s = minOf(size.width, size.height)
        drawRect(c, Offset((size.width - s) / 2, (size.height - s) / 2), Size(s, s))
    }

    val Previous: DrawScope.(Color) -> Unit = { c ->
        drawRect(c, Offset(0f, 0f), Size(size.width * 0.12f, size.height))
        triangleLeft(size.width * 0.12f, size.width * 0.56f, c)
        triangleLeft(size.width * 0.56f, size.width, c)
    }

    val Next: DrawScope.(Color) -> Unit = { c ->
        triangle(0f, size.width * 0.44f, c)
        triangle(size.width * 0.44f, size.width * 0.88f, c)
        drawRect(c, Offset(size.width * 0.88f, 0f), Size(size.width * 0.12f, size.height))
    }

    val Eject: DrawScope.(Color) -> Unit = { c ->
        drawPath(Path().apply {
            moveTo(size.width / 2, 0f); lineTo(size.width, size.height * 0.62f); lineTo(0f, size.height * 0.62f); close()
        }, c)
        drawRect(c, Offset(0f, size.height * 0.78f), Size(size.width, size.height * 0.22f))
    }
}
