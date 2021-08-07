package me.fru1t.sddumper.swing

import java.awt.Color
import javax.swing.JPanel

/** A stylized panel for the SDDumper application. */
abstract class SDDumperJPanel: JPanel() {
  companion object {
    val WIDTH = SDDumperMainFrame.SCREEN_WIDTH - SDDumperMainFrame.RIGHT_PANEL_WIDTH
  }

  init {
    layout = null
    background = Color.BLACK
  }

  abstract fun redrawPanel()
}