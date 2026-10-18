package com.inouiw.kilowatt.ar

import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.inouiw.kilowatt.domain.Geometry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

// The balcony surface ARCore measured. Corners are screen pixels, span and area are metres.
data class BalconyPlane(
    val cornersPx: List<Pair<Float, Float>>,
    val spanM: Float,
    val areaM2: Float,
    // True once span and area have held steady for about two seconds
    val stable: Boolean,
)

data class BalconyArState(
    val cameraTracking: Boolean = false,
    val plane: BalconyPlane? = null,
)

// Draws the ARCore camera image as the background and works out the balcony surface each frame.
class ArBalconyRenderer(private val session: Session) : GLSurfaceView.Renderer {

    private val _state = MutableStateFlow(BalconyArState())
    val state: StateFlow<BalconyArState> = _state.asStateFlow()

    private var cameraTexture = 0
    private var program = 0
    private var positionLoc = 0
    private var texCoordLoc = 0
    private var textureLoc = 0
    private var viewWidth = 1
    private var viewHeight = 1

    private val quadCoords: FloatBuffer = floatBufferOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
    private val quadTexCoords: FloatBuffer = floatBufferOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)
    private val viewMatrix = FloatArray(16)
    private val projMatrix = FloatArray(16)

    // Recent span and area readings, used to tell a steady measurement from a drifting one
    private val recentSpans = ArrayDeque<Float>()
    private val recentAreas = ArrayDeque<Float>()
    private var lastLogMillis = 0L

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        cameraTexture = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTexture)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        session.setCameraTextureName(cameraTexture)

        program = linkProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionLoc = GLES20.glGetAttribLocation(program, "a_Position")
        texCoordLoc = GLES20.glGetAttribLocation(program, "a_TexCoord")
        textureLoc = GLES20.glGetUniformLocation(program, "u_Texture")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        viewWidth = width
        viewHeight = height
        // The app is portrait only, so the display rotation is 0 (Surface.ROTATION_0)
        session.setDisplayGeometry(0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val frame = try {
            session.update()
        } catch (e: Exception) {
            Log.e(TAG, "Session update failed", e)
            return
        }

        if (frame.hasDisplayGeometryChanged()) {
            frame.transformCoordinates2d(
                Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES, quadCoords,
                Coordinates2d.TEXTURE_NORMALIZED, quadTexCoords,
            )
        }
        drawCameraBackground()

        val camera = frame.camera
        if (camera.trackingState != TrackingState.TRACKING) {
            recentSpans.clear()
            recentAreas.clear()
            _state.value = BalconyArState(cameraTracking = false)
            return
        }
        camera.getViewMatrix(viewMatrix, 0)
        camera.getProjectionMatrix(projMatrix, 0, NEAR, FAR)

        val plane = findBalconyPlane(session.getAllTrackables(Plane::class.java))
        if (plane == null) {
            recentSpans.clear()
            recentAreas.clear()
        }
        _state.value = BalconyArState(cameraTracking = true, plane = plane)
    }

    // The largest horizontal floor surface that is tracked. The balcony floor is the usual target.
    private fun findBalconyPlane(planes: Collection<Plane>): BalconyPlane? {
        val plane = planes
            .filter {
                it.trackingState == TrackingState.TRACKING &&
                    it.subsumedBy == null &&
                    it.type == Plane.Type.HORIZONTAL_UPWARD_FACING
            }
            .maxByOrNull { it.extentX * it.extentZ }
            ?: return null

        // Polygon vertices are (x, z) pairs in the plane's own coordinate space
        val buffer = plane.polygon.duplicate()
        buffer.rewind()
        val local = mutableListOf<Pair<Float, Float>>()
        while (buffer.remaining() >= 2) {
            local.add(Pair(buffer.get(), buffer.get()))
        }
        if (local.size < 3) return null

        val corners = local.mapNotNull { (x, z) ->
            val world = plane.centerPose.transformPoint(floatArrayOf(x, 0f, z))
            project(world)
        }
        if (corners.size < 3) return null

        val span = maxOf(plane.extentX, plane.extentZ)
        val area = Geometry.polygonArea(local)

        recentSpans.addLast(span)
        recentAreas.addLast(area)
        while (recentSpans.size > STABLE_FRAMES) {
            recentSpans.removeFirst()
            recentAreas.removeFirst()
        }
        val stable = recentSpans.size >= STABLE_FRAMES && Geometry.isSteady(recentSpans) && Geometry.isSteady(recentAreas)

        val now = System.currentTimeMillis()
        if (now - lastLogMillis >= 1000) {
            Log.i(TAG, String.format(java.util.Locale.US, "span=%.2f m area=%.2f m2 corners=%d stable=%s", span, area, corners.size, stable))
            lastLogMillis = now
        }

        return BalconyPlane(
            cornersPx = corners,
            spanM = span,
            areaM2 = area,
            stable = stable,
        )
    }

    // World point to screen pixels using ARCore's view and projection matrices
    private fun project(world: FloatArray): Pair<Float, Float>? {
        val cam = multiply(viewMatrix, floatArrayOf(world[0], world[1], world[2], 1f))
        val clip = multiply(projMatrix, cam)
        if (clip[3] <= 0f) return null
        val ndcX = clip[0] / clip[3]
        val ndcY = clip[1] / clip[3]
        return Pair((ndcX + 1f) / 2f * viewWidth, (1f - ndcY) / 2f * viewHeight)
    }

    private fun drawCameraBackground() {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTexture)
        GLES20.glUniform1i(textureLoc, 0)

        GLES20.glVertexAttribPointer(positionLoc, 2, GLES20.GL_FLOAT, false, 0, quadCoords)
        GLES20.glEnableVertexAttribArray(positionLoc)
        GLES20.glVertexAttribPointer(texCoordLoc, 2, GLES20.GL_FLOAT, false, 0, quadTexCoords)
        GLES20.glEnableVertexAttribArray(texCoordLoc)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(positionLoc)
        GLES20.glDisableVertexAttribArray(texCoordLoc)

        GLES20.glDepthMask(true)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }

    private fun linkProgram(vertex: String, fragment: String): Int {
        fun compile(type: Int, source: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            return shader
        }
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, compile(GLES20.GL_VERTEX_SHADER, vertex))
        GLES20.glAttachShader(p, compile(GLES20.GL_FRAGMENT_SHADER, fragment))
        GLES20.glLinkProgram(p)
        return p
    }

    companion object {
        const val TAG = "ArBalconyRenderer"
        const val NEAR = 0.1f
        const val FAR = 100f

        // About two seconds of readings at ARCore's frame rate
        const val STABLE_FRAMES = 60

        private const val VERTEX_SHADER = """
            attribute vec4 a_Position;
            attribute vec2 a_TexCoord;
            varying vec2 v_TexCoord;
            void main() {
                gl_Position = a_Position;
                v_TexCoord = a_TexCoord;
            }
        """

        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES u_Texture;
            varying vec2 v_TexCoord;
            void main() {
                gl_FragColor = texture2D(u_Texture, v_TexCoord);
            }
        """

        fun floatBufferOf(vararg values: Float): FloatBuffer =
            ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(values)
                position(0)
            }

        // Column-major 4x4 matrix times a 4-vector
        fun multiply(m: FloatArray, v: FloatArray): FloatArray {
            val out = FloatArray(4)
            for (row in 0 until 4) {
                out[row] = m[row] * v[0] + m[4 + row] * v[1] + m[8 + row] * v[2] + m[12 + row] * v[3]
            }
            return out
        }
    }
}
