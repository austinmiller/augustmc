package aug.script.framework.reload

import java.lang.reflect.{Field, Modifier}
import java.lang.{Byte, Long}
import java.util

import aug.script.framework.ReloadData
import aug.script.framework.tools.ScalaUtils

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.collection.{TraversableLike, mutable}
import scala.reflect.ClassTag
import scala.util.{Failure, Try}

class ReloadException(message: String, cause: Throwable = null) extends Exception(message, cause)

abstract class Converter[T](implicit ct: ClassTag[T]) {
  final def typeToConvert: Class[_] = ct.runtimeClass
  final def serialize(t: AnyRef): String = convertToString(t.asInstanceOf[T])

  final def canConvert(cl: Class[_]): Boolean = typeToConvert.isAssignableFrom(cl)

  def convertToString(t: T): String = t.toString
  def convertToValue(string: String): T

}

object ByteConverter extends Converter[java.lang.Byte] {
  override def convertToValue(string: String): Byte = string.toByte
}

object ShortConverter extends Converter[java.lang.Short] {
  override def convertToValue(string: String): java.lang.Short = string.toShort
}

object FloatConverter extends Converter[java.lang.Float] {
  override def convertToValue(string: String): java.lang.Float = string.toFloat
}

object DoubleConverter extends Converter[java.lang.Double] {
  override def convertToValue(string: String): java.lang.Double = string.toDouble
}

object BooleanConverter extends Converter[java.lang.Boolean] {
  override def convertToValue(string: String): java.lang.Boolean = string.toBoolean
}

object IntegerConverter extends Converter[java.lang.Integer] {
  override def convertToValue(string: String): Integer = string.toInt
}

object LongConverter extends Converter[java.lang.Long] {
  override def convertToValue(string: String): Long = string.toLong
}

object StringConverter extends Converter[String] {
  override def convertToValue(string: String): String = string
}

class CollectionConverter[T <: TraversableLike[String, _]](implicit tt: reflect.runtime.universe.TypeTag[T], ct: ClassTag[T]) extends Converter[T] {
  import scala.reflect.runtime.universe._

  private val tpe = typeOf[T]
  private val classSymbol: ClassSymbol = tpe.typeSymbol.asClass
  private val ru = runtimeMirror(getClass.getClassLoader)
  private val mm: ModuleMirror = ru.reflectModule(classSymbol.companion.asModule)
  private val apply = mm.symbol.info.members.find{ member =>
    member.isMethod && member.name.toString == "apply"
  }.map(_.asMethod).getOrElse(throw new ReloadException("no apply method found"))

  override def convertToString(t: T): String = ScalaUtils.encodeList(t.toList)

  override def convertToValue(string: String): T = {
    ru.reflect(mm.instance).reflectMethod(apply)(ScalaUtils.decodeList(string)).asInstanceOf[T]
  }
}

class JavaCollectionConverter[T <: util.Collection[String]](implicit ct: ClassTag[T]) extends Converter[T] {
  private val cl = ct.runtimeClass

  override def convertToString(t: T): String = ScalaUtils.encodeList(t.asScala.toList)

  override def convertToValue(string: String): T = {
    val in = cl.newInstance().asInstanceOf[T]
    ScalaUtils.decodeList(string).foreach(in.add)
    in
  }
}

object Reloader {
  import scala.reflect.runtime.universe
  private val runtimeMirror = universe.runtimeMirror(getClass.getClassLoader)

  // order matters here, more commonly used types first
  private val converters: List[Converter[_]] = List(
    IntegerConverter,
    DoubleConverter,
    FloatConverter,
    BooleanConverter,
    LongConverter,
    StringConverter,
    new CollectionConverter[ListBuffer[String]](),
    new CollectionConverter[mutable.Queue[String]](),
    new CollectionConverter[mutable.HashSet[String]](),
    new JavaCollectionConverter[java.util.ArrayList[String]](),
    new JavaCollectionConverter[java.util.HashSet[String]](),
    ShortConverter,
    ByteConverter
  )

  def load(reloadData: ReloadData, objs: List[AnyRef], userConverters: List[Converter[_]] = List.empty): List[Exception] = {
    val errors = ListBuffer[Exception]()
    val converters = userConverters ++ this.converters

    withFields(objs, errors, (rl: AnyRef, f: Field) => {
      val cl: Class[_] = rl.getClass
      val tcl: Class[_] = f.get(rl).getClass
      converters.find(_.canConvert(tcl)) match {
        case Some(converter) =>
          get(reloadData, cl, f).foreach(s => f.set(rl, converter.convertToValue(s)))

        case None =>
          errors += unsupported(f, cl)
      }
    })

    errors.toList
  }

