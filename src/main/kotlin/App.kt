import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.application.Application
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.scene.Group
import javafx.scene.Scene
import javafx.scene.canvas.Canvas
import javafx.scene.canvas.GraphicsContext
import javafx.scene.image.Image
import javafx.scene.layout.StackPane
import javafx.scene.paint.Color
import javafx.scene.shape.Rectangle

import javafx.stage.Stage
import javafx.util.Duration

class App : Application() {
    val robotRect = Rectangle(100.0, 100.0, 10.0, 10.0)
    val startRect = Rectangle(100.0, 100.0, 10.0, 10.0)
    val endRect = Rectangle(100.0, 100.0, 10.0, 10.0)

    var startTime = Double.NaN
    val trajectories = TrajectoryGen.createTrajectory()

    lateinit var fieldImage: Image
    lateinit var stage: Stage


    var activeTrajectoryIndex = 0
    val trajectoryDurations = trajectories.map { it.duration() }
    val duration = trajectoryDurations.sum()
    val numberOfTrajectories = trajectories.size

    companion object {
        var WIDTH = 0.0
        var HEIGHT = 0.0
    }

    override fun start(stage: Stage?) {
        this.stage = stage!!
        fieldImage = Image("/output3.png")

        val root = Group()

        WIDTH = fieldImage.width
        HEIGHT = fieldImage.height
        GraphicsUtil.pixelsPerInch = WIDTH / GraphicsUtil.FIELD_WIDTH
        GraphicsUtil.halfFieldPixels = WIDTH / 2.0

        val canvas = Canvas(WIDTH, HEIGHT)
        val gc = canvas.graphicsContext2D
        val t1 = Timeline(KeyFrame(Duration.millis(10.0), EventHandler<ActionEvent> { run(gc) }))
        t1.cycleCount = Timeline.INDEFINITE

        stage.scene = Scene(
            StackPane(
                root
            )
        )

        root.children.addAll(canvas, startRect, endRect, robotRect)

        stage.title = "PathVisualizer"
        stage.isResizable = false

        println("duration $duration")

        stage.show()
        t1.play()
    }

    fun run(gc: GraphicsContext) {
        if (startTime.isNaN())
            startTime = Clock.seconds

        GraphicsUtil.gc = gc
        gc.drawImage(fieldImage, 0.0, 0.0)

        gc.lineWidth = GraphicsUtil.LINE_THICKNESS

        gc.globalAlpha = 0.5
        GraphicsUtil.setColor(Color.RED)
        TrajectoryGen.drawOffbounds()
        gc.globalAlpha = 1.0

        val trajectory = trajectories[activeTrajectoryIndex]

        val prevDurations: Double = {
            var x = 0.0
            for (i in 0 until activeTrajectoryIndex)
                x += trajectoryDurations[i]
            x
        }()

        val time = Clock.seconds
        val profileTime = time - startTime - prevDurations
        val duration = trajectoryDurations[activeTrajectoryIndex]

        val start = trajectories.first().start()
        val end = trajectories.last().end()
        val current = trajectory[profileTime]

        if (profileTime >= duration) {
            activeTrajectoryIndex++
            if(activeTrajectoryIndex >= numberOfTrajectories) {
                activeTrajectoryIndex = 0
                startTime = time
            }
        }

        trajectories.forEach{GraphicsUtil.drawSampledPath(it.path)}

        GraphicsUtil.updateRobotRect(startRect, start, GraphicsUtil.END_BOX_COLOR, 0.5)
        GraphicsUtil.updateRobotRect(endRect, end, GraphicsUtil.END_BOX_COLOR, 0.5)

        GraphicsUtil.updateRobotRect(robotRect, current, GraphicsUtil.ROBOT_COLOR, 0.85)
        GraphicsUtil.drawRobotVector(current)
        val xSpacing = 180.0
        drawMatrix(gc,0.0,10.0,start.heading,start.x,start.y,"Start")
        drawMatrix(gc,xSpacing*1,10.0,end.heading,end.x,end.y,"End")
        drawMatrix(gc,xSpacing*2,10.0,current.heading,current.x,current.y,"Interpolation (Current)")
        val sToC = disp(start.heading,start.x,start.y,current.heading,current.x,current.y)
        val cToE = disp(current.heading,current.x,current.y,end.heading,end.x,end.y)
        drawMatrix(gc,xSpacing*3,10.0,sToC.first,sToC.second,sToC.third,"disp(Start,Current)")
        drawMatrix(gc,xSpacing*4,10.0,cToE.first,cToE.second,cToE.third,"disp(Current,End)")

        stage.title = "Profile duration : ${"%.2f".format(duration)} - time in profile ${"%.2f".format(profileTime)}"
    }
}

