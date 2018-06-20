package netmon.gui

import javax.swing.{JPanel, JFrame, JButton, JLabel, JTabbedPane, JComponent}
import java.awt.{BorderLayout, FlowLayout}

/** Graphical user interface utilities */
object GUIUtils{

  object Types{
    type Command = java.awt.event.ActionListener
  }

  import Types.Command

  /** Check if GUI is in design mode - for instance when running program in sbt */
  def isDesignMode(): Boolean = {
    val p = System.getProperty("gui.designmode")
    p != null && p == "true"
  }

  def makeCommand(handler: => Unit) : Command =
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

  /** Note the ExitCommand doesn't work when the program is in design mode
    * The program is running in design mode when the property gui.designmode is set to 'true'
    * */
  def makeExitCommand(frame: javax.swing.JFrame = null) : Command =
     makeCommand{
      if(!isDesignMode())
        System.exit(0)
      else
        showWarning(title = "Error report", message = "Error: it doesn't work on design mode.", frame = frame)
    }

  val restartCommand : Command = makeCommand{
    import java.io.File
    val sep = File.separator
    val javaProgram = System.getProperty("java.home") + sep + "bin" + sep + "java"
    val thisJar     = new File(getClass().getProtectionDomain().getCodeSource().getLocation().toURI)
    val command = new java.util.ArrayList[String]()
    // println("Java program = " + javaProgram)
    // println("Thisjar      = " + thisJar)
    command.add(javaProgram)
    command.add("-jar")
    command.add(thisJar.getPath)
    val builder = new ProcessBuilder(command)
    Thread.sleep(200)
    builder.start()
    Thread.sleep(200)
    System.exit(0)
  }

}
