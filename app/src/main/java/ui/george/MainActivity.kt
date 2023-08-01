package ui.george

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


// PDF sample code from
// https://medium.com/@chahat.jain0/rendering-a-pdf-document-in-android-activity-fragment-using-pdfrenderer-442462cb8f9a
// Issues about cache etc. are not at all obvious from documentation, so we should expect people to need this.
// We may wish to provide this code.
class MainActivity : AppCompatActivity() {
    val LOGNAME = "pdf_viewer"
    val FILENAME = "shannon1948.pdf"
    val FILERESID = R.raw.shannon1948

    // manage the pages of the PDF, see below
    lateinit var pdfRenderer: PdfRenderer
    lateinit var parcelFileDescriptor: ParcelFileDescriptor
    var currentPage: PdfRenderer.Page? = null
    var pageIndex: Int = 0

    // custom ImageView class that captures strokes and draws them over the image
    lateinit var pageImage: PDFimage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val layout = findViewById<FrameLayout>(R.id.pdfLayout)
        layout.isEnabled = true

        pageImage = PDFimage(this)
        layout.addView(pageImage)
        pageImage.minimumWidth = 1000
        pageImage.minimumHeight = 2000

        // Navigation
        findViewById<ImageButton>(R.id.backButton).apply {
            setOnClickListener {
                pageIndex -= 1
                updatePage()
            }
        }
        findViewById<ImageButton>(R.id.nextButton).apply {
            setOnClickListener {
                pageIndex += 1
                updatePage()
            }
        }

        // Drawing tools
        val penButton = findViewById<ImageButton>(R.id.penButton)
        val highlighterButton = findViewById<ImageButton>(R.id.highlighterButton)
        val eraserButton = findViewById<ImageButton>(R.id.eraserButton)
        findViewById<ImageButton>(R.id.penButton).apply {
            setOnClickListener {
                pageImage.currentTool = Tool.PEN
                setBackgroundResource(R.drawable.toggle_button_selected)
                highlighterButton.setBackgroundResource(R.drawable.toggle_button_unselected)
                eraserButton.setBackgroundResource(R.drawable.toggle_button_unselected)
            }
        }
        findViewById<ImageButton>(R.id.highlighterButton).apply {
            setOnClickListener {
                pageImage.currentTool = Tool.HIGHLIGHTER
                penButton.setBackgroundResource(R.drawable.toggle_button_unselected)
                setBackgroundResource(R.drawable.toggle_button_selected)
                eraserButton.setBackgroundResource(R.drawable.toggle_button_unselected)
            }
        }
        findViewById<ImageButton>(R.id.eraserButton).apply {
            setOnClickListener {
                pageImage.currentTool = Tool.ERASER
                penButton.setBackgroundResource(R.drawable.toggle_button_unselected)
                highlighterButton.setBackgroundResource(R.drawable.toggle_button_unselected)
                setBackgroundResource(R.drawable.toggle_button_selected)
            }
        }

        // open page 0 of the PDF
        // it will be displayed as an image in the pageImage (above)
        try {
            updatePage()
        }
        catch (exception: IOException) {
            Log.d(LOGNAME, "Error opening PDF")
        }
    }

    override fun onStop() {
        super.onStop()
//        try {
//            closeRenderer()
//        }
//        catch (ex: IOException) {
//            Log.d(LOGNAME, "Unable to close PDF renderer")
//        }
    }

    private fun updatePage() {
        openRenderer(this)
        showPage()
        findViewById<TextView>(R.id.pageNumber).text = String.format(
            getString(R.string.pageNumber),
            pageIndex + 1,
            pdfRenderer.pageCount
        )
        closeRenderer()
    }

    @Throws(IOException::class)
    private fun openRenderer(context: Context) {
        // In this sample, we read a PDF from the assets directory.
        val file = File(context.cacheDir, FILENAME)
        if (!file.exists()) {
            // pdfRenderer cannot handle the resource directly,
            // so extract it into the local cache directory.
            val asset = this.resources.openRawResource(FILERESID)
            val output = FileOutputStream(file)
            val buffer = ByteArray(1024)
            var size: Int
            while (asset.read(buffer).also { size = it } != -1) {
                output.write(buffer, 0, size)
            }
            asset.close()
            output.close()
        }
        parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)

        // capture PDF data
        // all this just to get a handle to the actual PDF representation
        pdfRenderer = PdfRenderer(parcelFileDescriptor)
    }

    // do this before you quit!
    @Throws(IOException::class)
    private fun closeRenderer() {
        currentPage?.close()
        currentPage = null
        pdfRenderer.close()
        parcelFileDescriptor.close()
    }

    private fun showPage() {
        if (pdfRenderer.pageCount <= pageIndex) {
            pageIndex = pdfRenderer.pageCount - 1
            return
        }
        else if (0 > pageIndex) {
            pageIndex = 0
            return
        }
        pageImage.reset(pageIndex)

        // Close the current page before opening another one.
        currentPage?.close()

        // Use `openPage` to open a specific page in PDF.
        currentPage = pdfRenderer.openPage(pageIndex)

        if (currentPage != null) {
            // Important: the destination bitmap must be ARGB (not RGB).
            val bitmap =
                Bitmap.createBitmap(
                    currentPage!!.width * 2,
                    currentPage!!.height * 2,
                    Bitmap.Config.ARGB_8888
                )

            // Here, we render the page onto the Bitmap.
            // To render a portion of the page, use the second and third parameter. Pass nulls to get the default result.
            // Pass either RENDER_MODE_FOR_DISPLAY or RENDER_MODE_FOR_PRINT for the last parameter.
            currentPage!!.render(bitmap, null, Matrix().apply {
                postScale(2f, 2f)
            }, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            // Display the page
            pageImage.setImage(bitmap)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putInt("pageIndex", pageIndex)
        val data: MutableMap<Int, Map<Tool, MutableList<MutableList<Float>>>> = pageImage.pathPoints
        outState.putIntegerArrayList("pagePathKeys", ArrayList(data.keys))
        for (key in data.keys) {
            val toolMapBundle = Bundle()
            for (tool in data[key]!!.keys) {
                val pathList = data[key]!![tool]!!
                val pathBundle = Bundle()
                for (i in pathList.indices) {
                    val path = pathList[i]
                    val pathArray = FloatArray(path.size)
                    for (j in path.indices) {
                        pathArray[j] = path[j]
                    }
                    pathBundle.putFloatArray(i.toString(), pathArray)
                }
                toolMapBundle.putBundle(tool.toString(), pathBundle)
            }
            outState.putBundle(key.toString(), toolMapBundle)
        }
    }
}