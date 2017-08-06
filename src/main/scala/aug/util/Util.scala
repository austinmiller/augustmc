package aug.util

import java.awt.event.{ActionEvent, ActionListener}
import java.awt.image.BufferedImage
import java.awt.{Color, Font, GraphicsEnvironment}
import java.io._
import java.nio.ByteBuffer
import java.util.concurrent.{Callable, Executors}
import java.util.jar.{Attributes, JarEntry, JarInputStream}
import java.util.regex.Pattern

import aug.profile.ConfigManager
import aug.script.ScriptLoader
import com.typesafe.scalalogging.Logger
import org.apache.commons.io.IOUtils
import org.reflections.Reflections
import org.reflections.scanners.{ResourcesScanner, SubTypesScanner}
import org.reflections.util.{ClasspathHelper, ConfigurationBuilder, FilterBuilder}
import org.slf4j.LoggerFactory

import scala.collection.immutable
import scala.reflect.ClassTag
import scala.util.control.NonFatal
import scala.util.{Failure, Try}

object TryWith {
  def apply[C <: Closeable, R](resource: => C)(f: C => R): Try[R] =
    Try(resource).flatMap(resourceInstance => {
      try {
        val returnValue = f(resourceInstance)
        Try(resourceInstance.close).map(_ => returnValue)
      }  catch {
        case NonFatal(exceptionInFunction) =>
          try {
            resourceInstance.close
            Failure(exceptionInFunction)
          } catch {
            case NonFatal(exceptionInClose) =>
              exceptionInFunction.addSuppressed(exceptionInClose)
              Failure(exceptionInFunction)
          }
      }
    })
}

class RingBuffer[A](val capacity: Int)(implicit m: ClassTag[A]) extends scala.collection.mutable.IndexedSeq[A] {
  private val data: Array[A] = new Array[A](capacity)
  private var index = 0
  var length = 0

  private def off(idx: Int) : Int = (index -idx+capacity) % capacity

  def push(elem: A) : Unit = {
    if(length < capacity) length += 1
    index = (1+index)%length
    data(index) = elem
  }

  def apply(idx: Int) : A = {
    if(idx < 0 || idx >= capacity) throw new IndexOutOfBoundsException
    data(off(idx))
  }

  override def update(idx: Int, elem: A): Unit = {
    if(idx < 0 || idx >= length) throw new IndexOutOfBoundsException
    data(off(idx)) = elem
  }
}

object Util {

  object Implicits {
    implicit def actionListener[T](f:  => T) : ActionListener = new ActionListener {
      override def actionPerformed(e: ActionEvent): Unit = f
    }

    implicit def actionListener(f: ActionEvent => Unit) : ActionListener = new ActionListener {
      override def actionPerformed(e: ActionEvent): Unit = f(e)
    }

    implicit def runnable(f: => Unit): Runnable = new Runnable() { def run() = f }
  }

  private val tp = Executors.newCachedThreadPool()

  def time[T](f: => T): (Long, T) = {
    val ms = System.currentTimeMillis()
    val rv = f
    (System.currentTimeMillis() - ms, rv)
  }

  def printTime[T](f: => T): T = {
    val (ms,t) = time(f)
    println(s"took $ms")
    t
  }

  def run[T](f: => T) = tp.submit(new Callable[T] {
    override def call(): T = f
  })

  def invokeLater(f: () => Unit) = {
    tp.submit(new Runnable { def run = f() })
  }

  def invokeLater(timeout: Long, f: () => Unit) = {
    tp.submit(new Runnable {
      override def run(): Unit = {
        Try {
          Thread.sleep(timeout)
        }

        f()
      }
    })
  }

  val log = Logger(LoggerFactory.getLogger(Util.getClass))

  val name = "August MC"
  val major = 2017
  val minor = 1

  val colorEscapeCode = "\u001B"
  val resetCode =s"${colorEscapeCode}[0m"

  def fullName : String = s"$name $version"
  def version : String = s"$major.$minor"

  def isWindows : Boolean = System.getProperty("os.name").toLowerCase.contains("windows")

  def concatenate(args: Array[Byte]*) : Array[Byte] = {
    val length = args map { _.length } reduce { _ + _ }
    val bb = ByteBuffer.allocate(length)

    for(b <- args) bb.put(b)
    bb.array
  }

  def right(bytes: Array[Byte], length: Int) = {
    val bb = ByteBuffer.allocate(length)
    bb.put(bytes,0,length)
    bb.array()
  }

