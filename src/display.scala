package netmon.display

import javax.swing.{JPanel, JFrame, JButton}
import java.awt.{BorderLayout, FlowLayout}


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

    // Make JTextArea read-only 
    out.setEditable(false)
    out.setFont(new java.awt.Font("monospaced", java.awt.Font.PLAIN, 12))

    frame.setTitle("Internet Connection Status")
    frame.setSize(580, 500)
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
