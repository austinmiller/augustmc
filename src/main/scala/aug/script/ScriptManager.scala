package aug.script

import java.io.File
import java.net.{URL, URLClassLoader}

import aug.profile.{PPScriptJail, Profile}
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}

object JailClassLoader {
  val log = Logger(LoggerFactory.getLogger(JailClassLoader.getClass))
  val scriptPackage = "aug.script"
}

/**
  * <p>
  * This class loader jails some packages for scripts in order to prevent cross contamination between running profiles
  * and in order to allow for reloading scripts on the fly.
  * </p>
  *
  * <p>
  * jailPackages are base package names which will be loaded in the jail, while all other classes falling outside those
  * packages are pulled from the common class loader. "amc.script" by default is a jailed package. However, this class
  * loader class, despite being in this package, is loaded, of course, by the common class loader.
  * </p>
  *
  * @author austin
  *
  */
class JailClassLoader(val urls: Array[URL], jailPackages: Set[String]) extends
  ClassLoader(Thread.currentThread().getContextClassLoader) {

  import JailClassLoader._

  private class DetectClass(val parent: ClassLoader) extends ClassLoader(parent) {
    override def findClass(name: String) = super.findClass(name)
  }

  private class ChildClassLoader(val urls: Array[URL], realParent: DetectClass, jailPackages: Set[String])
    extends URLClassLoader(urls,null) {
    override def findClass(name: String): Class[_] = {

      if(!isJailed(name)) {
        log.trace("FREE: {}",name)
        realParent.loadClass(name)
      } else {

        Try {
          log.trace("JAILED {}", name)

          Option(super.findLoadedClass(name)) getOrElse {
            super.findClass(name)
          }
        } match {
          case Failure(e) =>
            log.error(s"failed to load in jail $name", e)
            realParent.loadClass(name)
          case Success(c) => c
        }
      }
    }
    def isJailed(name: String) = jailPackages.exists { p => p.length > 0 && name.startsWith(p) }
  }

  private val childClassLoader = new ChildClassLoader(urls,new DetectClass(getParent),jailPackages + scriptPackage)

  override protected def loadClass(name: String, resolve: Boolean) : Class[_] = {
    Try {
      childClassLoader.findClass(name)
    } match {
      case Failure(e) => super.loadClass(name,resolve)
      case Success(c) => c
    }
  }


}

sealed trait ScriptEvent

case object 

object ScriptManager {
  val log = Logger(LoggerFactory.getLogger(ScriptManager.getClass))
}

class ScriptManager(scriptDir: File, scriptClass: String, profile: Profile) {
  import ScriptManager._

  private val jailLoader = new JailClassLoader(classpath,jailed)

  private def jailed = profile.getString(PPScriptJail).split(",").toSet

  private def classpath: Array[URL] = {
    val classpath: String = System.getProperty("java.class.path")
    val urls = Seq.newBuilder[URL]
    for (dir <- classpath.split(":")) yield {
      log.trace("Adding classpath URL {}", dir)
      urls += new File(dir).toURI.toURL
    }

    if (scriptDir.exists && scriptDir.isDirectory) {
      urls += scriptDir.toURI.toURL
    }

    urls.result.toArray
  }
}
