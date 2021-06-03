package me.fru1t.sddumper

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.*
import java.util.stream.Collectors
import kotlin.IllegalStateException
import kotlin.math.round
import kotlin.system.exitProcess

fun main() {
  SDDumperApplication().run()
}

class SDDumperApplication {
  companion object {
    private const val MAX_DRIVE_CHECKS_BEFORE_SHUTDOWN = 120
    private const val WAIT_TIME_BETWEEN_DRIVE_CHECKS_MS = 1000L
    private const val WAIT_TIME_BEFORE_SHUTDOWN_AFTER_SUCCESS_SECONDS = 30
    private const val OS_LINUX = "Linux"
    private const val ABOUT_1_MB_IN_BYTES = 1 * 1000 * 1000 // 1 byte * 1000 kb/b * 1000 mb/kb

    private val RASPBERRY_MOUNT_ROOT = File("/media/pi")
    private val IGNORE_FOLDERS = listOf(
      "PRIVATE/PANA_GRP",
      "MISC/GIS",
      "MISC/IDX",
      "MISC/LOG",
      "MISC/THM",
      "MISC/XCODE",
      "WPSettings.dat",
      "IndexerVolumeGuid",
    )

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
  }

  private var state: SDDumperState = SDDumperState.DRIVE_CHECK
  private var driveChecks: Int = 0

  private var availableDrives: List<File>? = null

  private var selectedDestinationDrive: File? = null
  private var implicitSourceDrives: List<File>? = null
  private var fileTransferList: List<File>? = null

  private var didConfirmTransfer = false
  private var successfulTransferFileList: List<File>? = null
  private var transferStartTime: Long? = null

  fun run() {
    // Hi. Welcome to my state machine.
    while (true) {
      when (state) {
        SDDumperState.DRIVE_CHECK -> {
          checkForDrives()
        }
        SDDumperState.DRIVE_SELECT -> {
          selectDestinationDrive()
        }
        SDDumperState.TRANSFER_PREPARE -> {
          prepareTransfer()
        }
        SDDumperState.TRANSFER_CONFIRM -> {
          confirmTransfer()
        }
        SDDumperState.TRANSFER -> {
          transfer()
        }
        SDDumperState.SUCCESS -> {
          success()
        }
        SDDumperState.SHUTDOWN -> {
          val os = System.getProperty("os.name")
          if (os == OS_LINUX) {
            println("Shutting down in Linux")
            Runtime.getRuntime().exec("sudo shutdown now")
          }
          exitProcess(0)
        }
      }
    }
  }

  private fun checkForDrives() {
    println()

    val paths = RASPBERRY_MOUNT_ROOT.listFiles() ?: emptyArray()
    for (path in paths) {
      val percentFree = round(10000.0 * path.freeSpace / path.totalSpace) / 100.0
      println(
        path.absolutePath
            + ": "
            + bytesToString(path.freeSpace)
            + " of "
            + bytesToString(path.totalSpace)
            + " ("
            + percentFree
            + "%) free"
      )
    }

    if (paths.size < 2) {
      driveChecks++
      println(
        "${paths.size} drives found. Waiting for at least 2. " +
            "$driveChecks of $MAX_DRIVE_CHECKS_BEFORE_SHUTDOWN checks done before shutting down..."
      )

      if (driveChecks >= MAX_DRIVE_CHECKS_BEFORE_SHUTDOWN) {
        println("Maximum drive checks done. Shutting down to save power.")
        state = SDDumperState.SHUTDOWN
        return
      }

      Thread.sleep(WAIT_TIME_BETWEEN_DRIVE_CHECKS_MS)
      return
    }

    println("Precondition met: Found ${paths.size} drives.")
    availableDrives = paths.toList()
    state = SDDumperState.DRIVE_SELECT
  }

