package aug.script.test

import aug.script.shared._

class WindowGraph extends ClientInterface {
  var profile : ProfileInterface = null
  var com : TextWindowInterface = null

  override def init(profileInterface: ProfileInterface): Unit = {
    profile = profileInterface
    com = profile.createTextWindow("com")
    val metric = profile.createTextWindow("metric")

    val graph = new SplitWindow(
      new WindowReference("console"),
      new SplitWindow(
        new WindowReference("com"),
        new WindowReference("metric"),
        false, 0.8f
      ),
      true)

    profile.setWindowGraph(graph)

    com.echo("hello")
    com.echo("hello2")
    metric.echo("100 xpm")
  }

  override def shutdown(): Unit = {}

  override def handleLine(lineNum: Long, line: String): Boolean = {
    if (line.contains("Exits")) {
      com.echo(line)
    }

    false
  }

  override def handleFragment(fragment: String): Unit = {}

  override def handleGmcp(gmcp: String): Unit = {}

  override def handleCommand(cmd: String): Boolean = { false }

  override def onConnect(): Unit = {}

  override def onDisconnect(): Unit = {}
}
