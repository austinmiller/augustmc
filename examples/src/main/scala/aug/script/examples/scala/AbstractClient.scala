package aug.script.examples.scala

import aug.script.framework.{ClientInterface, LineEvent, ProfileInterface, ReloadData}
import org.mongodb.scala.{MongoClient, MongoDatabase}

abstract class AbstractClient extends ClientInterface {
  protected var profile: ProfileInterface

  override def init(profileInterface: ProfileInterface, reloadData: ReloadData): Unit = {
    this.profile = profileInterface
  }

  override def shutdown(): ReloadData = { new ReloadData }

  override def handleLine(lineEvent: LineEvent): Boolean = { false }

  override def handleFragment(lineEvent: LineEvent): Unit = {}

  override def handleGmcp(gmcp: String): Unit = {}

  override def handleCommand(cmd: String): Boolean = { false }

  override def onConnect(id: Long, url: String, port: Int): Unit = {}

  override def onDisconnect(id: Long): Unit = {}

  override def initDB(mongoClient: MongoClient, mongoDatabase: MongoDatabase): Unit = {}
}
