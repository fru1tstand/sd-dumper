package me.fru1t.sddumper

import java.io.File

data class Session(
  var selectedDestinationDrive: File? = null,
  var fileTransferList: List<File>? = null,

  val println: (s: String?) -> Unit,
  val setState: (state: SDDumperState) -> Unit
)
