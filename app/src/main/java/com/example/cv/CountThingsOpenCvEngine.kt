package com.example.cv

import android.graphics.Bitmap
import android.util.Log
import com.example.model.CellPointMarker
import com.example.model.DetectedNeubauerGrid
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

private const val CV_TAG = "CountThingsOpenCV"

/**
 * CountThings-inspired On-Device OpenCV Computer Vision Engine for Neubauer Hemocytometer Slides.
 *
 * Implements two core CountThings workflows completely offline (zero API quota / zero billing):
 * 1. Auto-CV Neubauer Mode:
 *    - Illumination equalization & circular ocular-vignette masking
 *    - Tilted Neubauer grid detection via HoughLinesP + bare grid-intersection suppression
 *    - Multi-scale Difference-of-Gaussians (DoG) + 360-degree radial dark-membrane ring symmetry
 *      for Live viable cells (naturally rejects scratches, dust fibers, and grid intersections)
 *    - Trypan-Blue / dark-body contrast + Distance Transform watershed peak splitting for Dead cells
 *
 * 2. Tap-a-Cell Template Matching Mode (True CountThings Signature):
 *    - Snaps onto a user-tapped reference cell (Live or Dead), estimates its optimal patch radius,
 *      and runs multi-scale + rotation-invariant Normalized Cross-Correlation (`Imgproc.matchTemplate`,
 *      `TM_CCOEFF_NORMED`) with Non-Maximum Suppression across the slide.
 */
object CountThingsOpenCvEngine {

    @Volatile
    private var openCvInitialized: Boolean = false

    data class DetectionResult(
        val markers: List<CellPointMarker>,
        val detectedGrid: DetectedNeubauerGrid,
        val summaryPersian: String,
        val technicalDetails: String,
        val technicalOutput: String
    )

    private data class CandidateCell(
        val x: Float,
        val y: Float,
        val radius: Float,
        val isLive: Boolean,
        val isOnGridLine: Boolean,
        val isOverlapping: Boolean,
        val confidence: Int,
        val score: Float
    )

    private data class GridExtraction(
        val grid: DetectedNeubauerGrid,
        val lineMask: ByteArray,
        val intersectionMask: ByteArray
    )

    fun ensureInitialized(): Boolean {
        if (openCvInitialized) return true
        return synchronized(this) {
            if (openCvInitialized) return true
            val ok = try {
                OpenCVLoader.initLocal()
            } catch (e: Throwable) {
                Log.e(CV_TAG, "Failed to initialize OpenCV native library", e)
                false
            }
            openCvInitialized = ok
            ok
        }
    }

    fun analyzeAuto(sourceBitmap: Bitmap, nextIdStart: Long): DetectionResult {
        check(ensureInitialized()) {
            "کتابخانه بینایی ماشین OpenCV روی این دستگاه مقداردهی نشد."
        }

        val workBitmap = scaleForCv(sourceBitmap, maxDim = 1280)
        val w = workBitmap.width
        val h = workBitmap.height

        val rgba = Mat()
        val bgr = Mat()
        val gray8u = Mat()
        val gray32f = Mat()
        val bg32f = Mat()
        val normSigned32f = Mat()
        val blueExcess32f = Mat()
        val ocularMask = Mat()

        try {
            Utils.bitmapToMat(workBitmap, rgba)
            Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
            Imgproc.cvtColor(bgr, gray8u, Imgproc.COLOR_BGR2GRAY)
            gray8u.convertTo(gray32f, CvType.CV_32F)

            // Large Gaussian background estimation (~4% of dimension, odd kernel)
            val bgK = ((max(w, h) * 0.042).toInt() or 1).coerceIn(31, 75)
            Imgproc.GaussianBlur(gray32f, bg32f, Size(bgK.toDouble(), bgK.toDouble()), bgK * 0.35)
            Core.subtract(gray32f, bg32f, normSigned32f)

            computeBlueExcess(bgr, bgK, blueExcess32f)
            buildOcularMask(gray8u, ocularMask)

            val normData = FloatArray(w * h)
            normSigned32f.get(0, 0, normData)

            val blueData = FloatArray(w * h)
            blueExcess32f.get(0, 0, blueData)

            val ocularBytes = ByteArray(w * h)
            ocularMask.get(0, 0, ocularBytes)

            val sigmaNoise = estimateRobustNoiseSigma(normData, ocularBytes, w, h)
            val gridInfo = extractNeubauerGridAndMasks(normSigned32f, ocularBytes, w, h, sigmaNoise)

            // 1. Multi-scale DoG + 360-degree dark-ring symmetry for LIVE cells
            val liveCandidates = detectLiveCellsMultiScaleDoG(
                normSigned32f = normSigned32f,
                normData = normData,
                ocularBytes = ocularBytes,
                gridInfo = gridInfo,
                w = w,
                h = h,
                sigmaNoise = sigmaNoise
            )

            // 2. Trypan-Blue / negative darkness + Distance Transform peak splitting for DEAD cells
            val deadCandidates = detectDeadCellsDistanceTransform(
                normSigned32f = normSigned32f,
                blueExcess32f = blueExcess32f,
                normData = normData,
                blueData = blueData,
                ocularBytes = ocularBytes,
                gridInfo = gridInfo,
                existingLive = liveCandidates,
                w = w,
                h = h,
                sigmaNoise = sigmaNoise
            )

            val combined = finalizeMarkers(
                candidates = liveCandidates + deadCandidates,
                w = w,
                h = h,
                nextIdStart = nextIdStart
            )

            val liveCount = combined.count { it.isLive }
            val deadCount = combined.count { !it.isLive }
            val gridTotal = gridInfo.grid.totalLinesCount

            return DetectionResult(
                markers = combined,
                detectedGrid = gridInfo.grid,
                summaryPersian = "آنالیز آفلاین سبک CountThings (OpenCV): تقارن ۳۶۰ درجه هاله سلول زنده، حذف خطوط و تقاطع‌های شبکه نئوبار و تفکیک توده‌های چسبیده انجام شد.",
                technicalDetails = buildString {
                    appendLine("موتور شمارش: CountThings-Style OpenCV 4.11 (آفلاین و بدون محدودیت سهمیه)")
                    appendLine("رزولوشن پردازش: ${w}×${h} پیکسل | نویز پایه (MAD): ${"%.2f".format(sigmaNoise)}")
                    appendLine("خطوط شبکه نئوبار شناسایی‌شده: ${gridInfo.grid.verticalLines.size} عمودی × ${gridInfo.grid.horizontalLines.size} افقی")
                    appendLine("تشخیص خام: $liveCount سلول زنده + $deadCount سلول مرده (مجموع ${combined.size})")
                    append("نکته: برای دقت فوق‌العاده در بزرگ‌نمایی‌های خاص، ابزار «الگوی زنده/مرده» را انتخاب کرده و روی ۱ سلول نمونه ضربه بزنید.")
                },
                technicalOutput = "OpenCV 4.11 Auto-CV | Grid=$gridTotal | Live=$liveCount | Dead=$deadCount | Sigma=${"%.1f".format(sigmaNoise)}"
            )
        } finally {
            rgba.release()
            bgr.release()
            gray8u.release()
            gray32f.release()
            bg32f.release()
            normSigned32f.release()
            blueExcess32f.release()
            ocularMask.release()
            if (workBitmap !== sourceBitmap && !workBitmap.isRecycled) {
                workBitmap.recycle()
            }
        }
    }

