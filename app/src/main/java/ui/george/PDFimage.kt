package ui.george

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.ImageView
import kotlin.properties.Delegates


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

    // image to display
    var bitmap: Bitmap? = null
    private var transformation = Matrix()
    private var imageScale = 1.0f
    private val scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())
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
        if (event.pointerCount > 1) {
            return true
        }
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                Log.d(LOGNAME, "Action down")
                path = Path()
                path!!.moveTo(event.x, event.y)
                if (currentTool != Tool.ERASER) {
                    paths[pageNum]?.get(currentTool)!!.add(path!!)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                Log.d(LOGNAME, "Action move")
                path!!.lineTo(event.x, event.y)
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
                        paths[pageNum]?.get(tool)!!.removeAll(deletePaths)
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
        }
        transformation = Matrix()
        imageScale = 1.0f
    }

    override fun onDraw(canvas: Canvas) {
        // draw background
        canvas.concat(transformation)
        if (bitmap != null) {
            setImageBitmap(bitmap)
//            canvas.drawBitmap(bitmap!!, 0f, 0f, null)
        }
        // Draw lines
        for (path in paths[pageNum]?.get(Tool.HIGHLIGHTER)!!) {
            canvas.drawPath(path, paint[Tool.HIGHLIGHTER]!!)
        }
        for (path in paths[pageNum]?.get(Tool.PEN)!!) {
            canvas.drawPath(path, paint[Tool.PEN]!!)
        }
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
}