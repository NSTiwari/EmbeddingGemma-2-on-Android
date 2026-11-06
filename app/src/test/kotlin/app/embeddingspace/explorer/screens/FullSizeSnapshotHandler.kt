package app.embeddingspace.explorer.screens

import app.cash.paparazzi.Snapshot
import app.cash.paparazzi.SnapshotHandler
import java.io.File
import java.lang.reflect.Proxy

/**
 * Writes each snapshot as a full size PNG named after the test method. No comparison, no scaling.
 *
 * The Android unit test classpath has no java.awt, so the image is written through reflection
 * instead of a direct ImageIO call.
 */
fun fullSizeSnapshotHandler(dir: File, name: (Snapshot) -> String = { it.testName.methodName }): SnapshotHandler {
    val frameType = SnapshotHandler.FrameHandler::class.java
    val loader = SnapshotHandler::class.java.classLoader

    fun writePng(image: Any, file: File) {
        val imageIo = Class.forName("javax.imageio.ImageIO")
        val rendered = Class.forName("java.awt.image.RenderedImage")
        imageIo.getMethod("write", rendered, String::class.java, File::class.java).invoke(null, image, "png", file)
    }

    fun frameHandler(snapshot: Snapshot): SnapshotHandler.FrameHandler =
        Proxy.newProxyInstance(loader, arrayOf(frameType)) { _, method, args ->
            if (method.name == "handle") {
                dir.mkdirs()
                writePng(args[0], File(dir, name(snapshot) + ".png"))
            }
            null
        } as SnapshotHandler.FrameHandler

    return Proxy.newProxyInstance(loader, arrayOf(SnapshotHandler::class.java)) { _, method, args ->
        if (method.name == "newFrameHandler") frameHandler(args[0] as Snapshot) else null
    } as SnapshotHandler
}