  def touch(file: File) : Unit = {
    Try {
      if(!file.exists) new FileOutputStream(file).close
      file.setLastModified(System.currentTimeMillis)
    } match {
      case Failure(e) => log.error(s"failed to touch file ${file.getAbsolutePath}",e)
      case _ =>
    }
  }

  def removeColors(string: String): String = string.replaceAll("\u001B\\[.*?m", "")

  def toHex(color: Color) = f"#${color.getRed}%02x${color.getGreen}%02x${color.getBlue}%02x".toUpperCase
  def colorCode(code: String) = "" + 27.toByte.toChar + "[" + code + "m"

  val fontSizes = Array(8, 9, 10, 11, 12, 13, 14, 18, 24, 36, 48, 64)

  val monospaceFamilies: immutable.Seq[String] = {
    // create a graphics environment to measure fonts
    val bf = new BufferedImage(200, 80, BufferedImage.TYPE_INT_RGB)
    val bfg = bf.createGraphics
    val letters: IndexedSeq[Char] = (for (a <- 'a' to 'z') yield a) ++ (for (a <- 'A' to 'Z') yield a)

    GraphicsEnvironment.getLocalGraphicsEnvironment.getAllFonts.filter { font =>
      val fm = bfg.getFontMetrics(font)
      val widths = letters.map(ch => fm.stringWidth("" + ch)).toSet
      !letters.exists(!font.canDisplay(_)) && widths.size == 1
    }.map(_.getFamily()).toSet.toList.sorted
  }

  val defaultFont: Font = {
    val desirableFonts = List("Menlo", "Consolas")

    desirableFonts.find(monospaceFamilies.contains).map(new Font(_, 0, 12))
      .getOrElse(new Font(Font.MONOSPACED, 0, 12))
  }

  lazy val sharedClassesInPackage: Array[Class[_]] = {
    val reflections = new Reflections(new ConfigurationBuilder()
      .setScanners(new SubTypesScanner(false /* don't exclude Object.class */), new ResourcesScanner())
      .setUrls(ClasspathHelper.forClassLoader(ClasspathHelper.contextClassLoader(), ClasspathHelper.staticClassLoader()))
      .filterInputsBy(new FilterBuilder().include(FilterBuilder.prefix(ScriptLoader.FRAMEWORK_CLASSPATH))))
    reflections.getSubTypesOf(classOf[Object]).toArray.map(_.asInstanceOf[Class[_]])
  }

  /**
    * <p>Writed shared class files as jar to file, iff the files don't equal.  Return true
    * or false based on whether it was necessary to write the jar.</p>
    * @param file
    * @return
    */
  def writeSharedJar(file: File) : Boolean = {
    val classBytes: Map[String, Array[Byte]] = Util.sharedClassesInPackage.map{ cl =>
      val name = cl.getName.replace(".", "/") + ".class"
      val is = cl.getResourceAsStream(cl.getSimpleName + ".class")
      name -> IOUtils.toByteArray(is)
    }.toMap


    val existingClassBytes = getExistingClassBytes(file)

    val different = classBytes.keys != existingClassBytes.keys || classBytes.keys.exists { ck =>
      !existingClassBytes(ck).sameElements(classBytes(ck))
    }

    if (different) {
      TryWith(new FileOutputStream(file)) { fos =>
        val manifest = new java.util.jar.Manifest
        manifest.getMainAttributes.put(Attributes.Name.MANIFEST_VERSION, "1.0")
        TryWith(new java.util.jar.JarOutputStream(fos, manifest)) { jos =>
          classBytes.foreach { e =>
            val cl = e._1
            val bytes = e._2
            val entry = new JarEntry(cl)
            jos.putNextEntry(entry)
            entry.setTime(System.currentTimeMillis())
            jos.write(bytes)
            jos.closeEntry()
          }
        }
      }
      true
    } else false
  }

  def getExistingClassBytes(file: File): Map[String, Array[Byte]] = {
    val mapb = Map.newBuilder[String, Array[Byte]]

    if (file.exists()) {
      TryWith(new JarInputStream(new FileInputStream(file))) { jis =>
        var entry = jis.getNextJarEntry
        while (entry != null) {
          if (entry.getName.endsWith(".class")) {
            mapb += entry.getName -> IOUtils.toByteArray(jis)
          }
          entry = jis.getNextJarEntry
        }
      }
    }

    mapb.result()
  }

  val sharedJarFile = new File(ConfigManager.configDir, "framework.jar")
}

case class TelnetColor(color: Int, bright: Boolean) {
  def quote: String = Pattern.quote(toString)
  override def toString: String = {
    val bc = if(bright) 1 else 0
    s"${Util.colorEscapeCode}[$bc;${color}m"
  }
}

import scala.util.Random