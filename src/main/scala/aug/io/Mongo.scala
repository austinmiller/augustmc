package aug.io

import java.util.concurrent.{Executors, TimeUnit}

import aug.profile.{MongoInit, Profile, ProfileConfig}
import aug.util.Util
import org.mongodb.scala.{Document, MongoClient, MongoCollection, MongoDatabase}

class Mongo (profile: Profile, profileConfig: ProfileConfig) extends AutoCloseable {

  private val executorService = Executors.newFixedThreadPool(1)

  private val slog = profile.slog
  private val dbname = profileConfig.mongoConfig.db

  private var mongoClient: MongoClient = _
  private var db: MongoDatabase = _
  private var rooms: MongoCollection[Document] = _
  private var metrics: MongoCollection[Document] = _

  executorService.submit(new Runnable {
    override def run(): Unit = mongoLoop()
  })

  private def mongoLoop(): Unit = {
    try {
      val ms = System.currentTimeMillis()
      init()
      slog.info(s"successfully initialized mongo after ${System.currentTimeMillis() - ms} ms")

      if (!executorService.isShutdown) {
        profile.offer(MongoInit(mongoClient, db))
      }
    } catch {
      case e: Throwable =>
        slog.error(s"could not init mongodb", e)
    }
  }

  def init(): Unit = {
    val user = profileConfig.mongoConfig.user
    val password = profileConfig.mongoConfig.password
    val host = profileConfig.mongoConfig.host

    mongoClient = Util.printTime(MongoClient(s"mongodb://$user:$password@$host/?authSource=$dbname"))
    db = mongoClient.getDatabase(dbname)
  }

  override def close(): Unit = {
    executorService.shutdownNow()
    if (!executorService.awaitTermination(500, TimeUnit.MILLISECONDS)) {
      slog.error("timed out trying to shutdown mongo client")
    }
    mongoClient.close()
  }

  override def toString: String = mongoClient.toString
}

