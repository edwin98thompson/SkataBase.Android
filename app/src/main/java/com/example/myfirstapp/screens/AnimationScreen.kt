package com.example.myfirstapp.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myfirstapp.viewmodel.OrientationViewModel
import dev.romainguy.kotlin.math.Quaternion
import io.github.sceneview.Scene
import io.github.sceneview.math.Position
import io.github.sceneview.math.Scale
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader

@Composable
fun AnimationScreen(
    viewModel: OrientationViewModel
) {
    val orientation by viewModel.orientation.collectAsState()

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)

    var modelNode by remember {
        mutableStateOf<ModelNode?>(null)
    }

    // Calibration offsets
    var rollOffset by remember { mutableFloatStateOf(0f) }
    var pitchOffset by remember { mutableFloatStateOf(0f) }
    var yawOffset by remember { mutableFloatStateOf(0f) }


    val correctionQuaternion = remember(
        rollOffset,
        pitchOffset,
        yawOffset
    ) {
        Quaternion.fromEuler(
            pitch = Math.toRadians(pitchOffset.toDouble()).toFloat(),
            yaw = Math.toRadians(yawOffset.toDouble()).toFloat(),
            roll = Math.toRadians(rollOffset.toDouble()).toFloat(),
        )
    }


    Box(
        modifier = Modifier.fillMaxSize()
    ) {

        Scene(
            modifier = Modifier.fillMaxSize(),

            engine = engine,

            modelLoader = modelLoader,

            childNodes = listOfNotNull(modelNode),

            cameraManipulator = null,

            onViewCreated = {

                modelNode = ModelNode(
                    modelInstance = modelLoader.createModelInstance(
                        assetFileLocation = "models/skateboard.glb"
                    )
                ).apply {

                    scale = Scale(0.2f)
                    position = Position(0f, 0f, -2f)
                }
            }
        )


        // Offset controls
//        Column(
//            modifier = Modifier
//                .align(Alignment.BottomCenter)
//                .padding(16.dp),
//            horizontalAlignment = Alignment.CenterHorizontally
//        ) {
//
//            OffsetControl(
//                label = "Roll",
//                value = rollOffset,
//                onIncrease = { rollOffset += 5f },
//                onDecrease = { rollOffset -= 5f }
//            )
//
//            OffsetControl(
//                label = "Pitch",
//                value = pitchOffset,
//                onIncrease = { pitchOffset += 5f },
//                onDecrease = { pitchOffset -= 5f }
//            )
//
//            OffsetControl(
//                label = "Yaw",
//                value = yawOffset,
//                onIncrease = { yawOffset += 5f },
//                onDecrease = { yawOffset -= 5f }
//            )
//
//            Button(
//                onClick = {
//                    rollOffset = 0f
//                    pitchOffset = 0f
//                    yawOffset = 0f
//                }
//            ) {
//                Text("Reset")
//            }
//        }

    }


    LaunchedEffect(orientation) {

        orientation.let {

            val imuQuaternion = Quaternion(
                it.q1,
                it.q2,
                it.q3,
                it.q0
            )

            modelNode?.quaternion =
                correctionQuaternion * imuQuaternion
        }
    }
}


@Composable
fun OffsetControl(
    label: String,
    value: Float,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit
) {

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(4.dp)
    ) {

        Text(
            text = "$label: ${value}°",
            modifier = Modifier.width(100.dp)
        )


        Button(
            onClick = onDecrease
        ) {
            Text("-")
        }


        Spacer(
            modifier = Modifier.width(8.dp)
        )


        Button(
            onClick = onIncrease
        ) {
            Text("+")
        }
    }
}