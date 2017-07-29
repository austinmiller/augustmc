package aug.io

import java.util.concurrent.{Executors, TimeUnit}

import aug.profile.{Profile, ProfileConfig}
import aug.util.Util
import org.bson.BsonType
import org.mongodb.scala.bson.BsonValue
import org.mongodb.scala.model.IndexOptions
import org.mongodb.scala.model.Indexes._
import org.mongodb.scala.model.IndexOptions
import org.mongodb.scala.{Document, MongoClient, MongoCollection, MongoDatabase, Observable}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object MongoImplicits {
  implicit class DocumentObservable[C](val observable: Observable[Document]) extends ImplicitObservable[Document] {
    override val converter: (Document) => String = (doc) => doc.toJson
  }

  implicit class GenericObservable[C](val observable: Observable[C]) extends ImplicitObservable[C] {
    override val converter: (C) => String = (doc) => doc.toString
  }

  trait ImplicitObservable[C] {
    val observable: Observable[C]
    val converter: (C) => String

    def results(): Seq[C] = Await.result(observable.toFuture(), Duration(10, TimeUnit.SECONDS))
    def headResult() = Await.result(observable.head(), Duration(10, TimeUnit.SECONDS))
    def printResults(initial: String = ""): Unit = {
      if (initial.length > 0) print(initial)
      results().foreach(res => println(converter(res)))
    }
    def printHeadResult(initial: String = ""): Unit = println(s"$initial${converter(headResult())}")
  }
}

class Mongo (profile: Profile, profileConfig: ProfileConfig) extends AutoCloseable {
  import MongoImplicits._
  import org.mongodb.scala.model.Indexes._

  private val executorService = Executors.newFixedThreadPool(1)

  val slog = profile.slog
  val dbname = profileConfig.mongoConfig.db

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
      slog.info(s"profile ${profile.name}: successfully initialized mongo $this after ${System.currentTimeMillis() - ms} ms")
    } catch {
      case e: Throwable =>
        slog.error(s"profile ${profile.name}: could not init mongo db", e)
    }
  }

  def init() = {
    import org.mongodb.scala.model.Aggregates._
    import org.mongodb.scala.model.Accumulators._

    val user = profileConfig.mongoConfig.user
    val password = profileConfig.mongoConfig.password
    val host = profileConfig.mongoConfig.host

    mongoClient = Util.printTime(MongoClient(s"mongodb://$user:$password@$host/?authSource=$dbname"))
    db = mongoClient.getDatabase(dbname)

    rooms = db.getCollection("room")
    rooms.createIndex(ascending("num", "areaNum"), IndexOptions().unique(true).name("room_num_index")).results()

    metrics = db.getCollection("metric")
    metrics.createIndex(ascending("timestamp"), IndexOptions().name("metric_ts_index")).results()

    // first 1501278043304
    // last 1501278046268
    val (ms, _) = Util.time {
      metrics.aggregate(List(group(null, sum("sum", "$value")))).printHeadResult()
    }

    println(s"took $ms")
//
//    val rand = new Random()
//
//    val (time, rv) = Util.time {
//      val docs = for(i <- 0 to 1000000) yield Document("timestamp" -> System.currentTimeMillis(), "value" -> rand.nextInt(10))
//      metrics.insertMany(docs).results()
//    }
//
//    slog.info(s"took $time ms to insert docs")
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

