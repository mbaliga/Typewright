// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.capture

import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.engine.trace.GrayscaleRaster
import com.asoc.typewright.engine.trace.RasterFrame
import com.asoc.typewright.engine.trace.coverageOf
import com.asoc.typewright.scripts.sheets.DrawOp
import com.asoc.typewright.scripts.sheets.GuideStyle
import com.asoc.typewright.scripts.sheets.PaperSize
import com.asoc.typewright.scripts.sheets.PrintScript
import com.asoc.typewright.scripts.sheets.PrintSheetSpec
import com.asoc.typewright.scripts.sheets.PrintSheets
import com.asoc.typewright.scripts.sheets.SheetLayout
import com.asoc.typewright.scripts.sheets.SheetRenderer
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Tests only: a printed template sheet with drawings in its boxes, and a picture of it taken a little turned on a desk. THESE ARE
 * SYNTHETIC: the sheet is rendered from the repository's own sheet model and the drawings from outlines handed in; a photograph of a
 * real page, in real light, is an owner-supplied fixture still owed.
 */
object SyntheticSheets {
    /** The sheet [spec] as a flat page of grey pixels at [pxPerMm], with each outline in [art] (by cell number) drawn in pen. */
    fun page(
        spec: PrintSheetSpec,
        art: Map<Int, List<Contour>>,
        paper: PaperSize = PaperSize.A4,
        pxPerMm: Double = 6.0,
        guides: GuideStyle = GuideStyle.LIGHT,
    ): GrayscaleRaster {
        val layout = SheetLayout.of(paper, spec.script)
        val w = (layout.pageWidthMm * pxPerMm).roundToInt()
        val h = (layout.pageHeightMm * pxPerMm).roundToInt()
        val px = IntArray(w * h) { 255 }

        fun fill(
            x0: Double,
            y0: Double,
            x1: Double,
            y1: Double,
            gray: Int,
        ) {
            for (y in max(0, (y0 * pxPerMm).toInt())..minOf(h - 1, (y1 * pxPerMm).toInt())) {
                for (x in max(0, (x0 * pxPerMm).toInt())..minOf(w - 1, (x1 * pxPerMm).toInt())) px[y * w + x] = minOf(px[y * w + x], gray)
            }
        }
        for (op in SheetRenderer.render(spec, layout, guides).ops) {
            when (op) {
                is DrawOp.Rect -> {
                    if (op.fillGray != null) {
                        fill(op.x, op.y, op.x + op.width, op.y + op.height, (op.fillGray!! * 255).roundToInt())
                    } else if (op.strokeGray != null) {
                        val g = (op.strokeGray!! * 255).roundToInt()
                        fill(op.x, op.y, op.x + op.width, op.y + op.strokeMm, g)
                        fill(op.x, op.y + op.height, op.x + op.width, op.y + op.height + op.strokeMm, g)
                        fill(op.x, op.y, op.x + op.strokeMm, op.y + op.height, g)
                        fill(op.x + op.width, op.y, op.x + op.width + op.strokeMm, op.y + op.height, g)
                    }
                }

                is DrawOp.Line -> {
                    val g = (op.gray * 255).roundToInt()
                    fill(minOf(op.x1, op.x2), minOf(op.y1, op.y2), maxOf(op.x1, op.x2) + op.widthMm, maxOf(op.y1, op.y2) + op.widthMm, g)
                }

                is DrawOp.Text -> {
                    Unit
                }
            }
        }
        for ((cell, contours) in art) {
            val box = layout.cellBoxes[cell]
            val bw = (box.width * pxPerMm).roundToInt()
            val bh = (box.height * pxPerMm).roundToInt()
            val frame = RasterFrame(pxPerMm * layout.frame.mmPerUnit, layout.frame.leftUnits, layout.frame.topUnits)
            val coverage = coverageOf(contours, frame, bw, bh)
            val left = (box.left * pxPerMm).roundToInt()
            val top = (box.top * pxPerMm).roundToInt()
            for (y in 0 until bh) {
                for (x in 0 until bw) {
                    val c = coverage[y * bw + x]
                    if (c <= 0.0) continue
                    val at = (top + y) * w + left + x
                    px[at] = minOf(px[at], (255 - c * (255 - PEN)).roundToInt())
                }
            }
        }
        return GrayscaleRaster(w, h, px)
    }

    /** [flat] laid on a darker desk and turned by [degrees], as a camera held a little off the vertical would see it. */
    fun photograph(
        flat: GrayscaleRaster,
        degrees: Double = 3.0,
    ): GrayscaleRaster {
        val margin = (max(flat.width, flat.height) * 0.12).roundToInt()
        val w = flat.width + 2 * margin
        val h = flat.height + 2 * margin
        val out = IntArray(w * h)
        val a = degrees * PI / 180.0
        val cosA = cos(a)
        val sinA = sin(a)
        val cx = w / 2.0
        val cy = h / 2.0
        for (y in 0 until h) {
            for (x in 0 until w) {
                val dx = x - cx
                val dy = y - cy
                val sx = cosA * dx + sinA * dy + flat.width / 2.0
                val sy = -sinA * dx + cosA * dy + flat.height / 2.0
                out[y * w + x] =
                    if (sx < 0 || sy < 0 || sx >= flat.width - 1 || sy >= flat.height - 1) {
                        DESK
                    } else {
                        val x0 = floor(sx).toInt()
                        val y0 = floor(sy).toInt()
                        val fx = sx - x0
                        val fy = sy - y0
                        val v =
                            flat.pixels[y0 * flat.width + x0] * (1 - fx) * (1 - fy) +
                                flat.pixels[y0 * flat.width + x0 + 1] * fx * (1 - fy) +
                                flat.pixels[(y0 + 1) * flat.width + x0] * (1 - fx) * fy +
                                flat.pixels[(y0 + 1) * flat.width + x0 + 1] * fx * fy
                        v.roundToInt().coerceIn(0, 255)
                    }
            }
        }
        return GrayscaleRaster(w, h, out)
    }

    /** [raster] as PNG file bytes, the form a picker hands over. */
    fun png(raster: GrayscaleRaster): ByteArray {
        val image = BufferedImage(raster.width, raster.height, BufferedImage.TYPE_BYTE_GRAY)
        for (y in 0 until raster.height) {
            for (x in 0 until raster.width) {
                val v = raster.pixels[y * raster.width + x]
                image.raster.setSample(x, y, 0, v)
            }
        }
        val out = ByteArrayOutputStream()
        check(ImageIO.write(image, "png", out)) { "no PNG writer" }
        return out.toByteArray()
    }

    /** The first Latin sheet's spec. */
    fun latinSheet(index: Int = 0): PrintSheetSpec = PrintSheets.forScript(PrintScript.LATIN).sheets[index]

    private const val PEN = 20
    private const val DESK = 70
}
