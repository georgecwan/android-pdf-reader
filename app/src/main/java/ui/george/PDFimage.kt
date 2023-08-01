package ui.george

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable.Orientation
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.ImageView


enum class Tool {
    PEN, HIGHLIGHTER, ERASER
}

@SuppressLint("AppCompatCustomView")
class PDFimage  // constructor
    (context: Context?) : ImageView(context) {

    val LOGNAME = "pdf_image"
    var pageNum = 0

    // drawing path
    var path: Path? = null
    var paths = mutableMapOf<Int, Map<Tool, MutableList<Path>>>(
        0 to mapOf(
            Tool.PEN to mutableListOf(),
            Tool.HIGHLIGHTER to mutableListOf()
        )
    )
    var pathPoints = mutableMapOf<Int, Map<Tool, MutableList<MutableList<Float>>>>(
        0 to mapOf(
            Tool.PEN to mutableListOf(),
            Tool.HIGHLIGHTER to mutableListOf()
        )
    )

    // image to display
    var bitmap: Bitmap? = null
    private var transformation = Matrix()
    private var imageScale = 1.0f
    private val scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())
    private val panGestureDetector = GestureDetector(context, GestureListener())
    var paint = mapOf(
        Tool.PEN to Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
            color = Color.BLUE
        },
        Tool.HIGHLIGHTER to Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 10f
            alpha = 155
            color = Color.YELLOW
        },
        Tool.ERASER to Paint().apply {
            strokeWidth = 2f
            style = Paint.Style.STROKE
        })

    var currentTool = Tool.PEN

    // capture touch events (down/move/up) to create a path
    // and use that to create a stroke that we can draw
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)
        panGestureDetector.onTouchEvent(event)
        if (event.pointerCount > 1) {
            return true
        }
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                Log.d(LOGNAME, "Action down")

                val inverseMatrix = Matrix().apply {
                    transformation.invert(this)
                }
                val originalPoint = PointF(event.x, event.y)
                if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    originalPoint.y += 860f
                    originalPoint.x /= (1442 / 1012f)
                    originalPoint.y /= (1442 / 1012f)
                }
                val transformedPoint = floatArrayOf(originalPoint.x, originalPoint.y)
                inverseMatrix.mapPoints(transformedPoint)

                path = Path()
                path!!.moveTo(transformedPoint[0], transformedPoint[1])
                if (currentTool != Tool.ERASER) {
                    paths[pageNum]?.get(currentTool)!!.add(path!!)
                    pathPoints[pageNum]?.get(currentTool)!!.add(mutableListOf(transformedPoint[0], transformedPoint[1]))
                }
            }

            MotionEvent.ACTION_MOVE -> {
                Log.d(LOGNAME, "Action move")

                val inverseMatrix = Matrix().apply {
                    transformation.invert(this)
                }
                val originalPoint = PointF(event.x, event.y)
                if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    originalPoint.y += 860f
                    originalPoint.x /= (1442 / 1012f)
                    originalPoint.y /= (1442 / 1012f)
                }
                val transformedPoint = floatArrayOf(originalPoint.x, originalPoint.y)
                inverseMatrix.mapPoints(transformedPoint)

                path!!.lineTo(transformedPoint[0], transformedPoint[1])
                if (currentTool != Tool.ERASER) {
                    pathPoints[pageNum]?.get(currentTool)!!.last().add(transformedPoint[0])
                    pathPoints[pageNum]?.get(currentTool)!!.last().add(transformedPoint[1])
                }
                if (currentTool == Tool.ERASER) {
                    for (tool in listOf(Tool.PEN, Tool.HIGHLIGHTER)) {
                        val deletePaths = mutableListOf<Path>()
                        for (drawnPath in paths[pageNum]?.get(tool)!!) {
                            val intersection = Path().apply {
                                op(path!!, drawnPath, Path.Op.INTERSECT)
                            }
                            if (!intersection.isEmpty) {
                                deletePaths.add(drawnPath)
                            }
                        }
                        var pathPointIndices = mutableListOf<Int>()
                        for (i in 0 until paths[pageNum]?.get(tool)!!.size) {
                            if (deletePaths.contains(paths[pageNum]?.get(tool)!![i])) {
                                pathPointIndices.add(i)
                            }
                        }
                        paths[pageNum]?.get(tool)!!.removeAll(deletePaths)
                        pathPointIndices.reverse()
                        for (i in pathPointIndices) {
                            pathPoints[pageNum]?.get(tool)!!.removeAt(i)
                        }
                    }
                }
            }

