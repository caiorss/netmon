package netmon


import scala.concurrent.Future
import concurrent.ExecutionContext.Implicits.global
import scala.util.{Success, Failure}
import scala.concurrent.Await
import scala.concurrent.duration._

/** General utility functions */ 
object Utils{
  def runEvery(period: Int)(action: => Unit) =
    while(true){
      action
      java.lang.Thread.sleep(period)
    }

    def getResourceImage(file: String, cls: Class[_]): java.awt.Image = {
    val uri = cls.getResource(file)
    assert(uri != null, s"Error resource file $file not found")
    java.awt.Toolkit.getDefaultToolkit().getImage(uri)
  }

  /// Show computation execution time
  def executionTime[A](comp: => A){
    val t1 = new java.util.Date()
    comp
    val t2 = new java.util.Date()
    val dt = (t2.getTime - t1.getTime).toDouble / 1000.0 
    println(s"This computation took ${dt} seconds.")
  }
  
}

/** Network Information Module */
object NetInfo{

  /** Returns true if TCP port from a given address is open */
  def isPortOpen(address: String, port: Int, timeout: Int = 1000): Boolean = {
    import java.net.Socket
    import java.net.InetSocketAddress
    try {
      val sock = new Socket()
      sock.connect(new InetSocketAddress(address, port), timeout)
      sock.close()
      true
    } catch {
      // Connection refused exception
      case (ex: java.net.ConnectException)
          => false
      case (ex: java.net.SocketTimeoutException)
          => false
      case ex: Throwable => throw ex
    }
  }

  def checkHTTPSync(hostname: String) =  {
    var is: java.io.InputStream = null  
    val c = new java.net.URL("http://" + hostname).openConnection()
    try {
      is = c.getInputStream()
      val buffer = new Array[Char](50)
      val br = new java.io.BufferedReader(
        new java.io.InputStreamReader(is)
      )   
      br.read(buffer, 0, 50)
      val line = buffer.mkString
      //println("line = " + line)
      line 
    } finally{
      if(is != null) is.close()
    }
  }

  def checkHTTP(hostname: String, address: String)(handler: (Boolean, String) => Unit) = { 
    val fut = Future{checkHTTPSync(hostname)}
    try {
      Await.result(fut, 2.second)
      handler(true, "Connection OK")
    } catch {
      case ex: java.net.UnknownHostException
          => try {
            // address of www.google.com = 216.58.222.100
            if(isPortOpen(address, 80))
              handler(false, s"Error: DNS Failure\nbut connection works (can connect to $address)\ntry changing DSN")
            else 
              handler(false, "Error: DNS Failure")
          } catch {
            case ex: java.net.SocketException
                => handler(false, "Network is unreachable (connect failed) + DNS Failure")
          }
      case ex: java.net.SocketException
          => handler(false, "Network is unreachable (connect failed)")
      case ex: java.util.concurrent.TimeoutException
          => handler(false, "Error: Connection very slow, timeout exceeded")
    }
  }  


  def getInterfaces(): Map[String,java.net.InetAddress] = {
    import java.net.InetAddress
    import java.net.NetworkInterface
    import collection.JavaConverters._

    def getInterfaceAddress(net: NetworkInterface) = {
      net.getInterfaceAddresses()
        .asScala
        .toSeq
        .find(inf => inf.getBroadcast() != null)
        .map(_.getAddress())
    }

    val interfaces = NetworkInterface
      .getNetworkInterfaces()
      .asScala
      .toSeq
    interfaces.filter(ni =>
      !ni.isLoopback()          // Exclude loopback interfaces
        && ni.isUp()            // Select active interface
        && ni.getHardwareAddress() != null
        && getInterfaceAddress(ni).isDefined
    )
      .map(ni => (ni.getName(), getInterfaceAddress(ni).get))
      .toMap
  } // ----- EOF function getInterfaces() -------- //

  def checkDNS() =
    try {
      java.net.InetAddress.getByName("www.google.com")
      true
    } catch {
      case ex: java.net.UnknownHostException => false
    }

}

/** Main Graphical User Inteface */ 
class Display(ico: java.awt.Image) extends javax.swing.JFrame{
  private val out      = new javax.swing.JTextArea()
  private val tray     = java.awt.SystemTray.getSystemTray()
  private val toolkit  = java.awt.Toolkit.getDefaultToolkit()
  private val popuMenu = new java.awt.PopupMenu()
  private val icon     = new java.awt.TrayIcon(ico)

  init()
  private def init(){
    val frame = this

    out.setEditable(false)

    frame.setTitle("Internet Connection Status")
    frame.setSize(400, 120)
    frame.setIconImage(ico)
    // frame.setLayout(new java.awt.FlowLayout())
    frame.add(new javax.swing.JScrollPane(out))
    frame.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE)
    frame.setBackground(java.awt.Color.CYAN)
    frame.setResizable(false)

    icon.setToolTip("Network Status Monitoring")
    tray.add(icon)
    icon.setImageAutoSize(true)
    frame.add(popuMenu)

    var flag = false

    val listener = new java.awt.event.ActionListener(){
      def actionPerformed(event: java.awt.event.ActionEvent){       
        flag = !flag
        frame.setVisible(flag)
      }
    }
    icon.addActionListener(listener)

    //frame.setVisible(true)
  }

  def display(msg: String) =
    out.setText(msg)

  def showInfo(title: String, message: String){
    icon.displayMessage(title, message,  java.awt.TrayIcon.MessageType.INFO)
  }
  def showError(title: String, message: String){
    icon.displayMessage(title, message,  java.awt.TrayIcon.MessageType.ERROR)
  }
  def showWarning(title: String, message: String){
    icon.displayMessage(title, message,  java.awt.TrayIcon.MessageType.WARNING)
  }

  def setTrayToolTip(text: String) =
    icon.setToolTip(text)

  def setIcon(img: java.awt.Image) = {
    this.setIconImage(img)
    this.icon.setImage(img)
  }
}

/** Program Entry Point */
object Main{
  // Known hostname that provides HTTP service 
  val probeHOST  = "www.google.com"
  // Known hostname's IP address 
  val probeADDR  = "216.58.222.100"

  val iconOnline  =
    Utils.getResourceImage("/resources/network-online.jpg", getClass())

  val iconOffline =
    Utils.getResourceImage("/resources/network-offline.png", getClass())

  def main(args: Array[String]) = {
    val disp = new Display(iconOnline)
    // disp.setIconImage(iconOnline)

    var onlineStatus = true

    // Run action every 2000 milliseconds or 2 seconds 
    Utils.runEvery(2000){
      val time = new java.util.Date()
      NetInfo.checkHTTP(probeHOST, probeADDR){ case (status, msg) =>
        val statusMsg = msg + "\n Last Update : " + time.toString()
        disp.display (statusMsg)
        disp.setTrayToolTip(statusMsg)
        if(status)
          disp.setIcon(iconOnline)
        else
          disp.setIcon(iconOffline)

        // if(onlineStatus && status == true){
        //   disp.showInfo("Connection Status", "Online - Connection OK.")
        //   onlineStatus = true
        // } else{
        //   disp.showInfo("Connection Status", "Offile - Connection Failed")
        //   onlineStatus = false
        // }
      }
    }
  }
} // --- End of object Main() ------ // 
