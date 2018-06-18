// Scala runtime and distribution version
scalaVersion := "2.12.6"

// import sbt.project

//============ Application Specific Settings ===============
// Application/Program name 
name                 := "netmon"
// Application version 
version              := "1.0"
// Project description
description          := "Network monitor written in Scala."
// Main class points to the main program entry point:
mainClass in Compile := Some("netmon.main.Main")
// DO NOT add scala version to the produced artifacts
crossPaths := false

//============= Dependencies ================================================
//
//
libraryDependencies ++= Seq(
  // Java -dependency format:
  //------------------------------------
  // <groupID> % <artifactID> % <version>
  //"org.codehaus.groovy" % "groovy-all" % "2.4.15"


  // Scala dependency format:
  //------------------------------------
  // <groupID> %% <artifactID> % <version>
)

//============= Overrides project layout =====================================
//   Original Scala Layout      ---  This layout  (Scala-only project)
//   ./src/main/*.files.scala         src/*.scala 
//   ./src/tests                      test/*.tests.scala
//
// Reference: 
//  + https://stackoverflow.com/questions/15476256/how-do-i-specify-a-custom-directory-layout-for-an-sbt-project
//  + https://stackoverflow.com/questions/10131340/changing-scala-sources-directory-in-sbt
//-------------------------------------------------------------------------
// Move classes from src/main/*.scala to ./src/*.scala
scalaSource in Compile := { (baseDirectory in Compile)(_ / "src") }.value
//-------------------------------------------------------------------------
// Move tests from src/tests to ./test 
scalaSource in Test := { (baseDirectory in Test)(_ / "test") }.value
// Move resource directory from ./src/main/resources to ./resources
resourceDirectory in Compile := baseDirectory.value / "resources"


initialize := { System.setProperty("gui.designmode", "true") }


//============= Customs SBT tasks ===================== //
//
//

/** Copy Uber jar to current directory (./)
  * Usage: $ sbt copyUber 
  * 
  * References:
  * + https://stackoverflow.com/questions/47872758/how-can-i-make-a-task-depend-on-another-task
  * + https://www.scala-sbt.org/1.x/docs/Migrating-from-sbt-013x.html#Migrating+from+sbt+0.12+style
  * + http://blog.bstpierre.org/writing-simple-sbt-task
  * + https://www.scala-sbt.org/1.0/docs/Custom-Settings.html
  * 
  *******************************************************************/
val copyUber = TaskKey[Unit]("copyUber", "Run produced uber jar")
copyUber := {
  import java.io.File
  val inpFile = new File(assembly.value.getPath)
  // val outFile = new File(inpFile.getName)
  val outFile = name.value + "-uber.jar"
  val inch = java.nio.channels.Channels.newChannel(
    new java.io.FileInputStream(inpFile))
  val fos = new java.io.FileOutputStream(outFile)
  fos.getChannel().transferFrom(inch, 0, java.lang.Long.MAX_VALUE)
    inch.close()
  println("Created  = " + outFile)
}



