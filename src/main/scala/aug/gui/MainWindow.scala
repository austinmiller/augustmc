package aug.gui

import java.awt.{Component, Frame}
import java.awt.event.{ComponentEvent, ComponentListener, WindowEvent, WindowStateListener}
import java.io.{File, FileInputStream, FileOutputStream, IOException}
import java.util.Properties
import javax.imageio.ImageIO
import javax.swing.JFrame

import aug.gui.property._
import aug.util.{TryWith, Util}
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success}


object MainWindow extends JFrame with ComponentListener with WindowStateListener {
  val log = Logger(LoggerFactory.getLogger(MainWindow.getClass))

  val (configDir, propFile) = {
    val homeDir = System.getProperty("use.home")
    val configDir = new File(s"$homeDir/config/aug")
    if (!configDir.exists) {
      log.info("creating path {}", configDir.getAbsolutePath)
      configDir.mkdirs
    }

    val propFile = new File(configDir, "aug.properties")

    Util.touch(propFile)

    (configDir, propFile)
  }

  val properties = new Properties()

  for (p <- MWProperties.properties) properties.setProperty(p.key, p.defaultValue)

  TryWith(new FileInputStream(propFile)){
    properties.load(_)
  } match {
    case Success(_) =>
    case Failure(e) => throw new IOException("failed to load properties file",e)
  }

  save

  setIconImage(ImageIO.read(MainWindow.getClass.getResourceAsStream("leaf.png")))

  addComponentListener(this)
  addWindowStateListener(this)


  setSize(getInt(MWWidth),getInt(MWHeight))
  setLocation(getInt(MWPosX),getInt(MWPosY))
  if(getBoolean(MWMaximized)) maximize

  adjustComponents

  def getInt(prop: MainWindowProperty): Int = properties.getProperty(prop.key).toInt
  def getString(prop: MainWindowProperty): String = properties.getProperty(prop.key)
  def getBoolean(prop: MainWindowProperty): Boolean = properties.getProperty(prop.key).toBoolean

  def save : Unit = synchronized {
    TryWith(new FileOutputStream(propFile)) {
      properties.store(_, "main window properties")
    } match {
      case Failure(e) => log.error("failed to save properties",e)
      case _ =>
    }
  }

  def adjustComponents : Unit = {
    // TODO
    //profiles.resize
  }

  def saveShape : Unit = {
    set(MWHeight,getHeight)
    set(MWWidth,getWidth)
    val loc = getLocation
    set(MWPosX,loc.x)
    set(MWPosY,loc.y)
    set(MWMaximized,isMaximized)
    save
    adjustComponents
  }

  def register(c: Component) {
    // TODO
//    c.addKeyListener(globalKeyListener);
//    c.addMouseWheelListener(new GlobalMouseWheelListener());
  }

  def set(prop: MainWindowProperty, v: Boolean) : Unit = set(prop,Option(v))
  def set(prop: MainWindowProperty, v: String) : Unit = set(prop,Option(v))
  def set(prop: MainWindowProperty, v: Int) : Unit = set(prop,Option(v))
  def set(prop: MainWindowProperty, v: Option[Any]): Unit = {
    properties.setProperty(prop.key, v map { _.toString } getOrElse prop.defaultValue)
  }

  def maximize(): Unit = setExtendedState(getExtendedState() | Frame.MAXIMIZED_BOTH);
  def isMaximized(): Boolean = (getExtendedState & Frame.MAXIMIZED_BOTH) > 0

  override def componentShown(e: ComponentEvent): Unit = {}

  override def componentHidden(e: ComponentEvent): Unit = {}

  override def componentMoved(e: ComponentEvent): Unit = saveShape

  override def componentResized(e: ComponentEvent): Unit = saveShape

  override def windowStateChanged(e: WindowEvent): Unit = saveShape
}
