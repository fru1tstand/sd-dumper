package me.fru1t.sddumper

enum class SDDumperState {
  /** Check for available drives. */
  DRIVE_CHECK,

  /** Select a destination drive to write to. */
  DRIVE_SELECT,

  /** Prepare for the transfer by finding the files and verifying sizing fits. */
  TRANSFER_PREPARE,

  /** Ask the user if they're ok with the detected transfers. */
  TRANSFER_CONFIRM,

  /** Transfer files from all other drives into the destination drive. */
  TRANSFER,

  /** Electric feel. */
  SUCCESS,

  /** Turn off the board. */
  SHUTDOWN
}