    /**
     * CountThings Signature Feature:
     * User taps ONE example cell (Live or Dead), and OpenCV snaps to that cell's center,
     * crops an illumination-normalized patch, and runs multi-scale + rotated Normalized Cross-Correlation
     * (`Imgproc.matchTemplate`, `TM_CCOEFF_NORMED`) across the entire slide!
     */
    fun analyzeWithTappedTemplate(
        sourceBitmap: Bitmap,
        tapNormX: Float,
        tapNormY: Float,
        isLiveTemplate: Boolean,
        existingOtherTypeMarkers: List<CellPointMarker>,
        nextIdStart: Long
    ): DetectionResult {
        check(ensureInitialized()) {
            "کتابخانه بینایی ماشین OpenCV روی این دستگاه مقداردهی نشد."
        }

        val workBitmap = scaleForCv(sourceBitmap, maxDim = 1280)
        val w = workBitmap.width
        val h = workBitmap.height

        val rgba = Mat()
        val bgr = Mat()
        val gray8u = Mat()
        val gray32f = Mat()
        val bg32f = Mat()
        val normSigned32f = Mat()
        val ocularMask = Mat()

        try {
            Utils.bitmapToMat(workBitmap, rgba)
            Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
            Imgproc.cvtColor(bgr, gray8u, Imgproc.COLOR_BGR2GRAY)
            gray8u.convertTo(gray32f, CvType.CV_32F)

            val bgK = ((max(w, h) * 0.042).toInt() or 1).coerceIn(31, 75)
            Imgproc.GaussianBlur(gray32f, bg32f, Size(bgK.toDouble(), bgK.toDouble()), bgK * 0.35)
            Core.subtract(gray32f, bg32f, normSigned32f)
            buildOcularMask(gray8u, ocularMask)

            val normData = FloatArray(w * h)
            normSigned32f.get(0, 0, normData)

            val ocularBytes = ByteArray(w * h)
            ocularMask.get(0, 0, ocularBytes)

            val sigmaNoise = estimateRobustNoiseSigma(normData, ocularBytes, w, h)
            val gridInfo = extractNeubauerGridAndMasks(normSigned32f, ocularBytes, w, h, sigmaNoise)

            // 1. Snap user tap onto true cell center within 18px search window
            val rawTx = (tapNormX * w).roundToInt().coerceIn(14, w - 15)
            val rawTy = (tapNormY * h).roundToInt().coerceIn(14, h - 15)
            val (snappedX, snappedY) = snapToCellCenter(
                normData = normData,
                w = w,
                h = h,
                tapX = rawTx,
                tapY = rawTy,
                lookForBrightCore = isLiveTemplate
            )

            // 2. Estimate optimal cell template half-size (9..16 px -> 19x19 .. 33x33 patch)
            val patchHalf = estimateTemplateHalfSize(normData, w, h, snappedX, snappedY, isLiveTemplate)
            val patchSize = patchHalf * 2 + 1
            val templRect = Rect(
                (snappedX - patchHalf).coerceIn(0, w - patchSize),
                (snappedY - patchHalf).coerceIn(0, h - patchSize),
                patchSize,
                patchSize
            )
            val baseTemplate = Mat(normSigned32f, templRect).clone()

            // 3. Multi-scale & multi-rotation TM_CCOEFF_NORMED matching (CountThings style)
            val bestScoreData = FloatArray(w * h) { -1f }
            val scales = floatArrayOf(0.88f, 1.0f, 1.14f)
            val rotateCodes = intArrayOf(-1, Core.ROTATE_90_CLOCKWISE, Core.ROTATE_180, Core.ROTATE_90_COUNTERCLOCKWISE)

            try {
                for (scale in scales) {
                    val scaledSide = ((patchSize * scale).roundToInt() or 1).coerceIn(13, 45)
                    val scaledTempl = Mat()
                    if (scaledSide == patchSize) {
                        baseTemplate.copyTo(scaledTempl)
                    } else {
                        Imgproc.resize(baseTemplate, scaledTempl, Size(scaledSide.toDouble(), scaledSide.toDouble()), 0.0, 0.0, Imgproc.INTER_LINEAR)
                    }

                    try {
                        for (rotCode in rotateCodes) {
                            val variant = Mat()
                            val resMat = Mat()
                            try {
                                if (rotCode == -1) {
                                    scaledTempl.copyTo(variant)
                                } else {
                                    Core.rotate(scaledTempl, variant, rotCode)
                                }
                                if (w <= variant.cols() || h <= variant.rows()) continue

                                Imgproc.matchTemplate(normSigned32f, variant, resMat, Imgproc.TM_CCOEFF_NORMED)
                                val rw = resMat.cols()
                                val rh = resMat.rows()
                                val resArr = FloatArray(rw * rh)
                                resMat.get(0, 0, resArr)

                                val halfV = scaledSide / 2
                                for (ry in 0 until rh) {
                                    val fy = ry + halfV
                                    if (fy >= h) continue
                                    val dstRow = fy * w
                                    val srcRow = ry * rw
                                    for (rx in 0 until rw) {
                                        val fx = rx + halfV
                                        if (fx >= w) continue
                                        val v = resArr[srcRow + rx]
                                        if (v > bestScoreData[dstRow + fx]) {
                                            bestScoreData[dstRow + fx] = v
                                        }
                                    }
                                }
                            } finally {
                                variant.release()
                                resMat.release()
                            }
                        }
                    } finally {
                        scaledTempl.release()
                    }
                }
            } finally {
                baseTemplate.release()
            }

            // 4. Local-maximum NMS over match score map + artifact / intersection rejection
            val nmsRad = max(6, (patchHalf * 0.78f).roundToInt())
            val candidates = ArrayList<CandidateCell>()
            val minRawMatch = 0.40f

            for (y in patchHalf until h - patchHalf) {
                val rowOff = y * w
                for (x in patchHalf until w - patchHalf) {
                    val idx = rowOff + x
                    if (ocularBytes[idx] == 0.toByte()) continue
                    val score = bestScoreData[idx]
                    if (score < minRawMatch) continue

                    // Local 2D maximum check in (2*3+1)x(2*3+1) neighborhood
                    var isPeak = true
                    for (dy in -3..3) {
                        val nr = (y + dy) * w
                        for (dx in -3..3) {
                            if (dx == 0 && dy == 0) continue
                            if (bestScoreData[nr + x + dx] > score) {
                                isPeak = false
                                break
                            }
                        }
                        if (!isPeak) break
                    }
                    if (!isPeak) continue

                    val inIntersection = gridInfo.intersectionMask[idx] != 0.toByte()
                    val onLine = gridInfo.lineMask[idx] != 0.toByte()

                    // Verify basic polarity + guard against bare grid intersections
                    val centerVal = normData[idx]
                    if (isLiveTemplate) {
                        if (centerVal < 4f) continue
                        val ringInfo = measureDarkMembraneRing(
                            normData = normData,
                            w = w,
                            h = h,
                            cx = x,
                            cy = y,
                            radius = patchHalf * 0.65f,
                            sigmaNoise = sigmaNoise
                        )
                        val minSectors = if (inIntersection) 8 else if (onLine) 6 else 5
                        if (ringInfo.darkSectors < minSectors) continue
                    } else {
                        if (centerVal > -4f) continue
                        if (inIntersection && score < 0.68f) continue
                    }

                    // Map match correlation (0.40..0.92) -> confidence (25..99)
                    val conf = (((score - 0.32f) / 0.56f) * 74f + 25f).roundToInt().coerceIn(20, 99)
                    candidates.add(
                        CandidateCell(
                            x = x.toFloat(),
                            y = y.toFloat(),
                            radius = patchHalf * 0.72f,
                            isLive = isLiveTemplate,
                            isOnGridLine = onLine,
                            isOverlapping = false,
                            confidence = conf,
                            score = score
                        )
                    )
                }
            }

            // Greedy NMS sorted by match score, preserving tapped cell center
            val sorted = candidates.sortedByDescending { it.score }
            val nmsKept = ArrayList<CandidateCell>(sorted.size)
            val minCenterDist = nmsRad.toFloat().coerceAtLeast(6.5f)
            for (c in sorted) {
                val tooClose = nmsKept.any { kept ->
                    hypot(kept.x - c.x, kept.y - c.y) < minCenterDist
                }
                if (!tooClose) {
                    nmsKept.add(c)
                }
            }

            val matchedMarkers = finalizeMarkers(
                candidates = nmsKept,
                w = w,
                h = h,
                nextIdStart = nextIdStart
            )

            // Merge with preserved markers of the opposite class (removing overlaps)
            val merged = (existingOtherTypeMarkers.filter { other ->
                matchedMarkers.none { m ->
                    hypot((m.normalizedX - other.normalizedX) * w, (m.normalizedY - other.normalizedY) * h) < minCenterDist * 0.85f
                }
            } + matchedMarkers)

            val typeLabel = if (isLiveTemplate) "سلول زنده" else "سلول مرده"
            return DetectionResult(
                markers = merged,
                detectedGrid = gridInfo.grid,
                summaryPersian = "تطبیق الگوی CountThings بر اساس نمونه $typeLabel انتخابی شما انجام شد (${matchedMarkers.size} مورد هم‌الگو پیدا شد). با اسلایدر حساسیت می‌توانید آستانه تطبیق را تنظیم کنید.",
                technicalDetails = buildString {
                    appendLine("حالت CountThings: تطبیق الگوی پویا (Template Matching — TM_CCOEFF_NORMED)")
                    appendLine("نمونه مرجع: $typeLabel در (${snappedX}, ${snappedY}) با پنجره ${patchSize}×${patchSize} px")
                    appendLine("اسکن چندمقیاسی و چرخشی: ۰.۸۸× تا ۱.۱۴× در ۴ زاویه (۰°، ۹۰°، ۱۸۰°، ۲۷۰°)")
                    append("تعداد هم‌الگوهای یافت‌شده: ${matchedMarkers.size} | کل نشانه‌ها: ${merged.size}")
                },
                technicalOutput = "CountThings Template ($typeLabel) | Patch=${patchSize}px | Matched=${matchedMarkers.size}"
            )
        } finally {
            rgba.release()
            bgr.release()
            gray8u.release()
            gray32f.release()
            bg32f.release()
            normSigned32f.release()
            ocularMask.release()
            if (workBitmap !== sourceBitmap && !workBitmap.isRecycled) {
                workBitmap.recycle()
            }
        }
    }