  private fun selectDestinationDrive() {
    val availableDrives =
      this.availableDrives ?: throw IllegalStateException("Expected there to be drives available")
    assert(availableDrives.size > 1) { "We require more than 1 drive to transfer files to/from" }

    println()
    println("Select a drive ('z' to refresh, 'x' to shut down)")
    for (i in availableDrives.indices) {
      val path = availableDrives[i]
      val percentFree = round(10000.0 * path.freeSpace / path.totalSpace) / 100.0
      println(
        ('a' + i) + " - " + path.absolutePath
            + ": "
            + bytesToString(path.freeSpace)
            + " of "
            + bytesToString(path.totalSpace)
            + " ("
            + percentFree
            + "%) free"
      )
    }

    val selection = Scanner(System.`in`).next()
    if (selection.length != 1) {
      println("Invalid input, try again.")
      return
    }
    if (selection == "z") {
      state = SDDumperState.DRIVE_CHECK
      return
    }
    if (selection == "x") {
      state = SDDumperState.SHUTDOWN
      return
    }

    val index = selection[0].minus('a')
    if (index < 0 || index > availableDrives.size - 1) {
      println("Invalid input, try again.")
      return
    }

    selectedDestinationDrive = availableDrives[index]
    implicitSourceDrives = availableDrives.filterIndexed { i, _ -> i != index }

    println(
      "Selected ${selectedDestinationDrive!!.absolutePath} as destination, " +
          "and ${implicitSourceDrives.toString()} as sources"
    )
    state = SDDumperState.TRANSFER_PREPARE
  }

