package aug.script.examples.scala

import aug.script.framework._

class WindowGraph extends AbstractClient {
  var profile : ProfileInterface = _
  var console : TextWindowInterface = _
  var com : TextWindowInterface = _
  var metric : TextWindowInterface = _

  override def init(profileInterface: ProfileInterface, reloadData: ReloadData): Unit = {
    profile = profileInterface
    console = profile.getTextWindow("console")
    com = profile.createTextWindow("com")
    metric = profile.createTextWindow("metric")

    val graph = new SplitWindow(
      new WindowReference("console"),
      new SplitWindow(
        new WindowReference("com"),
        new WindowReference("metric"),
        false, 0.8f
      ),
      true)

    metric.setSplittable(false)
    metric.setHighlightable(false)
    com.setTextFont("Courier", 36)
    com.setTopColorScheme("bright")

    profile.setWindowGraph(graph)

    com.echo("hello")
    com.echo("hello2")
    com.setLine(new LineWithNum(10, "line ten"))
    com.setLine(new LineWithNum(8, "line ten"))
    metric.echo("100 xpm")

    val le = com.getLine(10)
    if (le.isPresent) {
      metric.echo(s"com [10] == ${le.get}")
    }
  }

  override def handleCommand(cmd: String): Boolean = {
    cmd match {
      case "clear" =>
        metric.clear()
        true

      case "unsplit" =>
        console.unsplit()
        true

      case "split" =>
        console.split()
        true

      case "timeout" =>
        // intentionally force client thread timeout, for testing reasons
        Thread.sleep(2000)
        true

      case none => false
    }
  }

  override def shutdown(): ReloadData = {
    metric.echo("shutdown script") // to show that shutdown is called despite timeout, and in the right order.
    super.shutdown()
  }
}
