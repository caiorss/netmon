package netmon.utils

import scala.concurrent.Future
import concurrent.ExecutionContext.Implicits.global
import scala.util.{Success, Failure}
import scala.concurrent.Await
import scala.concurrent.duration._


case class InterfaceData(
  name:              String,
  displayName:       String,
  isUp:              Boolean,
  isVirtual:         Boolean,
  multicast:         Boolean,
  hardwareAddress:   String,
  addresses:         List[String]
) {
  override def toString() = {
    val sw = new java.io.StringWriter()
    val pw = new java.io.PrintWriter(sw)
    val status = if (isUp) "up" else "down"
    pw.println( "Name                      = " + name)
    pw.println( "Display Name              = " + displayName)
    pw.println(s"Status                    = $status")
    pw.println( "Ethernet Address          = " + hardwareAddress)
    pw.println( "Addresses (IPv4)          = " + addresses.mkString(" "))
    sw.toString
  }
}

/** Network Information Module */
object NetInfo{

  import java.net.InetAddress
  import java.net.NetworkInterface
  import collection.JavaConverters._

  /** Returns true if TCP port from a given address is open */
  def isPortOpen(address: String, port: Int, timeout: Int = 1000): Boolean = {
    import java.net.Socket
    import java.net.InetSocketAddress
    try Utils.runWithTimeout(timeout){
      val sock = new Socket()
      sock.connect(new InetSocketAddress(address, port))
      sock.close()
      true
    } catch {
      // Connection refused exception
      case ex: java.net.ConnectException
          => false
      case ex: java.net.SocketTimeoutException
          => false
      case ex: java.net.NoRouteToHostException
          => false 
      case ex: java.util.concurrent.TimeoutException
          => false
    }
  }



  /** Check if port 80 or 443 of a given hostname or address is open by reading
      first 50 bytes and then closing the connection.
      Note: This function is synchronous and doesn't have a timeout. So it can block
       GUI threads.
    */
  def checkHTTPSync(hostname: String) =  {
    val bufferSize = 50 // number of bytes to read
    var is: java.io.InputStream = null  
    val c = new java.net.URL("http://" + hostname).openConnection()
    try {
      is = c.getInputStream()
      val buffer = new Array[Char](bufferSize)
      val br = new java.io.BufferedReader(
        new java.io.InputStreamReader(is)
      )   
      br.read(buffer, 0, bufferSize)
      val line = buffer.mkString
      //println("line = " + line)
      line 
    } finally{
      if(is != null) is.close()
    }
  }

  def checkHTTP(hostname: String, address: String, port: Int = 8080)(handler: (Boolean, String) => Unit) = {
    val fut = Future{checkHTTPSync(hostname)}
    try {
      Await.result(fut, 4.second)
      handler(true, "Connection OK.")
    } catch {
      case ex: java.net.UnknownHostException
          => try {
            // address of www.google.com = 216.58.222.100
            if(isPortOpen(address, port = port))
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

  def getInterfaceAddress(net: NetworkInterface) = {
    net.getInterfaceAddresses()
      .asScala
      .toSeq
      .find(inf => inf.getBroadcast() != null)
      .map(_.getAddress())
  }


  def getInterfaces(): Map[String,java.net.InetAddress] = {
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

  /** Get all addresses (IPs numbers) of a network interface */
  def getInterfaceAddresses(iface: NetworkInterface): List[String] =
    iface.getInetAddresses()
      .asScala.toSeq
      .map(_.getAddress())
      .filter(_.size == 4)  // Select IPv4 only - 4 bytes
      .map{addr => addr.map(b => b & 0xFF).mkString(".") }
      .toList

   /** Get a list with all network interfaces */
   def getIfacesData(): List[InterfaceData] = {
     def ethernetAddrToString(bytes: Array[Byte]) =
       bytes match {
         case null  => ""
         case _     => bytes.map(b => "%02X".format(b)).mkString(":")
       }
     val interfaces = NetworkInterface
       .getNetworkInterfaces()
       .asScala
       .toSeq
     interfaces.map{ iface =>
       InterfaceData(
         name            = iface.getName(),
         displayName     = iface.getDisplayName(),
         isUp            = iface.isUp,
         isVirtual       = iface.isVirtual, 
         multicast       = iface.supportsMulticast,
         hardwareAddress = ethernetAddrToString(iface.getHardwareAddress()),
         addresses       = getInterfaceAddresses(iface)
       )
     }.toList
   }

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


} // ----- End of NetInfo object ---- //


