package me.fru1t.sddumper

import me.fru1t.sddumper.components.DestinationDriveComponent
import me.fru1t.sddumper.components.TransferComponent
import me.fru1t.sddumper.components.TransferConfirmComponent
import me.fru1t.sddumper.swing.SDDumperMainFrame
import java.io.File
import kotlin.system.exitProcess

class SDDumperApplication {
  companion object {
    private const val OS_LINUX = "Linux"

    val RASPBERRY_MOUNT_ROOT = File("/media/pi")
  }

  private val mainFrame = SDDumperMainFrame()
  private val session = Session(println = mainFrame::println, setState = this::setState)

  fun launchGui() {
    mainFrame.isVisible = true
    setState(SDDumperState.GUI_DRIVE_SELECT)
  }

  private fun setState(state: SDDumperState) {
    when(state) {
      SDDumperState.GUI_DRIVE_SELECT -> {
        mainFrame.setFullscreenComponent(DestinationDriveComponent(session))
      }
      SDDumperState.GUI_TRANSFER_CONFIRM -> {
        mainFrame.setFullscreenComponent(TransferConfirmComponent(session))
      }
      SDDumperState.GUI_TRANSFER -> {
        mainFrame.setFullscreenComponent(TransferComponent(session))
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