  def save(reloadData: ReloadData, objs: List[AnyRef], userConverters: List[Converter[_]] = List.empty): List[Exception] = {
    val errors = ListBuffer[Exception]()
    val converters = userConverters ++ this.converters

    withFields(objs, errors, (rl: AnyRef, f: Field) => {
      val cl: Class[_] = rl.getClass
      val t: AnyRef = f.get(rl)
      converters.find(_.canConvert(t.getClass)) match {
        case Some(converter) =>
          put(reloadData, cl, f, converter.serialize(t))

        case None =>
          errors += unsupported(f, cl)
      }
    })

    errors.toList
  }

  def loadInstances[T <: Reloadable](
                                      reloadData: ReloadData,
                                      cl: java.lang.Class[T],
                                      objs: java.util.List[T],
                                      userConverters: java.util.List[Converter[_]]): java.util.List[Exception] = {

    val (instances, errors) = loadInstances(reloadData, cl, userConverters.asScala.toList)
    instances.foreach(objs.add)
    errors.asJava
  }


  def loadInstances[T <: Reloadable](
                                      reloadData: ReloadData,
                                      cl: java.lang.Class[T],
                                      objs: java.util.List[T]): java.util.List[Exception] = {

    val (instances, errors) = loadInstances(reloadData, cl, List.empty)
    instances.foreach(objs.add)
    errors.asJava
  }

  def loadInstances[T <: Reloadable](
                                      reloadData: ReloadData,
                                      cl: Class[T]): (List[T], List[Exception]) = {
    loadInstances(reloadData, cl, List.empty)
  }

  def loadInstances[T <: Reloadable](
                                      reloadData: ReloadData,
                                      cl: Class[T],
                                      userConverters: List[Converter[_]]): (List[T], List[Exception]) = {
    val errors = ListBuffer[Exception]()
    val objs = ListBuffer[T]()
    val converters = userConverters ++ this.converters

    val prefix = cl.getName + "|"

    val keys: mutable.Set[Long] = reloadData.data.keySet().asScala.filter(_.startsWith(prefix)).map(extractInstanceKey)

    val fields = cl.getDeclaredFields.filter(isAnnotated)

    keys.foreach{ key =>
      val o = cl.newInstance()
      o.setKey(key)

      fields.foreach {f=>
        Try {
          val access = f.isAccessible
          if (!access) f.setAccessible(true)

          val t = f.get(o)

          converters.find(_.canConvert(t.getClass)) match {
            case Some(converter) =>
              get(reloadData, s"$prefix#${f.getName}").foreach(s => f.set(o, converter.convertToValue(s)))

            case None =>
              errors += unsupported(f, cl)
          }

          if (!access) f.setAccessible(false)
        } match {
          case Failure(e) => errors += new ReloadException("failed manipulating type", e)
          case _ =>
        }
      }

      objs += o
    }

    (objs.toList, errors.toList)
  }

  def saveInstances(reloadData: ReloadData, objs: java.util.List[Reloadable], userConverters: java.util.List[Converter[_]]): List[Exception] = {
    saveInstances(reloadData, objs.asScala.toList, userConverters.asScala.toList)
  }

  def saveInstances(reloadData: ReloadData, objs: java.util.List[Reloadable]): List[Exception] = {
    saveInstances(reloadData, objs.asScala.toList, List.empty)
  }

  def saveInstances(reloadData: ReloadData, objs: List[Reloadable]): List[Exception] = {
    saveInstances(reloadData, objs, List.empty)
  }

  def saveInstances(reloadData: ReloadData, objs: List[Reloadable], userConverters: List[Converter[_]]): List[Exception] = {
    val errors = ListBuffer[Exception]()
    val converters = userConverters ++ this.converters

    withFields(objs, errors, (o: AnyRef, f: Field) => {
      val cl: Class[_] = o.getClass
      val rl = o.asInstanceOf[Reloadable]
      val t: AnyRef = f.get(o)
      converters.find(_.canConvert(t.getClass)) match {
        case Some(converter) =>
          put(reloadData, s"${cl.getName}|${rl.getKey}", f, converter.serialize(t))

        case None =>
          errors += unsupported(f, cl)
      }
    })

    errors.toList
  }


    // have to be Java friendly, I guess
  def loadStaticFields(reloadData: ReloadData, classes: java.util.List[java.lang.Class[_]],
                       userConverters: java.util.List[Converter[_]]): java.util.List[Exception] = {
    loadStaticFields(reloadData, classes, userConverters.asScala.toList)
  }

