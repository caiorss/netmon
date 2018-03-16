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

  def openUrl(uri: String){
    import java.awt.Desktop
    import java.io.IOException
    import java.net.URI
    import java.net.URISyntaxException
    val u = new URI(uri)
    val desktop = Desktop.getDesktop()
    desktop.browse(u)
  }

  /** Print to a string buffer and retrieve its content as string.
       Examples: 
       {{{
          scala> withString{ s => for(i <- 1 to 4) s.print("i * 3 = " + (i * 3) + " ; ") }
          res39: String = "i * 3 = 3 ; i * 3 = 6 ; i * 3 = 9 ; i * 3 = 12 ; "

          scala> withString{ s => for(i <- 1 to 3) s.println(i) }
          res41: String =
          "1
          2
          3
          "
       }}}
     */ 
  def withString(writer: java.io.PrintWriter => Unit): String = {
    val sw = new java.io.StringWriter()
    val pw = new java.io.PrintWriter(sw)
    writer(pw)
    sw.toString
  }

  /** Attempt to get a return value with timeout and throws TimeoutException
      if the timeout is violated.
      Throws: java.util.concurrent.TimeoutException
    */
  def runWithTimeout[A](timeoutMs: Int)(action: => A): A = {
    val fut = Future { action }
    Await.result(fut, timeoutMs.milliseconds)
  }

  /** Stream process or shell command to output in async mode (without
     block current thread.)
    */
  def streamProcessOutput(
    cmd:       String,
    args:      List[String] = List(),
    timeoutMs: Int = 5000
    )(out: java.io.PrintWriter) = Future {
    val pb = new java.lang.ProcessBuilder(cmd)
    args foreach pb.command.add
    val proc = pb.start()
    //process = proc
    val stdout = new java.util.Scanner(proc.getInputStream())
    val stderr = new java.util.Scanner(proc.getErrorStream())
    // Read process output in a new thread
    val fut = Future {
      while(stdout.hasNextLine())
        out.println(stdout.nextLine())
      while(stderr.hasNextLine())
        out.println(stderr.nextLine())
      out.println("Process ended with exit status = " + proc.exitValue())
    }
    // Monitor process thread waiting it finish until timeout is reached.
    // If timeout is reached, kill the process.
    try Await.result(fut, timeoutMs.milliseconds)
    catch {
      case ex: java.util.concurrent.TimeoutException
          => {
            proc.destroy()
            out.println("\nProcess killed after timeout exceeded")
          }
    } finally {
      stdout.close()
      stderr.close()
    }
  }

  val stdout = new java.io.PrintWriter(System.out, true)

  /** Helper class to turn a TextArea widget into a PrintWriter and
      allow redirecting stdout IO to it.
    */
  class TextAreaWriter(ta: javax.swing.JTextArea, maxLines: Int = 20) extends java.io.Writer{
    private val buffer = new StringBuilder()

    def countLines() =
      ta.getText().lines.count(_ => true)

    override def write(arr: Array[Char]) =
      buffer.append(arr.mkString)

    override def write(arr: Array[Char], off: Int, len: Int) =
      buffer.append(arr.slice(off, off + len).mkString)

    override def write(str: String) =
      buffer.append(str)

    override def flush() =
      javax.swing.SwingUtilities.invokeLater { () =>
        ta.append(buffer.toString)
        // Scroll to bottom
        ta.setCaretPosition(ta.getDocument().getLength())
      }

    // Don't do anything - dummy method
    override def close() = ()

    def getPrinter() =
      new java.io.PrintWriter(this, true)

    def clear() = javax.swing.SwingUtilities.invokeLater { () =>
      buffer.clear()
      ta.setText("")
    }
  }

  /** Create PrintWriter object out of a JTextArea object. */
  def makeTextAreaPW(ta: javax.swing.JTextArea): java.io.PrintWriter =
    new java.io.PrintWriter(new TextAreaWriter(ta), true)

} // ---- End of object Utils ----- //


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
