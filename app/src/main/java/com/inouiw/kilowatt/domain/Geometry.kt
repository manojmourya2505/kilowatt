package com.inouiw.kilowatt.domain

import kotlin.math.abs

object Geometry {

    /** Area of a polygon from its (x, z) corners, in square metres (shoelace formula). */
    fun polygonArea(points: List<Pair<Float, Float>>): Float {
        var sum = 0f
        for (i in points.indices) {
            val (x1, z1) = points[i]
            val (x2, z2) = points[(i + 1) % points.size]
            sum += x1 * z2 - x2 * z1
        }
        return abs(sum) / 2f
    }

    /** True when the highest and lowest values are within 10% of each other. */
    fun isSteady(values: Collection<Float>): Boolean {
        val high = values.max()
        val low = values.min()
        return high - low <= 0.1f * high
    }
}
