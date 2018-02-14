import scala.concurrent.Future
import concurrent.ExecutionContext.Implicits.global
import scala.util.{Success, Failure}
import scala.concurrent.Await
import scala.concurrent.duration._



/// Show computation execution time
def executionTime[A](comp: => A){
  val t1 = new java.util.Date()
  comp
  val t2 = new java.util.Date()
  val dt = (t2.getTime - t1.getTime).toDouble / 1000.0 
  println(s"This computation took ${dt} seconds.")
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

// def checkWAN() =
//   // Google's www.google.com 
//   isPortOpen("216.58.222.100", 80)

def isPortOpen(address: String, port: Int, timeout: Int = 1000) = {
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

// case class NetworkStatusHandler(
//   NetworkOK:           String => Unit,
//   NetwrokDNSFailLure:  String => Unit,
//   NetworkUnreachable : String => Unit,
// )


def checkHTTP(hostname: String)(handler: (Boolean, String) => Unit) = { 
  val fut = Future{checkHTTPSync(hostname)}
  try {
    Await.result(fut, 2.second)
    handler(true, "Connection OK")
  } catch {
    case ex: java.net.UnknownHostException
        => try {
          // address of www.google.com = 216.58.222.100
          if(isPortOpen("216.58.222.100", 80))
            handler(false, "Error: DNS Failure\nbut connection works (can connect to 216.58.222.100)\ntry changing DSN")
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


class Display extends javax.swing.JFrame{
  private val out = new javax.swing.JTextArea()
  init()
  private def init(){
    out.setEditable(false)

    val frame = this
    frame.setTitle("Internet Connection Status")
    frame.setSize(300, 100)
    // frame.setLayout(new java.awt.FlowLayout())
    frame.add(new javax.swing.JScrollPane(out))
    frame.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE)
    frame.setBackground(java.awt.Color.CYAN)
    frame.setResizable(false)
    frame.setVisible(true)
  }
  def display(msg: String) =
    out.setText(msg)
}


def runEvery(period: Int)(action: => Unit) =
  while(true){
    action
    java.lang.Thread.sleep(period)
  }


def getResourceImage(file: String, cls: Class[_]) = {  
  val uri = cls.getResource(file)
  assert(uri != null, s"Error resource file $file not found")
  java.awt.Toolkit.getDefaultToolkit().getImage(uri)
}

// val iconOnline  = getResourceImage("/resources/network-online.jpg", getClass())
// val iconOffline = getResourceImage("/resources/network-error.png", getClass())

val iconOnline  = getResourceImage("/resources/network-online.jpg", getClass())
val iconOffline = getResourceImage("/resources/network-offline.png", getClass())

println("Icon = " + iconOnline)

val disp = new Display()
disp.setIconImage(iconOnline)

// Run action every 2000 milliseconds or 
runEvery(2000){
  val time = new java.util.Date()
  checkHTTP("www.google.com"){ case (status, msg) =>
    disp.display (msg + "\n Last Update : " + time.toString())
    if(status)
      disp.setIconImage(iconOnline)
    else
      disp.setIconImage(iconOffline)
  }
}
