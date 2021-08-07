package me.fru1t.sddumper

enum class SDDumperState {
  /** Select a drive via the GUI. */
  GUI_DRIVE_SELECT,

  /** Check and confirm all files for transfer. */
  GUI_TRANSFER_CONFIRM,

  /** Transfer files and show in GUI. */
  GUI_TRANSFER,

  /** Turn off the board. */
  SHUTDOWN
}