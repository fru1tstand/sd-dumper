package me.fru1t.sddumper.components

import me.fru1t.sddumper.SDDumperApplication
import me.fru1t.sddumper.SDDumperState
import me.fru1t.sddumper.Session
import me.fru1t.sddumper.StringUtils
import me.fru1t.sddumper.swing.SDDumperButton
import me.fru1t.sddumper.swing.SDDumperJPanel
import me.fru1t.sddumper.swing.SDDumperMainFrame
import java.awt.Color
import java.awt.Font
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.ArrayList
import java.util.stream.Collectors
import javax.swing.JLabel

/** Find and confirm files for transfer from other drives into destination drive. */
class TransferConfirmComponent(private val session: Session) : SDDumperJPanel() {
  companion object {
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

    private const val ABOUT_1_MB_IN_BYTES = 1 * 1000 * 1000 // 1 byte * 1000 kb/b * 1000 mb/kb
  }

  private val prompt = JLabel("")
  private val acceptButton = SDDumperButton("Accept") {
    session.setState(SDDumperState.GUI_TRANSFER)
  }
  private val declineButton = SDDumperButton("Decline") {
    session.setState(SDDumperState.GUI_DRIVE_SELECT)
  }

  init {
    prompt.horizontalAlignment = JLabel.CENTER
    prompt.setBounds(
      0, 80, WIDTH, SDDumperMainFrame.SCREEN_HEIGHT - SDDumperButton.HEIGHT)
    prompt.setLocation(0, 50)
    prompt.foreground = Color.WHITE
    prompt.font = Font("Serif", Font.PLAIN, 24)

    acceptButton.setLocation(0, SDDumperMainFrame.SCREEN_HEIGHT - SDDumperButton.HEIGHT)
    declineButton.setLocation(
      SDDumperButton.WIDTH, SDDumperMainFrame.SCREEN_HEIGHT - SDDumperButton.HEIGHT)

    add(acceptButton)
    add(declineButton)
    add(prompt)
  }

  override fun redrawPanel() {
    if (session.selectedDestinationDrive?.exists() != true) {
      session.println(
        "Selected destination (${session.selectedDestinationDrive?.toString()}) no longer exists")
      session.setState(SDDumperState.GUI_DRIVE_SELECT)
      return
    }
    val selectedDestinationDrive = session.selectedDestinationDrive!!

    session.println(null)
    session.println("Finding files...")

    val paths = SDDumperApplication.RASPBERRY_MOUNT_ROOT.listFiles() ?: emptyArray()
    val fileList = paths.filter { it != session.selectedDestinationDrive }.flatMap { drive ->
      Files.find(
        drive.toPath(),
        99,
        { _: Path, attrs: BasicFileAttributes -> attrs.isRegularFile })
        .map { path -> path.toFile() }
        .filter { file -> !IGNORE_FOLDERS.any { ignorePath -> file.absolutePath.contains(ignorePath) } }
        .collect(Collectors.toList())
    }
    session.println("Listing all found files: ")
    for (file in fileList) {
      session.println("${file.name} ")
    }

    val spaceToBeUsedByAllFilesBytes = fileList.sumOf { file -> file.length() }

    // Leave ~1MB of buffer just in case
    val remainingSpaceOnDestinationBytes = selectedDestinationDrive.freeSpace - ABOUT_1_MB_IN_BYTES
    val remainingSpaceOnDestinationString = StringUtils.bytesToString(remainingSpaceOnDestinationBytes)
    if (remainingSpaceOnDestinationBytes > spaceToBeUsedByAllFilesBytes) {
      // All files will fit
      session.println(
        "It seems all ${fileList.size} file(s) will fit, " +
            "taking ${StringUtils.bytesToString(spaceToBeUsedByAllFilesBytes)}"
      )
      session.fileTransferList = fileList
    } else {
      // Not all files will fit, calculate which files can
      session.println(
        "It seems like not all ${fileList.size} file(s) will fit (needs " +
            "${StringUtils.bytesToString(spaceToBeUsedByAllFilesBytes)}, has $remainingSpaceOnDestinationString)"
      )
      session.println("Manually calculating which files can fit using the super lazy FIFO method...")
      var runningTotalSpaceBytes = 0L
      val trimmedFileList = ArrayList<File>()
      for (file in fileList) {
        val fileSize = file.length()
        if (runningTotalSpaceBytes + fileSize < remainingSpaceOnDestinationBytes) {
          runningTotalSpaceBytes += fileSize
          trimmedFileList.add(file)
          session.println(
            "Adding $file (${StringUtils.bytesToString(fileSize)}); " +
                "running total: ${StringUtils.bytesToString(runningTotalSpaceBytes)} of $remainingSpaceOnDestinationString left"
          )
        } else {
          session.println(
            "Ignoring $file (${StringUtils.bytesToString(fileSize)}); " +
                "running total: ${StringUtils.bytesToString(runningTotalSpaceBytes)} of $remainingSpaceOnDestinationString left"
          )
        }
      }

      session.println(
        "Only ${trimmedFileList.size} file(s) will fit, taking ${
          StringUtils.bytesToString(
            runningTotalSpaceBytes
          )
        }"
      )
      session.fileTransferList = trimmedFileList
    }

    if (session.fileTransferList?.isEmpty() != false) {
      session.println("No files found. Back to drive select.")
      session.setState(SDDumperState.GUI_DRIVE_SELECT)
      return
    }

    val totalFileSize = session.fileTransferList!!.sumOf { file -> file.length() }

    prompt.text =
      "<html>Transfer ${session.fileTransferList?.size} file(s)?<br />" +
          "This leaves ${StringUtils.bytesToString(selectedDestinationDrive.freeSpace - totalFileSize)} " +
          "of space left in <br />$selectedDestinationDrive</html>"
    repaint()
  }
}