package netmon.display

import javax.swing.{JPanel, JFrame, JButton, JLabel, JTabbedPane, JComponent}
import java.awt.{BorderLayout, FlowLayout}


object GUIUtils{

  object Types{
    type Command = java.awt.event.ActionListener
  }

  def makeCommand(handler: => Unit) =     
      new java.awt.event.ActionListener(){
        def actionPerformed(evt: java.awt.event.ActionEvent) = {
          handler
        }
      }    

  def onClick(button: JButton) (handler: => Unit) = {
    button.addActionListener(
      new java.awt.event.ActionListener(){
        def actionPerformed(evt: java.awt.event.ActionEvent) = {
          handler
        }
      }
    )
  }

  def showWarning(message: String, title: String = "Alert", frame: JFrame = null) =
    javax.swing.JOptionPane
      .showMessageDialog(
        frame,
        message,
        title,
        javax.swing.JOptionPane.WARNING_MESSAGE
    )

  def makeTextPanel(text: String) = {
    import java.awt._
    import javax.swing.{JLabel, JPanel}
    val panel = new JPanel(false)
    val filler = new JLabel()
    // filler.setHorizontalAlignment(JLabel.CENTER)
    panel.setLayout(new GridLayout(1, 1))
    panel.add(filler)
    panel
  }

}

/** Main Graphical User Inteface */ 
class Display(ico: java.awt.Image) extends javax.swing.JFrame{
  import netmon.utils.Utils

  private val out               = new javax.swing.JTextArea()
  private val tray              = java.awt.SystemTray.getSystemTray()
  private val toolkit           = java.awt.Toolkit.getDefaultToolkit()
  private val popuMenu          = new java.awt.PopupMenu()
  private val icon              = new java.awt.TrayIcon(ico)
  private val btnRefesh         = new JButton("Refresh")
  private val btnOpenRouterSite = new javax.swing.JButton("Open Router's Site")
  private val btnExit           = new JButton("Exit")
  private val bgColor           = java.awt.Color.WHITE

  private val buttonTraceroute = new JButton("Traceroute 8.8.8.8")
  private val buttonDMSEG      = new JButton("Dmesg - [Unix]")
  private val buttonPingAddr   = new JButton("Ping 8.8.8.8")
  private val processStatus    = new JLabel("Status: = ")
  private val commandOutput    = new javax.swing.JTextArea()
  private val cmdOutputWriter = new Utils.TextAreaWriter(this.commandOutput)

  import GUIUtils.Types.Command

  init()
  private def init(){
    val frame = this

    // ---- Tray Icon Setup ------------- // 
    icon.setToolTip("Network Status Monitoring")
    tray.add(icon)
    icon.setImageAutoSize(true)  

    // Make JTextArea read-only
    out.setEditable(false)
    out.setFont(new java.awt.Font("monospaced", java.awt.Font.PLAIN, 12))


    // ---- Connection Status Pane ------- //
    val panelStatus = GUIUtils.makeTextPanel("Network Information")
    panelStatus.setLayout(new java.awt.BorderLayout())
    val buttonPane = new JPanel()
    buttonPane.setLayout(new java.awt.FlowLayout())
    buttonPane.setBackground(bgColor)
    buttonPane.add(btnRefesh)
    buttonPane.add(btnOpenRouterSite)
    buttonPane.add(btnExit)

    panelStatus.add(buttonPane, BorderLayout.NORTH)
    panelStatus.add(new javax.swing.JScrollPane(out), BorderLayout.CENTER)
    panelStatus.add(popuMenu)

    btnRefesh.setBackground(bgColor)
    btnOpenRouterSite.setBackground(bgColor)
    btnExit.setBackground(bgColor)


    //---- Tools Pane -----------------------//
    val paneTools = GUIUtils.makeTextPanel("Network Tools")
    // Ping Google's DNS
    buttonPingAddr.setBackground(bgColor) 
    buttonTraceroute.setBackground(bgColor)
    buttonDMSEG.setBackground(bgColor)
    val paneButton1 = new JPanel(new java.awt.FlowLayout())
    paneButton1.setBackground(bgColor)
    commandOutput.setEditable(false)
    commandOutput.setFont(new java.awt.Font("monospaced", java.awt.Font.PLAIN, 12))
    paneButton1.add(buttonPingAddr)
    paneButton1.add(buttonTraceroute)
    paneButton1.add(buttonDMSEG)
    paneTools.setLayout(new java.awt.BorderLayout())
    paneTools.add(paneButton1, BorderLayout.NORTH)
    paneTools.add(new javax.swing.JScrollPane(commandOutput), BorderLayout.CENTER)
    paneTools.add(processStatus, BorderLayout.SOUTH)


    //----- Tabbed Pane (main pane) --------------// 

    val tabbedPane = new JTabbedPane()
    tabbedPane.add("Connection Status", panelStatus)
    tabbedPane.add("Network Tools",     paneTools)

    // ----- Frame Setup ---------------- // 

    frame.setContentPane(tabbedPane)    
    frame.setTitle("Internet Connection Status")
    frame.setSize(580, 500)
    frame.setIconImage(ico)
    // frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE)
    frame.getContentPane().setBackground(java.awt.Color.CYAN)
    // frame.setResizable(false)


    var flag = false

    val listener = new java.awt.event.ActionListener(){
      def actionPerformed(event: java.awt.event.ActionEvent){       
        flag = !flag
        frame.setVisible(flag)
      }
    }
    icon.addActionListener(listener)

    //frame.setVisible(true)
  } // --- EOF method init() ---- //

  def setExitCommand(cmd: Command) =
    btnExit.addActionListener(cmd)

  def setOpenSiteCommand(cmd: Command) =
    btnOpenRouterSite.addActionListener(cmd)

  def setRefreshCommand(cmd: Command) =
    btnRefesh.addActionListener(cmd)

  def clearCommandDisplayWriter() = {
    println("Clean display")
    cmdOutputWriter.clear()
  }

  def setProcessStatusText(text: String) =
    processStatus.setText(text)

  def getCommandDisplayWriter(): java.io.PrintWriter =
    cmdOutputWriter.getPrinter()

  def setPingHostCommand(cmd: Command) =
    buttonPingAddr.addActionListener(cmd)

  def setTracerouteCommand(cmd: Command) =
    buttonTraceroute.addActionListener(cmd)

  def setDmesgCommand(cmd: Command) =
    buttonDMSEG.addActionListener(cmd)

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
