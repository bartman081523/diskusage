package com.google.android.diskusage.datasource.fast

import com.google.android.diskusage.utils.AppHelper.appContext
import com.google.android.diskusage.utils.DeviceHelper.isDeviceRooted
import org.jetbrains.annotations.Contract
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Reads the serialised directory tree produced by `libscan.so`.
 *
 * The tree is generated through JNI (see `app/src/main/jni/scan.c`). The old
 * approach — executing the shared library as a child process — no longer works
 * on Android 10+: the ELF has no startup routine and the platform restricts
 * executing app-provided binaries. It is only attempted for root mode.
 */
class NativeScannerStream private constructor(
    private val input: InputStream,
    private val process: Process?
) : InputStream() {

    @Throws(IOException::class)
    override fun read(): Int = input.read()

    @Throws(IOException::class)
    override fun read(buffer: ByteArray): Int = input.read(buffer)

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, byteOffset: Int, byteCount: Int): Int =
        input.read(buffer, byteOffset, byteCount)

    @Throws(IOException::class)
    override fun close() {
        try {
            input.close()
        } finally {
            process?.let { awaitExit(it) }
        }
    }

    /**
     * Waits for the child process to terminate, but never blocks forever: a
     * stuck helper used to leave the app on the "Scanning directories" dialog.
     */
    private fun awaitExit(process: Process, timeoutMs: Long = EXIT_TIMEOUT_MS) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            try {
                process.exitValue()
                return
            } catch (e: IllegalThreadStateException) {
                try {
                    Thread.sleep(POLL_INTERVAL_MS)
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }
        process.destroy()
        process.destroyForcibly()
    }

    companion object Factory {
        private const val LIBSCAN = "libscan.so"
        private const val EXIT_TIMEOUT_MS = 5_000L
        private const val POLL_INTERVAL_MS = 20L

        private val nativeAvailable: Boolean = try {
            System.loadLibrary("scan")
            true
        } catch (t: Throwable) {
            Timber.w(t, "libscan.so could not be loaded")
            false
        }

        @JvmStatic
        @Contract("_, _ -> new")
        @Throws(IOException::class)
        fun create(path: String, rootRequired: Boolean): NativeScannerStream {
            if (rootRequired && isDeviceRooted()) {
                return runScannerAsRoot(path)
            }
            if (nativeAvailable) {
                try {
                    val data = scanNative(path)
                    if (data != null && data.isNotEmpty()) {
                        return NativeScannerStream(ByteArrayInputStream(data), null)
                    }
                    Timber.w("native scan of %s produced no data", path)
                } catch (t: Throwable) {
                    Timber.w(t, "native scan of %s failed", path)
                }
            }
            // Fall back to the child process; the caller falls back to the Java
            // scanner if this fails as well.
            return runScanner(path)
        }

        /**
         * Runs the tree walker in-process. Returns the serialised tree, or
         * `null` when it does not fit into the native output buffer.
         */
        @JvmStatic
        private external fun scanNative(path: String): ByteArray?

        private fun runScanner(root: String): NativeScannerStream {
            val libscanPath = "${appContext.applicationInfo.nativeLibraryDir}/$LIBSCAN"
            val process = ProcessBuilder(libscanPath, root)
                .redirectErrorStream(false)
                .start()
            return NativeScannerStream(process.inputStream, process)
        }

        private fun runScannerAsRoot(root: String): NativeScannerStream {
            val libscanPath = "${appContext.applicationInfo.nativeLibraryDir}/$LIBSCAN"
            val su = arrayOf("su", "/system/bin/su", "/system/xbin/su").firstNotNullOfOrNull { candidate ->
                runCatching { ProcessBuilder(candidate).start() }.getOrNull()
            } ?: throw IOException("No su binary found")

            su.outputStream.use { out ->
                out.write("$libscanPath $root\n".toByteArray())
                out.write("exit\n".toByteArray())
                out.flush()
            }
            return NativeScannerStream(su.inputStream, su)
        }
    }
}
