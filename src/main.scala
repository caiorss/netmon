package netmon.main

import scala.concurrent.Future
import concurrent.ExecutionContext.Implicits.global
import scala.util.{Success, Failure}
import scala.concurrent.Await
import scala.concurrent.duration._

// file: utils.scala 
import netmon.utils.{Utils, NetInfo}

// file: display.scala 
import netmon.display.{Display, GUIUtils}

/** Program Entry Point */
object Main{

  /**  List of pairs with hostnames and IP addresses
    *  [(Host of Google, IP address of google), (Host of yahoo, IP of yahoo), ...]
    *  The IP addresses can be obtained from the command $ ping www.bing.com 
    */
  val probeConfig = List(
    ("www.youtube.com",   "216.58.222.78"),
    ("www.yahoo.com",     "98.138.252.38"),
    ("www.bing.com",      "204.79.197.200"),
    ("www.google.com",    "172.217.30.4"),
    ("www.amazon.com",    "54.192.56.234"),
    ("www.twitter.com",   "104.244.42.129"),
    ("www.microsoft.com", "23.76.243.153"),
    ("www.baidu.com",     "103.235.46.39"),
    ("www.yandex.com",    "213.180.204.62")
  )

  val iconOnline  =
    Utils.getResourceImage("/resources/network-online.jpg", getClass())

  val iconOffline =
    Utils.getResourceImage("/resources/network-offline.png", getClass())

  def monitorFuture[A](task: Future[A], command: String)(printer: String => Unit) = {
    Future {
      while(!task.isCompleted){
        Thread.sleep(500)
        printer(s"Status: Running $command ...")
        Thread.sleep(500)
        printer(s"Status: Running $command ... ...")
      }
      printer("Status: Process finished.")
    }
  }

  val defaultTimeout = 30000

  def main(args: Array[String]) = {
    val disp             = new Display(iconOnline)
    val exitCmd          = GUIUtils.makeCommand{ System.exit(0) }    
    val notImplmentedCmd = GUIUtils.makeCommand{
      GUIUtils.showWarning("Error: Command not implemented", frame = disp)
    }
    val openSiteCmd = GUIUtils.makeCommand{
      NetInfo.findDefaultGateway() match {
        case Some(addr)
            => Utils.openUrl("http://" + addr)
        case None
            => GUIUtils.showWarning(
              "Error: gateway's address not found.",
              "Error Report",
              frame = disp
            )
      }
    }

    val guiDisplayPW = disp.getCommandDisplayWriter()
    //val guiDisplayPW = Utils.stdout

    val pingCommand = GUIUtils.makeCommand{
      // Address of Google's DNS = 8.8.8.8
      disp.clearCommandDisplayWriter()
      val task = Utils.streamProcessOutput(
        "ping", List("8.8.8.8"), timeoutMs = 5000){guiDisplayPW}
      monitorFuture(task, "ping 8.8.8.8"){disp.setProcessStatusText}
    }

    val tracerouteCommand = GUIUtils.makeCommand{      
      val cmd = if(System.getProperty("os.name").contains("windows"))
        "tracert"
      else
        "traceroute"
      disp.clearCommandDisplayWriter()
      val task = Utils.streamProcessOutput(
        cmd, List("8.8.8.8"), defaultTimeout){guiDisplayPW}
      monitorFuture(task, "traceroute 8.8.8.8"){disp.setProcessStatusText}
    }

    val dmsegCommand = GUIUtils.makeCommand{
      disp.clearCommandDisplayWriter()
      val task = Utils.streamProcessOutput(
        "dmesg", List("-T", "--level=err"), defaultTimeout){guiDisplayPW}
      monitorFuture(task, "dmesg"){disp.setProcessStatusText}
    }

    disp.setExitCommand(exitCmd)
    disp.setRefreshCommand(notImplmentedCmd)          
    disp.setOpenSiteCommand(openSiteCmd)

    disp.setPingHostCommand(pingCommand)
    disp.setTracerouteCommand(tracerouteCommand)
    disp.setDmesgCommand(dmsegCommand)      
 
    // disp.setIconImage(iconOnline)
    // State is true for Online and false for offline 
    var state = true 

    // Run action every 5000 milliseconds or 5 seconds 
    Utils.runEvery(5000){
      val time = new java.util.Date()

      // Choose a random probing pair of hostname and address 
      val (host, addr) = Utils.pickRandom(probeConfig)

      NetInfo.checkHTTP(host, addr){ case (status, msg) =>

        val gatewayAddress = NetInfo.findDefaultGateway() 
        val gIpAddr = gatewayAddress getOrElse "Not found"

        val canAccessGateway: Boolean =
          gatewayAddress.map {addr =>
            val flag1 = NetInfo.isPortOpen(addr, 80,  500)
            val flag2 = NetInfo.isPortOpen(addr, 443, 500)
            flag1 || flag2 
          }.getOrElse(false)

        val statusMsg = Utils.withString{ pw =>
          pw.println(msg)
          pw.println()
          pw.println(s"Probe host and address       $host, $addr")
          pw.println(s"Default gateway              $gIpAddr")
          pw.println(s"Can access router's web page $canAccessGateway")
          pw.println( "Last Update                  " + time.toString())
          pw.println()
          pw.println("Network Interfaces Data")
          pw.println("--------------------------------------------------")
          pw.println()
          NetInfo.getIfacesData() foreach pw.println
        }

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

