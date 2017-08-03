package aug.script.test

import aug.script.framework.{ClientInterface, ProfileInterface, ReloadData}

abstract class AbstractClient extends ClientInterface {
  protected var profile: ProfileInterface

  override def init(profileInterface: ProfileInterface, reloadData: ReloadData): Unit = {
    this.profile = profileInterface
  }

  override def shutdown(): ReloadData = { new ReloadData }

  override def handleLine(lineNum: Long, line: String): Boolean = { false }

  override def handleFragment(fragment: String): Unit = {}

  override def handleGmcp(gmcp: String): Unit = {}

  override def handleCommand(cmd: String): Boolean = { false }

  override def onConnect(id: Long, url: String, port: Int): Unit = {}

  override def onDisconnect(id: Long): Unit = {}
}
