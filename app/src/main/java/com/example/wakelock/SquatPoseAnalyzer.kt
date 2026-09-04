package com.example.wakelock

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import kotlin.math.atan2

class SquatPoseAnalyzer(
    private val onSquatCounted: (Int) -> Unit
) : ImageAnalysis.Analyzer {

    private enum class SquatState { UP, DOWN }

    private val options = PoseDetectorOptions.Builder()
        .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
        .build()

    private val detector = PoseDetection.getClient(options)
    private var currentState = SquatState.UP
    private var squatCount = 0

    private fun calculateAngle(
        firstPoint: Pair<Float, Float>,
        midPoint: Pair<Float, Float>,
        lastPoint: Pair<Float, Float>
    ): Double {
        var result = Math.toDegrees(
            (atan2(lastPoint.second - midPoint.second, lastPoint.first - midPoint.first) -
                    atan2(firstPoint.second - midPoint.second, firstPoint.first - midPoint.first)).toDouble()
        )
        result = Math.abs(result)
        if (result > 180.0) {
            result = 360.0 - result
        }
        return result
    }

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val inputImage = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )

            detector.process(inputImage)
                .addOnSuccessListener { pose ->
                    val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
                    val leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
                    val leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)

                    val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
                    val rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)
                    val rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)

                    // 1. High confidence threshold: Uses inFrameLikelihood
                    val minConfidence = 0.75f

                    val leftLegValid = leftHip != null && leftKnee != null && leftAnkle != null &&
                            leftHip.inFrameLikelihood > minConfidence &&
                            leftKnee.inFrameLikelihood > minConfidence &&
                            leftAnkle.inFrameLikelihood > minConfidence

                    val rightLegValid = rightHip != null && rightKnee != null && rightAnkle != null &&
                            rightHip.inFrameLikelihood > minConfidence &&
                            rightKnee.inFrameLikelihood > minConfidence &&
                            rightAnkle.inFrameLikelihood > minConfidence

                    if (leftLegValid || rightLegValid) {
                        var angleSum = 0.0
                        var count = 0

                        if (leftLegValid) {
                            angleSum += calculateAngle(
                                Pair(leftHip!!.position.x, leftHip.position.y),
                                Pair(leftKnee!!.position.x, leftKnee.position.y),
                                Pair(leftAnkle!!.position.x, leftAnkle.position.y)
                            )
                            count++
                        }

                        if (rightLegValid) {
                            angleSum += calculateAngle(
                                Pair(rightHip!!.position.x, rightHip.position.y),
                                Pair(rightKnee!!.position.x, rightKnee.position.y),
                                Pair(rightAnkle!!.position.x, rightAnkle.position.y)
                            )
                            count++
                        }

                        val averageAngle = angleSum / count

                        // 2. Strict State Machine (Requires full squat <90° then standing straight >160°)
                        if (averageAngle < 90 && currentState == SquatState.UP) {
                            currentState = SquatState.DOWN
                        } else if (averageAngle > 160 && currentState == SquatState.DOWN) {
                            currentState = SquatState.UP
                            squatCount++
                            onSquatCounted(squatCount)
                        }
                    }
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}