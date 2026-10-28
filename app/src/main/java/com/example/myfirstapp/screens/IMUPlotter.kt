package com.example.myfirstapp.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.myfirstapp.model.OrientationDataPoint

@Composable
fun IMUPlotter(
    data: List<OrientationDataPoint>,
    modifier: Modifier = Modifier
) {

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
    ) {

        if (data.size < 2) return@Canvas


        val maxValue = 1f
        val minValue = -1f


        fun drawSeries(
            values: List<Float>,
            color: Color
        ) {

            val path = Path()

            values.forEachIndexed { index, value ->

                val x =
                    index.toFloat() /
                            (values.size - 1) *
                            size.width


                val y =
                    size.height -
                            ((value - minValue) /
                                    (maxValue - minValue) *
                                    size.height)


                if (index == 0)
                    path.moveTo(x, y)
                else
                    path.lineTo(x, y)
            }


            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 3f)
            )
        }

        drawSeries(
            data.map { it.q0 },
            Color.Yellow
        )

        drawSeries(
            data.map { it.q1 },
            Color.Red
        )

        drawSeries(
            data.map { it.q2 },
            Color.Green
        )

        drawSeries(
            data.map { it.q3},
            Color.Cyan
        )
    }
}