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

    /** Get image or Icon from resource */
    def getResourceImage(file: String, cls: Class[_]): java.awt.Image = {
      val uri = cls.getResource(file)
      assert(uri != null, s"Error resource file $file not found")
      java.awt.Toolkit.getDefaultToolkit().getImage(uri)
    }

  /**  Show computation execution time */
  def executionTime[A](comp: => A){
    val t1 = new java.util.Date()
    comp
    val t2 = new java.util.Date()
    val dt = (t2.getTime - t1.getTime).toDouble / 1000.0 
    println(s"This computation took ${dt} seconds.")
  }

  private val seed  = new java.util.Random()

  /** Pick a random element of a list*/
  def pickRandom[A](xs: List[A]) =
    xs(seed.nextInt(xs.size))

  def joinLines(lines: String*) =
    lines.mkString("\n")
  
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
      Await.result(fut, 4.second)
      handler(true, "Connection OK.")
    } catch {
      case ex: java.net.UnknownHostException
          => try {
            // address of www.google.com = 216.58.222.100
            if(isPortOpen(address, 80))
              handler(false, "Error: DNS Failure, but can connect to probe address")
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

  /** Get IPv4 address of default gateway on Linux, MacOSX, BSD or Windows */
  def findDefaultGateway(): Option[String] = {
    import java.util.Scanner
    def findWhen(sc: Scanner)
                (next: => Scanner => (Boolean, String)) : Option[String] = {
      while(sc.hasNext()){
        val (flag, out) = next(sc)
        if(flag) return Some(out)
      }
      return None
    }
    // Read output of process $ netstat -rn
    //--------
    val pb = new java.lang.ProcessBuilder("netstat", "-rn")
    val inp = pb.start().getInputStream()
    val out = try scala.io.Source
      .fromInputStream(inp)
      .mkString
    finally {
      inp.close()
    }
    // Parse process output stored in out
    //-------
    val sc1 = new java.util.Scanner(out)
    val lineMatch = findWhen(sc1){ sc =>
      val line = sc.nextLine
      (line.contains("0.0.0.0"), line)
    }
    lineMatch flatMap { lin =>
      val s = new java.util.Scanner(lin)
      val os = System.getProperty("os.name").toLowerCase()
      if(os.contains("linux") || os.contains("osx") || os.contains("bsd")){
        s.next() // Ignore 1st column
        val gIP = s.next() // Gateway's IP
        s.next() ;
        val flag = s.next()
        s.next()  ; s.next() ; s.next()
        val iface = s.next() // Related Network interface
        if(flag == "UG") Some(gIP) else None
      } else {
        // Windows
        s.next() ; s.next()
        Some(s.next())
      }
    }
  }


} // ----- End of NetInfo moduel ---- //

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

  /**  List of pairs with hostnames and IP addresses
    *  [(Host of Google, IP address of google), (Host of yahoo, IP of yahoo), ...]
    *  The IP addresses can be obtained from the command $ ping www.bing.com 
    */
  val probeConfig = List(
    ("www.yahoo.com",     "98.138.252.38"),
    ("www.bing.com",      "204.79.197.200"),
    ("www.google.com",    "172.217.30.4"),
    ("www.amazon.com",    "54.192.56.234"),
    ("www.twitter.com",   "104.244.42.129"),
    ("www.microsoft.com", "23.76.243.153")    
  )

  val iconOnline  =
    Utils.getResourceImage("/resources/network-online.jpg", getClass())

  val iconOffline =
    Utils.getResourceImage("/resources/network-offline.png", getClass())

  def main(args: Array[String]) = {
    val disp = new Display(iconOnline)
    // disp.setIconImage(iconOnline)

    // State is true for Online and false for offline 
    var state = true 

    // Run action every 5000 milliseconds or 5 seconds 
    Utils.runEvery(5000){
      val time = new java.util.Date()

      val (host, addr) = Utils.pickRandom(probeConfig)

      NetInfo.checkHTTP(host, addr){ case (status, msg) =>
        val statusMsg = Utils.joinLines(
          msg,
          s"Probe host and address = $host, $addr",
          "Last Update : " + time.toString()
        )        
        disp.display (statusMsg)
        disp.setTrayToolTip(statusMsg)
          
        // println("state = " + state)

        // Mealy's State Machine
        if(status){
          if(!state){
            state = true
            disp.setIcon(iconOnline)
            disp.showInfo("Connection Status", "Online")
            //println("Connection Status")
          }
        } else if(state) {
          state = false
          disp.setIcon(iconOffline)
          disp.showError("Connection Status", "Offline")
          //println("Connection Status")
        }
      }
    }
  }
} // --- End of object Main() ------ // 

