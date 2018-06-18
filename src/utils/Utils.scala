package netmon.utils

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

  /** Stream process or shell command to output in async mode (without block current thread.)  */
  def streamProcessOutput(
                           command:   String,
                           arguments: List[String] = List(),
                           timeoutMs: Int          = 5000,
                           debug:     Boolean      = false
                          )(out: java.io.PrintWriter) = Future {
    if(debug) println("Running command = " + command)

    val pb = new java.lang.ProcessBuilder(command)
    arguments foreach pb.command.add
    val proc = pb.start()
    //process = proc
    val stdout = new java.util.Scanner(proc.getInputStream())
    val stderr = new java.util.Scanner(proc.getErrorStream())
    // Read process output in a new thread
    val futStdout = Future {
      if(debug) println("Start reading stdout lines")
      while(stdout.hasNextLine()) {
        val line = stdout.nextLine()
        out.println(line)
        if(debug) System.out.println(line)
      }
      if(debug) println("Finish reading stdout lines")
    }

    val futStderr = Future {
      if(debug) println("Start reading stderr lines")
      while(stderr.hasNextLine()){
        val line = stderr.nextLine()
        out.println(line)
        if(debug) System.err.println(line)
      }
      if(debug) println("Finish reading stderr lines")
    }

    val fut = Future.sequence(List(futStdout, futStderr))
    fut.onSuccess{ case res => 
      out.println("Process ended with exit status = " + proc.exitValue())
    }
    
    if(debug) println("Waiting process output ...")
    // Monitor process thread waiting it finish until timeout is reached.
    // If timeout is reached, kill the process.
    try Await.result(fut, timeoutMs.milliseconds)
    catch {
      case ex: java.util.concurrent.TimeoutException
          => {
            proc.destroy()
            out.println("\nProcess killed after timeout exceeded")
            if(debug) println(s"Process killed <$command> due to timeout expiration.")
          }
    } finally {
      stdout.close()
      stderr.close()
      if(debug) println(s"End process <$command> execution")
    }
  } // --- End of function streamProcessOutput --- //

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


