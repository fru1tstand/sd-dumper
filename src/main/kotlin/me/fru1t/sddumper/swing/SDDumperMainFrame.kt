package me.fru1t.sddumper.swing

import com.google.common.collect.EvictingQueue
import java.awt.Color
import java.awt.event.WindowEvent
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.JTextArea

class SDDumperMainFrame : JFrame() {
  companion object {
    const val SCREEN_WIDTH = 800
    const val SCREEN_HEIGHT = 440

    private const val NULL_CONSOLE_ENTRY = ""

    const val RIGHT_PANEL_WIDTH = 300
    private const val BUTTON_BAR_HEIGHT = 60
  }

  private val rightPanel = JPanel()
  private val console = JTextArea()
  private val consoleContents = EvictingQueue.create<String>(31)

  init {
    setSize(SCREEN_WIDTH, SCREEN_HEIGHT)
    isUndecorated = true
    defaultCloseOperation = EXIT_ON_CLOSE
    layout = null
    contentPane.background = Color.BLACK


    setupRightPanel()
    add(rightPanel)
    println("Welcome to the SDDumper.")
  }

  private fun setupRightPanel() {
    rightPanel.setBounds(SCREEN_WIDTH - RIGHT_PANEL_WIDTH, 0, RIGHT_PANEL_WIDTH, SCREEN_HEIGHT)
    rightPanel.layout = null
    rightPanel.background = Color.BLACK

    // Console
    console.setBounds(0, 0, RIGHT_PANEL_WIDTH, SCREEN_HEIGHT - BUTTON_BAR_HEIGHT)
    console.isEditable = false
    console.background = Color.BLACK
    console.foreground = Color.WHITE
    console.lineWrap = true
    rightPanel.add(console)

    // Action buttons
    val exitButton = SDDumperButton("Exit") {
      dispatchEvent(WindowEvent(this, WindowEvent.WINDOW_CLOSING))
    }
    exitButton.setLocation(0, SCREEN_HEIGHT - BUTTON_BAR_HEIGHT)
    rightPanel.add(exitButton)
  }

  /** Clears the frame and inserts the given component to take up the full panel size. */
  fun setFullscreenComponent(component: SDDumperJPanel) {
    component.setBounds(0, 0, SCREEN_WIDTH - RIGHT_PANEL_WIDTH, SCREEN_HEIGHT)
    contentPane.removeAll()
    repaint()
    add(component)
    add(rightPanel)
    component.redrawPanel()
  }

  /** Prints a string to the application GUI console. */
  fun println(string: String?) {
    consoleContents.add(string ?: NULL_CONSOLE_ENTRY)

    val builder = StringBuilder()
    for (entry in consoleContents.reversed()) {
      if (entry == NULL_CONSOLE_ENTRY) {
        builder.append("\r\n")
      } else {
        builder.append(" > $entry\r\n")
      }
    }

    console.text = builder.toString()
  }
}