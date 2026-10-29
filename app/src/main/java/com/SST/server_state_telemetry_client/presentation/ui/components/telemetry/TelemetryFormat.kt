package com.SST.server_state_telemetry_client.presentation.ui.components.telemetry

/** Keep SSTD byte-per-second values in byte units when selecting a display scale. */
fun formatBytesPerSec(bps: Long): String {
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    val v = bps.toDouble()
    return when {
        v >= gb -> String.format("%.2f GB/s", v / gb)
        v >= mb -> String.format("%.2f MB/s", v / mb)
        v >= kb -> String.format("%.2f KB/s", v / kb)
        else -> "$bps B/s"
    }
}

/** Format the server's seconds as days and a remaining hours/minutes/seconds tuple. */
fun formatUptime(seconds: Long): String {
    val d = seconds / 86400
    val h = (seconds % 86400) / 3600
    val m = (seconds % 3600) / 60
    val sec = seconds % 60
    return "${d}d %02d:%02d:%02d".format(h, m, sec)
}

/** SSTD process usage is already normalized to host capacity, in 0.01% units. */
fun formatDaemonUsage(value: Int?): String =
    if (value == null || value !in 0..10000) "미측정"
    else java.lang.String.format(java.util.Locale.US, "%.2f%%", value / 100.0)