    private fun computeBlueExcess(bgr: Mat, bgK: Int, dst32f: Mat) {
        val channels = ArrayList<Mat>(3)
        val b32 = Mat()
        val g32 = Mat()
        val r32 = Mat()
        val rgMix = Mat()
        val rawBlue = Mat()
        val blueBg = Mat()
        try {
            Core.split(bgr, channels)
            channels[0].convertTo(b32, CvType.CV_32F)
            channels[1].convertTo(g32, CvType.CV_32F)
            channels[2].convertTo(r32, CvType.CV_32F)
            Core.addWeighted(r32, 0.55, g32, 0.45, 0.0, rgMix)
            Core.subtract(b32, rgMix, rawBlue)
            Imgproc.GaussianBlur(rawBlue, blueBg, Size(bgK.toDouble(), bgK.toDouble()), bgK * 0.35)
            Core.subtract(rawBlue, blueBg, dst32f)
        } finally {
            channels.forEach { it.release() }
            b32.release()
            g32.release()
            r32.release()
            rgMix.release()
            rawBlue.release()
            blueBg.release()
        }
    }

    private fun buildOcularMask(gray8u: Mat, dstMask: Mat) {
        val w = gray8u.cols()
        val h = gray8u.rows()
        val blurred = Mat()
        val rawMask = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(11.0, 11.0))
        try {
            Imgproc.GaussianBlur(gray8u, blurred, Size(15.0, 15.0), 5.0)
            // Compute median brightness of the central 50% crop to handle both brightfield & dark phase slides
            val centerRect = Rect(w / 4, h / 4, w / 2, h / 2)
            val centerCrop = Mat(blurred, centerRect)
            val meanCenter = Core.mean(centerCrop).`val`[0]
            centerCrop.release()

            val floor = min(56.0, max(20.0, meanCenter * 0.38))
            Imgproc.threshold(blurred, rawMask, floor, 255.0, Imgproc.THRESH_BINARY)
            Imgproc.erode(rawMask, dstMask, kernel)
        } finally {
            blurred.release()
            rawMask.release()
            kernel.release()
        }
    }

    private fun estimateRobustNoiseSigma(normData: FloatArray, ocularBytes: ByteArray, w: Int, h: Int): Float {
        // Sample evenly across lit interior to compute robust MAD of pixel contrast
        val absSamples = ArrayList<Float>(4096)
        val step = max(2, min(w, h) / 64)
        var y = step
        while (y < h - step) {
            val row = y * w
            var x = step
            while (x < w - step) {
                if (ocularBytes[row + x] != 0.toByte()) {
                    absSamples.add(abs(normData[row + x]))
                }
                x += step
            }
            y += step
        }
        if (absSamples.isEmpty()) return 8f
        absSamples.sort()
        val mad = absSamples[absSamples.size / 2]
        return (mad * 1.4826f).coerceIn(3.5f, 22f)
    }

    private fun extractNeubauerGridAndMasks(
        normSigned32f: Mat,
        ocularBytes: ByteArray,
        w: Int,
        h: Int,
        sigmaNoise: Float
    ): GridExtraction {
        val brightMask = Mat()
        val lines = Mat()
        val lineMaskMat = Mat.zeros(h, w, CvType.CV_8UC1)
        val interMaskMat = Mat.zeros(h, w, CvType.CV_8UC1)

        val vertLines = ArrayList<Float>()
        val horizLines = ArrayList<Float>()

        try {
            val brightThresh = max(14.0, sigmaNoise * 1.95)
            Imgproc.threshold(normSigned32f, brightMask, brightThresh, 255.0, Imgproc.THRESH_BINARY)
            brightMask.convertTo(brightMask, CvType.CV_8U)

            // Mask out dark ocular corners so vignette boundary isn't turned into lines
            val ocularMat = Mat(h, w, CvType.CV_8UC1)
            ocularMat.put(0, 0, ocularBytes)
            Core.bitwise_and(brightMask, ocularMat, brightMask)
            ocularMat.release()

            val minDim = min(w, h).toDouble()
            Imgproc.HoughLinesP(
                brightMask,
                lines,
                1.0,
                PI / 180.0,
                105,
                minDim * 0.16,
                22.0
            )

            data class Seg(val x1: Double, val y1: Double, val x2: Double, val y2: Double, val angleDeg: Double)
            val allSegs = ArrayList<Seg>()
            for (i in 0 until lines.rows()) {
                val vec = lines.get(i, 0) ?: continue
                val x1 = vec[0]; val y1 = vec[1]; val x2 = vec[2]; val y2 = vec[3]
                val ang = ((Math.toDegrees(kotlin.math.atan2(y2 - y1, x2 - x1)) % 180.0) + 180.0) % 180.0
                allSegs.add(Seg(x1, y1, x2, y2, ang))
            }

            // Partition into near-horizontal (within +-22 deg of 0/180) and near-vertical (within +-22 deg of 90)
            val hSegs = allSegs.filter { it.angleDeg <= 22.0 || it.angleDeg >= 158.0 }
            val vSegs = allSegs.filter { abs(it.angleDeg - 90.0) <= 22.0 }

            val hColor = Scalar(255.0)
            for (s in hSegs) {
                Imgproc.line(lineMaskMat, Point(s.x1, s.y1), Point(s.x2, s.y2), hColor, 7)
                // Normalized Y position at image vertical center
                val dx = s.x2 - s.x1
                val yMid = if (abs(dx) > 1.0) s.y1 + (s.y2 - s.y1) * ((w * 0.5 - s.x1) / dx) else (s.y1 + s.y2) * 0.5
                val ny = (yMid / h).toFloat()
                if (ny in 0.03f..0.97f) horizLines.add(ny)
            }
            for (s in vSegs) {
                Imgproc.line(lineMaskMat, Point(s.x1, s.y1), Point(s.x2, s.y2), hColor, 7)
                val dy = s.y2 - s.y1
                val xMid = if (abs(dy) > 1.0) s.x1 + (s.x2 - s.x1) * ((h * 0.5 - s.y1) / dy) else (s.x1 + s.x2) * 0.5
                val nx = (xMid / w).toFloat()
                if (nx in 0.03f..0.97f) vertLines.add(nx)
            }

            // Compute exact intersections between detected hSegs & vSegs to guard against bright '+' junction artifacts
            for (hs in hSegs) {
                for (vs in vSegs) {
                    val pt = intersectSegmentsExtended(hs.x1, hs.y1, hs.x2, hs.y2, vs.x1, vs.y1, vs.x2, vs.y2)
                    if (pt != null && pt.x in 4.0..(w - 4.0) && pt.y in 4.0..(h - 4.0)) {
                        Imgproc.circle(interMaskMat, pt, 13, hColor, -1)
                    }
                }
            }

            val dedupV = mergeCloseGridCoords(vertLines)
            val dedupH = mergeCloseGridCoords(horizLines)

            val lineBytes = ByteArray(w * h)
            lineMaskMat.get(0, 0, lineBytes)
            val interBytes = ByteArray(w * h)
            interMaskMat.get(0, 0, interBytes)

            val smallSquares = if (dedupV.size >= 2 && dedupH.size >= 2) {
                ((dedupV.size - 1) * (dedupH.size - 1)).coerceIn(1, 16)
            } else 16

            return GridExtraction(
                grid = DetectedNeubauerGrid(
                    verticalLines = dedupV,
                    horizontalLines = dedupH,
                    detectedSmallSquares = smallSquares,
                    detectedLargeSquares = 1
                ),
                lineMask = lineBytes,
                intersectionMask = interBytes
            )
        } finally {
            brightMask.release()
            lines.release()
            lineMaskMat.release()
            interMaskMat.release()
        }
    }

    private fun intersectSegmentsExtended(
        x1: Double, y1: Double, x2: Double, y2: Double,
        x3: Double, y3: Double, x4: Double, y4: Double
    ): Point? {
        val denom = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4)
        if (abs(denom) < 1e-4) return null
        val t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / denom
        val u = -((x1 - x2) * (y1 - y3) - (y1 - y2) * (x1 - x3)) / denom
        // Allow small extension (margin ~0.12 of segment length)
        if (t !in -0.12..1.12 || u !in -0.12..1.12) return null
        val ix = x1 + t * (x2 - x1)
        val iy = y1 + t * (y2 - y1)
        return Point(ix, iy)
    }

    private fun mergeCloseGridCoords(coords: List<Float>): List<Float> {
        if (coords.isEmpty()) return emptyList()
        val sorted = coords.sorted()
        val clusters = ArrayList<MutableList<Float>>()
        for (c in sorted) {
            if (clusters.isEmpty() || abs(c - clusters.last().average().toFloat()) > 0.042f) {
                clusters.add(mutableListOf(c))
            } else {
                clusters.last().add(c)
            }
        }
        return clusters.map { it.average().toFloat() }.take(7)
    }

    private fun detectLiveCellsMultiScaleDoG(
        normSigned32f: Mat,
        normData: FloatArray,
        ocularBytes: ByteArray,
        gridInfo: GridExtraction,
        w: Int,
        h: Int,
        sigmaNoise: Float
    ): List<CandidateCell> {
        val scales = floatArrayOf(4.2f, 6.0f, 8.2f, 10.6f)
        val rawCandidates = ArrayList<CandidateCell>(512)

        val blurInner = Mat()
        val blurOuter = Mat()
        val dog = Mat()
        val dilated = Mat()

        try {
            val dogData = FloatArray(w * h)
            val dilData = FloatArray(w * h)

            for (radius in scales) {
                val sIn = (radius * 0.42).coerceAtLeast(1.1)
                val sOut = (radius * 1.18).coerceAtLeast(2.4)
                val kIn = ((sIn * 4.0).toInt() or 1).coerceIn(3, 21)
                val kOut = ((sOut * 4.0).toInt() or 1).coerceIn(7, 45)

                Imgproc.GaussianBlur(normSigned32f, blurInner, Size(kIn.toDouble(), kIn.toDouble()), sIn)
                Imgproc.GaussianBlur(normSigned32f, blurOuter, Size(kOut.toDouble(), kOut.toDouble()), sOut)
                Core.subtract(blurInner, blurOuter, dog)

                val nmsK = ((radius * 1.15f).roundToInt() or 1).coerceIn(5, 13)
                val nmsKernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(nmsK.toDouble(), nmsK.toDouble()))
                Imgproc.dilate(dog, dilated, nmsKernel)
                nmsKernel.release()

                dog.get(0, 0, dogData)
                dilated.get(0, 0, dilData)

                val minDog = max(4.2f, sigmaNoise * 0.65f)
                val margin = (radius * 1.7f).roundToInt().coerceAtLeast(8)

                for (y in margin until h - margin) {
                    val rowOff = y * w
                    for (x in margin until w - margin) {
                        val idx = rowOff + x
                        if (ocularBytes[idx] == 0.toByte()) continue

                        val dVal = dogData[idx]
                        if (dVal < minDog || dVal < dilData[idx] - 1e-4f) continue
                        if (normData[idx] < max(5f, sigmaNoise * 0.7f)) continue

                        val inIntersection = gridInfo.intersectionMask[idx] != 0.toByte()
                        val onLine = gridInfo.lineMask[idx] != 0.toByte()

                        val ring = measureDarkMembraneRing(
                            normData = normData,
                            w = w,
                            h = h,
                            cx = x,
                            cy = y,
                            radius = radius,
                            sigmaNoise = sigmaNoise
                        )

                        // Key CountThings CV guard against grid lines, intersections, and elongated scratches:
                        // Live cells always wrap a bright center inside a 360-degree dark circular membrane!
                        val requiredSectors = when {
                            inIntersection -> 9
                            onLine -> 8
                            else -> 7
                        }
                        val requiredOpposite = when {
                            inIntersection -> 4
                            else -> 3
                        }
                        if (ring.darkSectors < requiredSectors || ring.oppositePairs < requiredOpposite) continue

                        // Reject elongated scratches/fibers where brightness extends far along one axis without dipping
                        if (ring.elongatedBrightAxis) continue

                        val contrastScore = (ring.coreBrightness - ring.meanRingDip) / max(8f, sigmaNoise)
                        val sectorRatio = ring.darkSectors / 12f
                        val pairRatio = ring.oppositePairs / 6f
                        val rawQuality = (sectorRatio * 0.45f + pairRatio * 0.30f + (contrastScore / 6.5f).coerceIn(0f, 1f) * 0.25f)
                        val intersectionPenalty = if (inIntersection && ring.darkSectors < 11) 0.14f else 0f
                        val finalQuality = (rawQuality - intersectionPenalty).coerceIn(0f, 1f)
                        val conf = (finalQuality * 62f + 36f).roundToInt().coerceIn(30, 98)

                        rawCandidates.add(
                            CandidateCell(
                                x = x.toFloat(),
                                y = y.toFloat(),
                                radius = radius,
                                isLive = true,
                                isOnGridLine = onLine,
                                isOverlapping = false,
                                confidence = conf,
                                score = finalQuality * 100f + contrastScore
                            )
                        )
                    }
                }
            }
        } finally {
            blurInner.release()
            blurOuter.release()
            dog.release()
            dilated.release()
        }

        // Cross-scale NMS: keep strongest cell center; allow close doublets (>= 0.85 * avg diameter)
        val sorted = rawCandidates.sortedByDescending { it.score }
        val kept = ArrayList<CandidateCell>(sorted.size)
        for (c in sorted) {
            val duplicate = kept.any { existing ->
                val minDist = max(6.8f, min(existing.radius, c.radius) * 1.15f)
                hypot(existing.x - c.x, existing.y - c.y) < minDist
            }
            if (!duplicate) {
                kept.add(c)
            }
        }
        return kept
    }

    private data class RingMetrics(
        val darkSectors: Int,
        val oppositePairs: Int,
        val coreBrightness: Float,
        val meanRingDip: Float,
        val elongatedBrightAxis: Boolean
    )

    private fun measureDarkMembraneRing(
        normData: FloatArray,
        w: Int,
        h: Int,
        cx: Int,
        cy: Int,
        radius: Float,
        sigmaNoise: Float
    ): RingMetrics {
        // Compute small inner core mean (3x3)
        var coreSum = 0f
        var coreN = 0
        for (dy in -1..1) {
            val py = (cy + dy).coerceIn(0, h - 1)
            for (dx in -1..1) {
                val px = (cx + dx).coerceIn(0, w - 1)
                coreSum += normData[py * w + px]
                coreN++
            }
        }
        val coreBrightness = coreSum / coreN

        val sectorDark = BooleanArray(12)
        val sectorAlwaysBright = BooleanArray(12)
        var darkSum = 0f
        var darkCount = 0

        val rMin = max(2.2f, radius * 0.72f)
        val rMax = max(rMin + 2f, radius * 1.68f)
        val rFar = max(rMax + 2f, radius * 2.15f)

        for (s in 0 until 12) {
            val theta = (s * 30.0) * (PI / 180.0)
            val cosT = cos(theta).toFloat()
            val sinT = sin(theta).toFloat()

            var minOnRay = Float.MAX_VALUE
            var stepR = rMin
            while (stepR <= rMax + 0.01f) {
                val sx = (cx + cosT * stepR).roundToInt().coerceIn(0, w - 1)
                val sy = (cy + sinT * stepR).roundToInt().coerceIn(0, h - 1)
                val v = normData[sy * w + sx]
                if (v < minOnRay) minOnRay = v
                stepR += 1.0f
            }

            // Dark membrane dip: clearly darker than background OR strongly below core
            val dipDrop = coreBrightness - minOnRay
            if (minOnRay < -2.2f && dipDrop >= max(9.5f, sigmaNoise * 1.25f)) {
                sectorDark[s] = true
                darkSum += minOnRay
                darkCount++
            } else {
                // Check if bright ridge continues further along this direction (grid line or scratch)
                val fx = (cx + cosT * rFar).roundToInt().coerceIn(0, w - 1)
                val fy = (cy + sinT * rFar).roundToInt().coerceIn(0, h - 1)
                if (normData[fy * w + fx] > max(6f, sigmaNoise * 0.8f) && minOnRay > 0f) {
                    sectorAlwaysBright[s] = true
                }
            }
        }

        var oppositePairs = 0
        var elongatedAxes = 0
        for (s in 0 until 6) {
            if (sectorDark[s] && sectorDark[s + 6]) {
                oppositePairs++
            }
            if (sectorAlwaysBright[s] && sectorAlwaysBright[s + 6]) {
                elongatedAxes++
            }
        }

        val meanDip = if (darkCount > 0) darkSum / darkCount else 0f
        return RingMetrics(
            darkSectors = darkCount,
            oppositePairs = oppositePairs,
            coreBrightness = coreBrightness,
            meanRingDip = meanDip,
            elongatedBrightAxis = elongatedAxes >= 1 && darkCount < 9
        )
    }

    private fun detectDeadCellsDistanceTransform(
        normSigned32f: Mat,
        blueExcess32f: Mat,
        normData: FloatArray,
        blueData: FloatArray,
        ocularBytes: ByteArray,
        gridInfo: GridExtraction,
        existingLive: List<CandidateCell>,
        w: Int,
        h: Int,
        sigmaNoise: Float
    ): List<CandidateCell> {
        val darkness = Mat()
        val boostedBlue = Mat()
        val deadScore = Mat()
        val deadBin = Mat()
        val distMap = Mat()
        val distDilated = Mat()

        val morphOpen = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(3.0, 3.0))
        val nmsKernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(9.0, 9.0))

        try {
            // Dead (Trypan Blue stained) cells are dark overall (normSigned < 0) + optionally extra blue
            Core.multiply(normSigned32f, Scalar(-1.0), darkness)
            Imgproc.threshold(blueExcess32f, boostedBlue, 0.0, 100.0, Imgproc.THRESH_TOZERO)
            Core.addWeighted(darkness, 1.0, boostedBlue, 0.45, 0.0, deadScore)

            val deadThresh = max(13.5, sigmaNoise * 1.95)
            Imgproc.threshold(deadScore, deadBin, deadThresh, 255.0, Imgproc.THRESH_BINARY)
            deadBin.convertTo(deadBin, CvType.CV_8U)

            val ocularMat = Mat(h, w, CvType.CV_8UC1)
            ocularMat.put(0, 0, ocularBytes)
            Core.bitwise_and(deadBin, ocularMat, deadBin)
            ocularMat.release()

            Imgproc.morphologyEx(deadBin, deadBin, Imgproc.MORPH_OPEN, morphOpen)

            // Distance Transform splits touching dead cell clusters into distinct peak centers
            Imgproc.distanceTransform(deadBin, distMap, Imgproc.DIST_L2, 5)
            Imgproc.dilate(distMap, distDilated, nmsKernel)

            val distData = FloatArray(w * h)
            val dilData = FloatArray(w * h)
            distMap.get(0, 0, distData)
            distDilated.get(0, 0, dilData)

            val rawDead = ArrayList<CandidateCell>(128)
            val margin = 10
            for (y in margin until h - margin) {
                val rowOff = y * w
                for (x in margin until w - margin) {
                    val idx = rowOff + x
                    if (ocularBytes[idx] == 0.toByte()) continue
                    val rad = distData[idx]
                    // Valid mammalian cell radius at 1280px working scale: 2.8px .. 13.5px
                    if (rad !in 2.8f..13.5f) continue
                    if (rad < dilData[idx] - 1e-4f) continue

                    // Must have a genuinely DARK center (rejects outer dark rings of bright live cells!)
                    val centerNorm = normData[idx]
                    if (centerNorm > -max(7.0f, sigmaNoise * 1.05f)) continue

                    // Check compact dark core (mean of small neighborhood must be dark, not bright live core nearby)
                    var coreSum = 0f
                    var brightPixels = 0
                    for (dy in -2..2) {
                        val nr = (y + dy) * w
                        for (dx in -2..2) {
                            val v = normData[nr + x + dx]
                            coreSum += v
                            if (v > 5f) brightPixels++
                        }
                    }
                    if (brightPixels > 2 || coreSum / 25f > -5.5f) continue

                    // Avoid duplicating a detected Live cell center
                    val overlapsLive = existingLive.any { live ->
                        hypot(live.x - x, live.y - y) < max(7.5f, (live.radius + rad) * 0.85f)
                    }
                    if (overlapsLive) continue

                    val inIntersection = gridInfo.intersectionMask[idx] != 0.toByte()
                    val onLine = gridInfo.lineMask[idx] != 0.toByte()
                    if (inIntersection && centerNorm > -sigmaNoise * 2.2f) continue

                    val darknessStrength = (-centerNorm + max(0f, blueData[idx]) * 0.45f) / max(8f, sigmaNoise)
                    val radiusBonus = if (rad in 3.5f..10.5f) 1.0f else 0.78f
                    val quality = ((darknessStrength / 5.5f).coerceIn(0f, 1f) * 0.72f + radiusBonus * 0.28f)
                    val conf = (quality * 60f + 35f).roundToInt().coerceIn(30, 97)

                    rawDead.add(
                        CandidateCell(
                            x = x.toFloat(),
                            y = y.toFloat(),
                            radius = rad.coerceIn(4f, 11f),
                            isLive = false,
                            isOnGridLine = onLine,
                            isOverlapping = false,
                            confidence = conf,
                            score = quality * 100f + darknessStrength
                        )
                    )
                }
            }

            // NMS for dead peaks
            val sorted = rawDead.sortedByDescending { it.score }
            val kept = ArrayList<CandidateCell>(sorted.size)
            for (c in sorted) {
                val dup = kept.any { existing ->
                    hypot(existing.x - c.x, existing.y - c.y) < max(7.0f, min(existing.radius, c.radius) * 1.2f)
                }
                if (!dup) kept.add(c)
            }
            return kept
        } finally {
            darkness.release()
            boostedBlue.release()
            deadScore.release()
            deadBin.release()
            distMap.release()
            distDilated.release()
            morphOpen.release()
            nmsKernel.release()
        }
    }

    private fun snapToCellCenter(
        normData: FloatArray,
        w: Int,
        h: Int,
        tapX: Int,
        tapY: Int,
        lookForBrightCore: Boolean
    ): Pair<Int, Int> {
        var bestX = tapX
        var bestY = tapY
        var bestVal = Float.NEGATIVE_INFINITY
        val searchR = 16
        for (dy in -searchR..searchR) {
            val py = (tapY + dy).coerceIn(6, h - 7)
            val row = py * w
            for (dx in -searchR..searchR) {
                if (dx * dx + dy * dy > searchR * searchR) continue
                val px = (tapX + dx).coerceIn(6, w - 7)
                // Smooth 3x3 score to snap on cell body center rather than 1-px noise
                var localSum = 0f
                for (sy in -1..1) {
                    val sRow = (py + sy) * w
                    for (sx in -1..1) {
                        localSum += normData[sRow + px + sx]
                    }
                }
                val score = if (lookForBrightCore) localSum else -localSum
                // Slight preference for proximity to user tap
                val distPenalty = hypot(dx.toFloat(), dy.toFloat()) * 1.5f
                val adjusted = score - distPenalty
                if (adjusted > bestVal) {
                    bestVal = adjusted
                    bestX = px
                    bestY = py
                }
            }
        }
        return bestX to bestY
    }

    private fun estimateTemplateHalfSize(
        normData: FloatArray,
        w: Int,
        h: Int,
        cx: Int,
        cy: Int,
        isLive: Boolean
    ): Int {
        // Scan radially outward from cell center to find membrane extent
        var bestRingR = 9
        var extremeVal = if (isLive) Float.MAX_VALUE else Float.MIN_VALUE
        for (r in 4..14) {
            var ringSum = 0f
            val nSamples = 12
            for (k in 0 until nSamples) {
                val theta = (k * (360.0 / nSamples)) * (PI / 180.0)
                val sx = (cx + cos(theta) * r).roundToInt().coerceIn(0, w - 1)
                val sy = (cy + sin(theta) * r).roundToInt().coerceIn(0, h - 1)
                ringSum += normData[sy * w + sx]
            }
            val meanRing = ringSum / nSamples
            if (isLive) {
                if (meanRing < extremeVal) {
                    extremeVal = meanRing
                    bestRingR = r
                }
            } else {
                // For dark dead cells, stop when radial average crosses back near background (>= -3)
                if (meanRing > -3f && r >= 5) {
                    bestRingR = r
                    break
                }
            }
        }
        return (bestRingR + 4).coerceIn(9, 16)
    }

    private fun finalizeMarkers(
        candidates: List<CandidateCell>,
        w: Int,
        h: Int,
        nextIdStart: Long
    ): List<CellPointMarker> {
        if (candidates.isEmpty()) return emptyList()
        var nextId = nextIdStart
        val overlapDistPx = max(14f, min(w, h) * 0.019f)

        return candidates.mapIndexed { idx, c ->
            val hasCloseNeighbor = candidates.indices.any { other ->
                other != idx && hypot(candidates[other].x - c.x, candidates[other].y - c.y) <= overlapDistPx
            }
            CellPointMarker(
                id = nextId++,
                normalizedX = (c.x / w.toFloat()).coerceIn(0.005f, 0.995f),
                normalizedY = (c.y / h.toFloat()).coerceIn(0.005f, 0.995f),
                isLive = c.isLive,
                isOnGridLine = c.isOnGridLine,
                isOverlapping = c.isOverlapping || hasCloseNeighbor,
                confidence = c.confidence
            )
        }
    }

    private fun scaleForCv(bitmap: Bitmap, maxDim: Int): Bitmap {
        val bw = bitmap.width.coerceAtLeast(1)
        val bh = bitmap.height.coerceAtLeast(1)
        return if (bw <= maxDim && bh <= maxDim) {
            bitmap
        } else {
            val scale = min(maxDim.toFloat() / bw, maxDim.toFloat() / bh)
            val nw = (bw * scale).roundToInt().coerceAtLeast(1)
            val nh = (bh * scale).roundToInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, nw, nh, true)
        }
    }
}
