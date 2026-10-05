@file:Suppress("UnusedReceiverParameter")

package aktual.core.icons.material

import aktual.core.icons.material.internal.materialIcon
import aktual.core.icons.material.internal.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

val MaterialIcons.ExpandMore: ImageVector by lazy {
  materialIcon(name = "ExpandMore", viewportSize = 960f) {
    materialPath {
      moveTo(480f, 615f)
      lineTo(240f, 375f)
      lineToRelative(56f, -56f)
      lineToRelative(184f, 184f)
      lineToRelative(184f, -184f)
      lineToRelative(56f, 56f)
      lineToRelative(-240f, 240f)
      close()
    }
  }
}