//            MotionEvent.ACTION_UP -> {
//                Log.d(LOGNAME, "Action up")
//            }
        }
        return true
    }

    // set image as background
    fun setImage(bitmap: Bitmap?) {
        this.bitmap = bitmap
    }

    // Resets transformations
    fun reset(pageNum: Int) {
        this.pageNum = pageNum
        if (paths[pageNum] == null) {
            paths[pageNum] = mapOf(
                Tool.PEN to mutableListOf(),
                Tool.HIGHLIGHTER to mutableListOf()
            )
            pathPoints[pageNum] = mapOf(
                Tool.PEN to mutableListOf(),
                Tool.HIGHLIGHTER to mutableListOf()
            )
        }
        transformation = Matrix()
        imageScale = 1.0f
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        // draw background
        canvas.concat(transformation)
        if (bitmap != null) {
            scaleType = ScaleType.CENTER_CROP
            setImageBitmap(bitmap)
//            canvas.drawBitmap(bitmap!!, 0f, 0f, null)
        }
        canvas.save()
        if (resources.configuration.orientation ==
            Configuration.ORIENTATION_LANDSCAPE) {
            canvas.translate(0f, -860f)
            canvas.scale((1442 / 1012f), (1442 / 1012f))
        }
        // Draw lines
        for (path in paths[pageNum]?.get(Tool.HIGHLIGHTER)!!) {
            canvas.drawPath(path, paint[Tool.HIGHLIGHTER]!!)
        }
        for (path in paths[pageNum]?.get(Tool.PEN)!!) {
            canvas.drawPath(path, paint[Tool.PEN]!!)
        }
        canvas.restore()
        super.onDraw(canvas)
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            // Make sure scaleFactor is between 1 and 4
            val scaleFactor = (1.0f / imageScale).coerceAtLeast(detector.scaleFactor.coerceAtMost(4.0f / imageScale))
            imageScale *= scaleFactor

            transformation.postScale(scaleFactor, scaleFactor, detector.focusX, detector.focusY)
            return true
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent?,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            if (e2?.pointerCount!! == 1) {
                return false
            }
            Log.d(LOGNAME, "Action scroll")

            transformation.postTranslate(-distanceX, -distanceY)
            return true
        }
    }

    fun setPathPoints(bundle: Bundle) {
        pathPoints = mutableMapOf<Int, Map<Tool, MutableList<MutableList<Float>>>>()
        val keys = bundle.getIntegerArrayList("pagePathKeys")
        for (key in keys!!) {
            val toolMapBundle = bundle.getBundle(key.toString())
            val penPoints = mutableListOf<MutableList<Float>>()
            val highlighterPoints = mutableListOf<MutableList<Float>>()
            val penKeys = toolMapBundle?.getBundle(Tool.PEN.toString())
            val highlighterKeys = toolMapBundle?.getBundle(Tool.HIGHLIGHTER.toString())
            for (i in 0 until penKeys?.size()!!) {
                val point = penKeys.getFloatArray(i.toString())?.toMutableList()!!
                penPoints.add(point)
            }
            for (i in 0 until highlighterKeys?.size()!!) {
                val point = highlighterKeys.getFloatArray(i.toString())?.toMutableList()!!
                highlighterPoints.add(point)
            }
            pathPoints[key] = mapOf(
                Tool.PEN to penPoints,
                Tool.HIGHLIGHTER to highlighterPoints
            )
        }
        paths = mutableMapOf<Int, Map<Tool, MutableList<Path>>>()
        for (key in pathPoints.keys) {
            val penPaths = mutableListOf<Path>()
            val highlighterPaths = mutableListOf<Path>()
            for (point in pathPoints[key]?.get(Tool.PEN)!!) {
                val path = Path()
                path.moveTo(point[0], point[1])
                for (i in 2 until point.size step 2) {
                    path.lineTo(point[i], point[i + 1])
                }
                penPaths.add(path)
            }
            for (point in pathPoints[key]?.get(Tool.HIGHLIGHTER)!!) {
                val path = Path()
                path.moveTo(point[0], point[1])
                for (i in 2 until point.size step 2) {
                    path.lineTo(point[i], point[i + 1])
                }
                highlighterPaths.add(path)
            }
            paths[key] = mapOf(
                Tool.PEN to penPaths,
                Tool.HIGHLIGHTER to highlighterPaths
            )
        }
    }
}