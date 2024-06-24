import com.github.unthingable.jam.Jam
import com.github.unthingable.Util
import com.github.unthingable.framework.binding.Binding
import com.github.unthingable.framework.binding.EB
import com.github.unthingable.framework.mode.SimpleModeLayer

trait Panel:
  this: Jam =>
  lazy val panelLayer = new SimpleModeLayer("panel"):
    override val modeBindings: Seq[Binding[?, ?, ?]] = Vector(
      EB(
        j.browse.st.press,
        "switch panel",
        () => ext.application.nextPanelLayout()
      )
    )
end Panel
