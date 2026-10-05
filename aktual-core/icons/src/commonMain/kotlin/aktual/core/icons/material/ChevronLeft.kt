@file:Suppress("UnusedReceiverParameter")

package aktual.core.icons.material

import aktual.core.icons.material.internal.materialIcon
import aktual.core.icons.material.internal.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

val MaterialIcons.ChevronLeft: ImageVector by lazy {
  materialIcon(name = "ChevronLeft", viewportSize = 960f, autoMirror = true) {
    materialPath {
      moveTo(560f, 720f)
      lineTo(320f, 480f)
      lineToRelative(240f, -240f)
      lineToRelative(56f, 56f)
      lineToRelative(-184f, 184f)
      lineToRelative(184f, 184f)
      lineToRelative(-56f, 56f)
      close()
    }
  }
}
