package aug.profile

import java.awt.Font
import java.io.{File, FileInputStream, FileOutputStream}
import javax.xml.bind.{JAXBContext, Marshaller}
import javax.xml.bind.annotation.{XmlAccessType, XmlAccessorType, XmlRootElement}

import aug.gui.MainWindow
import aug.util.{TryWith, Util}
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

import scala.collection.mutable

@XmlRootElement(name = "ColorSchemeConfig")
@XmlAccessorType(XmlAccessType.FIELD)
case class ColorSchemeConfig(
                              name: String = "",
                              defaultFg: String = "#AAAAAA",
                              defaultBg: String = "#000000",
                              black: String = "#000000",
                              red: String = "#FF0000",
                              green: String = "#00AA00",
                              yellow: String = "#AA5500",
                              blue: String = "#0000AA",
                              magenta: String = "#AA00AA",
                              cyan: String = "#00AAAA",
                              white: String = "#AAAAAA",
                              boldBlack: String = "#555555",
                              boldRed: String = "#FF5555",
                              boldGreen: String = "#55FF55",
                              boldYellow: String = "#FFFF55",
                              boldBlue: String = "#5555FF",
                              boldMagenta: String = "#FF55FF",
                              boldCyan: String = "#55FFFF",
                              boldWhite: String = "#FFFFFF"
                            ) {
  private def this() = this("")
}

@XmlRootElement(name = "TelnetConfig")
@XmlAccessorType(XmlAccessType.FIELD)
case class TelnetConfig(
                         host: String = "",
                         port: Int = 23,
                         fragmentTimeout: Long = 250,
                         echo: Boolean = true,
                         mccpEnabled: Boolean = true,
                         gmcpEnabled: Boolean = true,
                         gmcpSupports: String = ""
                       ) {
  private def this() = this("")
}

@XmlRootElement(name = "JavaConfig")
@XmlAccessorType(XmlAccessType.FIELD)
case class JavaConfig(
                       clientMode: String = "disabled",
                       mainClass: String = "",
                       classPath: Array[String] = Array.empty,
                       clientTimeout: Int = 500
                     ) {
  private def this() = this("disabled")
}

@XmlRootElement(name = "FontConfig")
@XmlAccessorType(XmlAccessType.FIELD)
case class FontConfig(
                       family: String = "default",
                       size: Int = 12
                     ) {
  private def this() = this("default")

  def toFont = {
    if (family == "default") {
      Util.defaultFont.deriveFont(size)
    } else new Font(family, 0, size)
  }
}

@XmlRootElement(name = "MainConfig")
@XmlAccessorType(XmlAccessType.FIELD)
case class MainConfig(
                       colorSchemes: Array[ColorSchemeConfig] = Array.empty
                     ) {
  private def this() = this(Array.empty)
}


@XmlRootElement(name = "ProfileConfig")
@XmlAccessorType(XmlAccessType.FIELD)
case class ProfileConfig(
                          name: String,
                          telnetConfig: TelnetConfig = TelnetConfig(),
                          javaConfig: JavaConfig = JavaConfig(),
                          commandLineFont: FontConfig = FontConfig()
                     ) {
  private def this() = this("")
}

object ConfigManager {
  val log = Logger(LoggerFactory.getLogger(ConfigManager.getClass))

  private val activeProfiles = mutable.Map[String, Profile]()
  private val profiles = mutable.Map[String, ProfileConfig]()
  private val profilesConfigContext = JAXBContext.newInstance(classOf[ProfileConfig])
  private val profilesConfigMarshaller = profilesConfigContext.createMarshaller()
  profilesConfigMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)

  private var mainConfig : MainConfig = MainConfig()
  private val mainConfigContext = JAXBContext.newInstance(classOf[MainConfig])
  private val mainConfigMarshaller = mainConfigContext.createMarshaller()
  mainConfigMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)

  val configDir = {
    val homeDir = System.getProperty("user.home")

    val configDirName = System.getProperty("aug.profile.ConfigManager.configDir", "augustmc")

    val configDir = new File(s"$homeDir/.config/$configDirName")

    if (!configDir.exists) {
      log.info("creating path {}", configDir.getAbsolutePath)
      configDir.mkdirs
    }

    configDir
  }

  val profilesDir = {
    val nd = new File(configDir, "profiles")

    if (!nd.exists) {
      log.info("creating path {}", nd.getAbsolutePath)
      nd.mkdirs
    }

    nd
  }

  val mainConfigPath = new File(configDir, "mainConfig.xml")

  def setMainConfig(mainConfig: MainConfig) = {
    synchronized {
      this.mainConfig = mainConfig

      TryWith(new FileOutputStream(mainConfigPath)) { fos =>
        mainConfigMarshaller.marshal(mainConfig, fos)
      }
    }
  }

  def activateProfile(name: String, mainWindow: MainWindow) = synchronized {
    activeProfiles(name) = new Profile(profiles(name), mainWindow)
  }

  def getUnactivedProfiles = synchronized {
    val keys = activeProfiles.keySet
    profiles.keys.filter(!keys.contains(_)).toList
  }

  def getActiveProfiles = synchronized(activeProfiles.keys)

  def getProfiles = synchronized(profiles.values.toList)

  def getProfile(name: String) : Option[ProfileConfig] = synchronized {
    profiles.get(name)
  }

  def setProfile(profileConfig: ProfileConfig) = synchronized {
    profiles(profileConfig.name) = profileConfig
    saveProfile(profileConfig.name)
    activeProfiles.get(profileConfig.name).foreach(_.setProfileConfig(profileConfig))
  }

  def deactivateProfile(name: String) = synchronized {
    activeProfiles.get(name).foreach { profile=>
      profile.close
    }

    activeProfiles.remove(name)
  }

  def closeAllProfiles = synchronized( {
    activeProfiles.keys.foreach(deactivateProfile)
    activeProfiles.clear()
  })

  private def saveProfile(name: String) = synchronized {
    profiles.get(name).foreach { pc =>
      val dir = new File(profilesDir, name)

      if (!dir.exists) {
        log.info("creating path {}", dir.getAbsolutePath)
        dir.mkdirs
      }

      val file = new File(dir, "profileConfig.xml")

      TryWith(new FileOutputStream(file)) { fos =>
        profilesConfigMarshaller.marshal(pc, fos)
      }
    }
  }

  def getMainConfig = synchronized(mainConfig)

  def load() : Unit = {
    if (mainConfigPath.exists) {
      TryWith(new FileInputStream(mainConfigPath)) { fis =>
        mainConfig = mainConfigContext.createUnmarshaller().unmarshal(fis).asInstanceOf[MainConfig]
      }
    }

    profilesDir.listFiles.filter(_.isDirectory).foreach { profileDir =>
      val profileFile = new File(profileDir, "profileConfig.xml")

      if (profileFile.exists()) {
        TryWith(new FileInputStream(profileFile)) { fis =>
          val profileConfig = profilesConfigContext.createUnmarshaller().unmarshal(fis).asInstanceOf[ProfileConfig]
          profiles(profileConfig.name) = profileConfig
        }

        log.info("loaded profile {}", profileFile)
      }
    }
  }
}