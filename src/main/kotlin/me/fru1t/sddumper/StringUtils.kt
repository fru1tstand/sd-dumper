package me.fru1t.sddumper

object StringUtils {
  /** Returns a human readable string of storage space in bytes, kilobytes, megabytes, or gigabytes. */
  fun bytesToString(bytes: Long): String {
    if (bytes < 1024) {
      return bytes.toString() + "B"
    }

    val kb = bytes / 1024
    if (kb < 1024) {
      return kb.toString() + "KB"
    }

    val mb = kb / 1024
    if (mb < 2048) {
      return mb.toString() + "MB"
    }

    val gb = mb / 1024
    return gb.toString() + "GB"
  }

  /** Returns a human readable string of time in mm:ss format. */
  fun secondsToString(seconds: Long): String {
    val minutesLeft = seconds / 60
    val minutesLeftString = if (minutesLeft < 10) {
      "0$minutesLeft"
    } else {
      minutesLeft
    }

    val secondsLeft = seconds % 60
    val secondsLeftString = if (seconds < 10) {
      "0$secondsLeft"
    } else {
      secondsLeft
    }

    return "$minutesLeftString:$secondsLeftString"
  }
}