  def loadStaticFields(reloadData: ReloadData, classes: java.util.List[java.lang.Class[_]]): java.util.List[Exception] = {
    loadStaticFields(reloadData, classes, List.empty)
  }

  def loadStaticFields(reloadData: ReloadData, classes: java.util.List[java.lang.Class[_]],
                       userConverters: List[Converter[_]]): java.util.List[Exception] = {
    val errors = new util.ArrayList[Exception]()
    val converters = userConverters ++ this.converters

    withJavaFields(classes, errors, (cl: Class[_], f: Field) => {
      val tcl = f.get(null).getClass
      converters.find(_.canConvert(tcl)) match {
        case Some(converter) =>
          get(reloadData, cl, f).foreach(s => f.set(null, converter.convertToValue(s)))

        case None =>
          errors.add(unsupported(f, cl))
      }
    })

    errors
  }

  def saveStaticFields(reloadData: ReloadData, classes: java.util.List[java.lang.Class[_]],
                       userConverters: java.util.List[Converter[_]]): java.util.List[Exception] = {
    saveStaticFields(reloadData, classes, userConverters.asScala.toList)
  }

  def saveStaticFields(reloadData: ReloadData, classes: java.util.List[java.lang.Class[_]]): java.util.List[Exception] = {
    saveStaticFields(reloadData, classes, List.empty)
  }

  def saveStaticFields(reloadData: ReloadData, classes: java.util.List[java.lang.Class[_]],
                       userConverters: List[Converter[_]]): java.util.List[Exception] = {
    val errors = new util.ArrayList[Exception]()
    val converters = userConverters ++ this.converters

    withJavaFields(classes, errors, (cl: Class[_], f: Field) => {
      val t: AnyRef = f.get(null)
      converters.find(_.canConvert(t.getClass)) match {
        case Some(converter) =>
          put(reloadData, cl, f, converter.serialize(t))

        case None =>
          errors.add(unsupported(f, cl))
      }
    })

    errors
  }

  private def extractInstanceKey(string: String): Long = {
    string.split("\\|")(1).split("#")(0).toLong
  }

  private def getStaticObj(s: String): Any = {
    val module = runtimeMirror.staticModule(s)
    runtimeMirror.reflectModule(module).instance
  }

  private def put(reloadData: ReloadData, cl: Class[_], f: Field, s: String): Unit = {
    put(reloadData, cl.getName, f, s)
  }

  private def put(reloadData: ReloadData, prefix: String, f: Field, s: String): Unit = {
    reloadData.data.put(s"$prefix#${f.getName}", s)
  }

  private def get(reloadData: ReloadData, cl: Class[_], f: Field): Option[String] = {
    get(reloadData, s"${cl.getName}#${f.getName}")
  }

  private def get(reloadData: ReloadData, key: String): Option[String] = {
    if(!reloadData.data.containsKey(key)) {
      None
    } else if (reloadData.data.get(key) == "") {
      None
    } else Some(reloadData.data.get(key))
  }

  private def unsupported(field: Field, cl: Class[_]) = new ReloadException(s"$field is unsupported @Reload class: $cl!")

  private def isStatic(field: Field): Boolean = Modifier.isStatic(field.getModifiers)
  private def isAnnotated(field: Field): Boolean = field.getAnnotation(classOf[Reload]) != null

  private def withJavaFields(classes: java.util.List[Class[_]],
                             errors: java.util.List[Exception],
                             funk: (Class[_], Field) => Unit): Unit = {

    classes.asScala.toList.foreach { cl =>
      cl.getDeclaredFields.filter(isStatic).filter(isAnnotated).foreach{ f=>
        Try {
          val access = f.isAccessible
          if (!access) f.setAccessible(true)
          funk(cl, f)
          if (!access) f.setAccessible(false)
        } match {
          case Failure(e) => errors.add(new ReloadException("failed manipulating type", e))
          case _ =>
        }
      }
    }
  }

  private def loadObject[T](o: T, fields: List[Field], errors: ListBuffer[Exception], funk: (T, Field) => Unit): Unit = {

  }

  private def withFields(objs: List[AnyRef],
                         errors: ListBuffer[Exception],
                         funk: (AnyRef, Field) => Unit): Unit = {
    objs.foreach(rl => {
      rl.getClass.getDeclaredFields.filter(isAnnotated).foreach(f => {
        Try {
          val access = f.isAccessible
          if (!access) f.setAccessible(true)
          funk(rl, f)
          if (!access) f.setAccessible(false)
        } match {
          case Failure(e) => errors += new ReloadException("failed manipulating type", e)
          case _ =>
        }
      })
    })
  }
}