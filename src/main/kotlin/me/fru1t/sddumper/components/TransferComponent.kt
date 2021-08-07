package me.fru1t.sddumper.components

import me.fru1t.sddumper.SDDumperState
import me.fru1t.sddumper.Session
import me.fru1t.sddumper.StringUtils
import me.fru1t.sddumper.swing.SDDumperJPanel
import me.fru1t.sddumper.swing.SDDumperMainFrame
import java.awt.Color
import java.awt.Font
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.ArrayList
import javax.swing.JLabel

/** Transfer files and show updates to GUI. */
class TransferComponent(private val session: Session) : SDDumperJPanel() {
  companion object {
    private const val WAIT_TIME_BEFORE_SHUTDOWN_AFTER_SUCCESS_SECONDS = 30
  }

  private data class TransferStatus(var isDone: Boolean = false)

  private val progressLabel = JLabel()
  private var totalTransferBytes: Long = 0L
  private var startingDestinationDriveSize: Long = 0L
  private var startTime: Long = 0L

  init {
    progressLabel.foreground = Color.WHITE
    progressLabel.font = Font("Serif", Font.PLAIN, 24)
    progressLabel.setBounds(0, 20, WIDTH, SDDumperMainFrame.SCREEN_HEIGHT)
    add(progressLabel)
  }

  private fun updateGuiText(text: String) {
    progressLabel.text = "<html>$text</html>"
  }

  override fun redrawPanel() {
    if (session.fileTransferList?.isEmpty() != false) {
      session.println("No files queued up for transfer... Back to drive select")
      session.setState(SDDumperState.GUI_DRIVE_SELECT)
      return
    }
    if (session.selectedDestinationDrive?.exists() != true) {
      session.println("Destination drive no longer connected... Back to drive select")
      session.setState(SDDumperState.GUI_DRIVE_SELECT)
      return
    }

    totalTransferBytes = session.fileTransferList!!.sumOf { file -> file.length() }
    startingDestinationDriveSize = session.selectedDestinationDrive!!.freeSpace
    startTime = System.currentTimeMillis()

    val transferStatus = TransferStatus()
    Thread {
      transfer(transferStatus)
    }.start()
    Thread {
      while (!transferStatus.isDone) {
        updateGui()
        Thread.sleep(500)
      }

      // One last time
      updateGui()
    }.start()
  }

  private fun updateGui() {
    val elapsedTransferTimeSeconds =
      ((System.currentTimeMillis() - startTime) / 1000.0).coerceAtLeast(1.0);
    val transferredBytes =
      startingDestinationDriveSize - session.selectedDestinationDrive!!.freeSpace
    val remainingBytes = totalTransferBytes - transferredBytes
    val bytesPerSecond = (transferredBytes / elapsedTransferTimeSeconds).toLong().coerceAtLeast(1)

    val elapsedTimeString = StringUtils.secondsToString(elapsedTransferTimeSeconds.toLong())
    val transferPercent = 100 * transferredBytes / totalTransferBytes;

    val timeRemainingString = StringUtils.secondsToString(remainingBytes / bytesPerSecond)

    updateGuiText(
      "Elapsed time: $elapsedTimeString<br />" +
          "Transferred ${StringUtils.bytesToString(transferredBytes)} of " +
          "${StringUtils.bytesToString(totalTransferBytes)} ($transferPercent%)<br />" +
          "<br /> <br />" +
          "Remaining ${StringUtils.bytesToString(remainingBytes)} at " +
          "${StringUtils.bytesToString(bytesPerSecond)}/s<br />" +
          "Complete in: $timeRemainingString"
    )
  }

  private fun transfer(transferStatus: TransferStatus) {
    val fileTransferList = session.fileTransferList!!
    val selectedDestinationDrive = session.selectedDestinationDrive!!

    val successfulTransferFileList = ArrayList<File>()
    for (i in fileTransferList.indices) {
      val sourceFile = fileTransferList[i]
      if (!sourceFile.isFile) {
        session.println("${sourceFile.absolutePath} is not a file, ignoring")
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

      session.println(
        "Writing ${sourceFile.absolutePath} to ${destinationFile.absolutePath} " +
            "(file ${i + 1} of ${fileTransferList.size})... "
      )

      try {
        val result = Files.copy(
          sourceFile.toPath(),
          destinationFile.toPath(),
          StandardCopyOption.COPY_ATTRIBUTES
        )
        val resultFile = result.toFile()
        if (!resultFile.exists() || resultFile.length() != sourceFile.length()) {
          session.println(
            "The resulting move either doesn't exist or doesn't match in file size, " +
                "attempting to revert by deleting the destination"
          )
          if (resultFile.delete()) {
            session.println("Successfully reverted. Skipping.")
          }
          session.println("Failed to revert. Skipping.")
        } else {
          if (sourceFile.delete()) {
            session.println("Deleted original.")
          }
          session.println("Move successful!")
          successfulTransferFileList.add(destinationFile)
        }
      } catch (e: IOException) {
        session.println("Failed to transfer <${sourceFile.absolutePath}> due to <$e>")
      }
    }

    transferStatus.isDone = true
    if (successfulTransferFileList.size == fileTransferList.size) {
      session.println("All transfers successful, running shutdown sequence in...")
      for (i in WAIT_TIME_BEFORE_SHUTDOWN_AFTER_SUCCESS_SECONDS downTo 0) {
        session.println(i.toString())
        Thread.sleep(1000)
      }
      session.setState(SDDumperState.SHUTDOWN)
    } else {
      session.println("I only managed to transfer ${successfulTransferFileList.size} file(s) of ${fileTransferList.size}")
      session.println("Moving back to drive select to debug.")
      session.setState(SDDumperState.GUI_DRIVE_SELECT)
    }

  }
}