package netmon.main

import scala.concurrent.Future
import concurrent.ExecutionContext.Implicits.global
import scala.util.{Success, Failure}
import scala.concurrent.Await
import scala.concurrent.duration._

// file: utils.scala 
import netmon.utils.{Utils, NetInfo}

// file: display.scala 
import netmon.display.Display

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
        val gIpAddr = NetInfo.findDefaultGateway() getOrElse "Not found"
        val statusMsg = Utils.joinLines(
          msg,
          s"Probe host and address = $host, $addr",
          s"Default gateway        = $gIpAddr",
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