fun drawMatrix(gc: GraphicsContext, x: Double, y: Double, theta: Double, tx: Double, ty: Double, title: String) {

    // set the text fill color
    gc.fill = Color.WHITE

    // draw the matrix
    gc.fillText("cos(${String.format("%.2f", theta)})  -sin(${String.format("%.2f", theta)})  ${String.format("%.2f", tx)}", x + 10, y + 20)
    gc.fillText("sin(${String.format("%.2f", theta)})   cos(${String.format("%.2f", theta)})  ${String.format("%.2f", ty)}", x + 10, y + 40)
    gc.fillText(" 0               0               1", x + 20, y + 60)

    // draw the brackets
    gc.lineWidth = 2.0
    gc.stroke = Color.WHITE
    gc.strokeLine(x, y, x, y + 80) // left bracket
    gc.strokeLine(x + 170, y, x + 170, y + 80) // right bracket

    // draw the title
    gc.fillText(title, x+20, y + 100)
}
fun disp(aTheta: Double, aTx: Double, aTy: Double, bTheta: Double, bTx: Double, bTy: Double): Triple<Double, Double, Double> {
    // Convert parameters to SE(2) matrices
    val a = arrayOf(
        arrayOf(cos(aTheta), -sin(aTheta), aTx),
        arrayOf(sin(aTheta), cos(aTheta), aTy),
        arrayOf(0.0, 0.0, 1.0)
    )
    val b = arrayOf(
        arrayOf(cos(bTheta), -sin(bTheta), bTx),
        arrayOf(sin(bTheta), cos(bTheta), bTy),
        arrayOf(0.0, 0.0, 1.0)
    )

    // Calculate the inverse of matrix a
    val aInv = inverse(a)

    // Calculate the product of a^{-1} and b
    val c = multiply(aInv, b)

    // Extract the new theta, tx, and ty
    val dispTheta = atan2(c[1][0], c[0][0])
    val dispTx = c[0][2]
    val dispTy = c[1][2]

    return Triple(dispTheta, dispTx, dispTy)
}

fun cos(theta: Double): Double = Math.cos(theta)
fun sin(theta: Double): Double = Math.sin(theta)
fun atan2(y: Double, x: Double): Double = Math.atan2(y, x)
fun inverse(matrix: Array<Array<Double>>): Array<Array<Double>> {
    val det = determinant(matrix)
    if (det == 0.0) {
        throw RuntimeException("Matrix is not invertible")
    }

    val inv = arrayOf(
        arrayOf((matrix[1][1] / det), (-matrix[0][1] / det), ((matrix[0][1] * matrix[2][2] - matrix[0][2] * matrix[2][1]) / det)),
        arrayOf((-matrix[1][0] / det), (matrix[0][0] / det), ((matrix[0][2] * matrix[1][0] - matrix[0][0] * matrix[2][1]) / det)),
        arrayOf(0.0, 0.0, 1.0)
    )

    return inv
}

fun multiply(a: Array<Array<Double>>, b: Array<Array<Double>>): Array<Array<Double>> {
    val result = arrayOf(
        arrayOf(0.0, 0.0, 0.0),
        arrayOf(0.0, 0.0, 0.0),
        arrayOf(0.0, 0.0, 0.0)
    )

    for (i in 0..2) {
        for (j in 0..2) {
            for (k in 0..2) {
                result[i][j] += a[i][k] * b[k][j]
            }
        }
    }

    return result
}

fun determinant(matrix: Array<Array<Double>>): Double {
    return matrix[0][0] * (matrix[1][1] * matrix[2][2] - matrix[1][2] * matrix[2][1]) -
            matrix[0][1] * (matrix[1][0] * matrix[2][2] - matrix[1][2] * matrix[2][0]) +
            matrix[0][2] * (matrix[1][0] * matrix[2][1] - matrix[1][1] * matrix[2][0])
}
fun main(args: Array<String>) {
    Application.launch(App::class.java, *args)
}