  private fun prepareTransfer() {
    val implicitSourceDrives =
      this.implicitSourceDrives
        ?: throw IllegalStateException("Expected source drives to be selected")
    val selectedDestinationDrive =
      this.selectedDestinationDrive
        ?: throw java.lang.IllegalStateException("Expected a selected destination")

    println()
    println("Finding files...")
    val fileList = implicitSourceDrives.flatMap { drive ->
      Files.find(
        drive.toPath(),
        99,
        { _: Path, attrs: BasicFileAttributes -> attrs.isRegularFile })
        .map { path -> path.toFile() }
        .filter { file -> !IGNORE_FOLDERS.any { ignorePath -> file.absolutePath.contains(ignorePath) } }
        .collect(Collectors.toList())
    }

    val spaceToBeUsedByAllFilesBytes = fileList.sumOf { file -> file.length() }

    // Leave ~1MB of buffer just in case
    val remainingSpaceOnDestinationBytes = selectedDestinationDrive.freeSpace - ABOUT_1_MB_IN_BYTES
    val remainingSpaceOnDestinationString = bytesToString(remainingSpaceOnDestinationBytes)
    if (remainingSpaceOnDestinationBytes > spaceToBeUsedByAllFilesBytes) {
      // All files will fit
      println(
        "It seems all ${fileList.size} file(s) will fit, taking ${
          bytesToString(
            spaceToBeUsedByAllFilesBytes
          )
        }"
      )
      this.fileTransferList = fileList
    } else {
      // Not all files will fit, calculate which files can
      println(
        "It seems like not all ${fileList.size} file(s) will fit (needs " +
            "${bytesToString(spaceToBeUsedByAllFilesBytes)}, has $remainingSpaceOnDestinationString)"
      )
      println("Manually calculating which files can fit using the super lazy FIFO method...")
      var runningTotalSpaceBytes = 0L
      val trimmedFileList = ArrayList<File>()
      for (file in fileList) {
        val fileSize = file.length()
        if (runningTotalSpaceBytes + fileSize < remainingSpaceOnDestinationBytes) {
          runningTotalSpaceBytes += fileSize
          trimmedFileList.add(file)
          println(
            "Adding $file (${bytesToString(fileSize)}); " +
                "running total: ${bytesToString(runningTotalSpaceBytes)} of $remainingSpaceOnDestinationString left"
          )
        } else {
          println(
            "Ignoring $file (${bytesToString(fileSize)}); " +
                "running total: ${bytesToString(runningTotalSpaceBytes)} of $remainingSpaceOnDestinationString left"
          )
        }
      }

      println(
        "Only ${trimmedFileList.size} file(s) will fit, taking ${
          bytesToString(
            runningTotalSpaceBytes
          )
        }"
      )
      this.fileTransferList = trimmedFileList
    }
    state = SDDumperState.TRANSFER_CONFIRM
  }

  private fun confirmTransfer() {
    val fileList = this.fileTransferList
      ?: throw IllegalStateException("Expected to have files queued up to transfer")
    val selectedDestinationDrive =
      this.selectedDestinationDrive
        ?: throw java.lang.IllegalStateException("Expected to have a selected destination drive")

    println()
    val totalFileSize = fileList.sumOf { file -> file.length() }
    print("Listing all found files: ")
    for (file in fileList) {
      print("${file.name} ")
    }
    println()
    println(
      "Moving ${fileList.size} file(s) into ${selectedDestinationDrive.absolutePath} " +
          "using ${bytesToString(totalFileSize)} of space"
    )
    println("This will leave ${bytesToString(selectedDestinationDrive.freeSpace - totalFileSize)} of space left")
    println("(A)ccept; (R)eset selections; (Q)uit and shutdown")
    when (Scanner(System.`in`).nextLine().toLowerCase()) {
      "a" -> {
        didConfirmTransfer = true
        state = SDDumperState.TRANSFER
      }
      "r" -> {
        state = SDDumperState.DRIVE_CHECK
      }
      "q" -> {
        state = SDDumperState.SHUTDOWN
      }
    }
  }

  private fun transfer() {
    assert(didConfirmTransfer) { "Expected the user to have confirmed the transfer." }
    val fileTransferList =
      this.fileTransferList
        ?: throw IllegalStateException("Expected to have files queued up to transfer")
    val selectedDestinationDrive =
      this.selectedDestinationDrive
        ?: throw java.lang.IllegalStateException("Expected to have a selected destination drive")

    transferStartTime = System.currentTimeMillis()
    val successfulTransferFileList = ArrayList<File>()
    for (i in fileTransferList.indices) {
      val sourceFile = fileTransferList[i]
      if (!sourceFile.isFile) {
        println("${sourceFile.absolutePath} is not a file, ignoring")
        continue
      }

      var destinationFile = File(selectedDestinationDrive, sourceFile.name)
      var tempName = 1
      while (destinationFile.exists()) {
        destinationFile =
          File(
            selectedDestinationDrive,
            sourceFile.nameWithoutExtension + "-" + tempName++ + "." + sourceFile.extension
          )
      }

      print("Writing ${sourceFile.absolutePath} to ${destinationFile.absolutePath} " +
          "(file ${i + 1} of ${fileTransferList.size})... ")

      val result = Files.copy(
        sourceFile.toPath(),
        destinationFile.toPath(),
        StandardCopyOption.COPY_ATTRIBUTES
      )
      val resultFile = result.toFile()
      if (!resultFile.exists() || resultFile.length() != sourceFile.length()) {
        if (resultFile.delete()) {
          print("Removed attempted file transfer. ")
        }
        print("Failed. Skipping.\r\n")
      } else {
        if (sourceFile.delete()) {
          print("Deleted original. ")
        }
        print("Move successful!\r\n")
        successfulTransferFileList.add(destinationFile)
      }
    }

    this.successfulTransferFileList = successfulTransferFileList
    state = SDDumperState.SUCCESS
  }

  private fun success() {
    val successfulTransfers =
      this.successfulTransferFileList
        ?: throw IllegalStateException("Expected to have successful transfers")
    val fileTransferList =
      this.fileTransferList ?: throw IllegalStateException("Expected to have queued transfers")
    val transferStartTime =
      this.transferStartTime ?: throw IllegalStateException("Expected to have a start time")

    val transferDuration = System.currentTimeMillis() - transferStartTime
    val minutes = transferDuration / 1000 / 60
    val seconds = transferDuration / 1000 % 60

    val transferBytes = successfulTransfers.sumOf { file -> file.length() }
    val bytesPerSecond = 1000 * transferBytes / transferDuration

    println(
      "Finished transferring ${successfulTransfers.size} of ${fileTransferList.size} " +
          "file(s) in $minutes:$seconds - ${bytesToString(bytesPerSecond)}/s"
    )

    println("Shutting down after $WAIT_TIME_BEFORE_SHUTDOWN_AFTER_SUCCESS_SECONDS second countdown...")
    for (i in WAIT_TIME_BEFORE_SHUTDOWN_AFTER_SUCCESS_SECONDS downTo 0) {
      print("$i ")
      Thread.sleep(1000)
    }
    state = SDDumperState.SHUTDOWN
  }
}
