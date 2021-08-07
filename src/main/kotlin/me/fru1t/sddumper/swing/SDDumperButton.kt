package me.fru1t.sddumper.swing

import java.awt.Color
import java.awt.event.ActionListener
import javax.swing.JButton

/** A SDDumper stylized [JButton]. */
class SDDumperButton(text: String, action: ActionListener) : JButton(text) {
  companion object {
    val HEIGHT = 60
    val WIDTH = 150
  }

  init {
    setSize(WIDTH, HEIGHT)
    addActionListener(action)
    background = Color.DARK_GRAY
    foreground = Color.WHITE
  }
}