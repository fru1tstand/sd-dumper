package me.fru1t.sddumper.components

import me.fru1t.sddumper.SDDumperApplication
import me.fru1t.sddumper.SDDumperState
import me.fru1t.sddumper.swing.SDDumperMainFrame
import me.fru1t.sddumper.Session
import me.fru1t.sddumper.StringUtils
import me.fru1t.sddumper.swing.SDDumperButton
import me.fru1t.sddumper.swing.SDDumperJPanel
import kotlin.math.round

/** Show connected drives and have the user select one. */
class DestinationDriveComponent(private val session: Session) : SDDumperJPanel() {
  private val refreshButton = SDDumperButton("Refresh Drives") {
    redrawPanel()
  }

  init {
    refreshButton.setLocation(0, SDDumperMainFrame.SCREEN_HEIGHT - SDDumperButton.HEIGHT)
  }

  override fun redrawPanel() {
    removeAll()
    repaint()

    session.println(null)
    session.println("Refreshing drives")

    val paths = SDDumperApplication.RASPBERRY_MOUNT_ROOT.listFiles() ?: emptyArray()
    session.println("Found ${paths.size} drive(s)")
    for (path in paths.withIndex()) {
      val percentFree = round(10000.0 * path.value.freeSpace / path.value.totalSpace) / 100.0
      val text =
        path.value.absolutePath + ": " + StringUtils.bytesToString(path.value.freeSpace) + " of " + StringUtils.bytesToString(
          path.value.totalSpace
        ) + " (" + percentFree + "%) free"
      session.println(text)

      val driveButton = SDDumperButton(text) {
        session.selectedDestinationDrive = path.value
        session.setState(SDDumperState.GUI_TRANSFER_CONFIRM)
      }

      driveButton.setSize(WIDTH, SDDumperButton.HEIGHT)
      driveButton.setLocation(0, SDDumperButton.HEIGHT * path.index)
      add(driveButton)
    }

    add(refreshButton)
  }
}