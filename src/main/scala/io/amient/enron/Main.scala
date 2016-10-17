package io.amient.enron

import java.nio.file.{FileSystems, Files, Paths}

import scala.collection.JavaConverters._
import scala.collection.parallel.ForkJoinTaskSupport

/**
  * Created by mharis on 17/10/2016.
  */
object Main extends App {

  if (args.length != 1) {
    println("Usage: ./build/scripts/enronapp <DATA-LOCATION>\n\tDATA-LOCATION is the mount location of the Enron ESB snapshot")
    System.exit(1)
  }
  val dataDir = args(0)

  val zipMailBoxMatcher = FileSystems.getDefault().getPathMatcher(s"glob:**/*_xml.zip")

  val mailboxes = Files.walk(Paths.get(dataDir)).iterator().asScala.toList.filter(zipMailBoxMatcher.matches).par

  println(mailboxes.size)

  mailboxes.tasksupport = new ForkJoinTaskSupport(new scala.concurrent.forkjoin.ForkJoinPool(32))

  val emails = mailboxes.flatMap(path => new Mailbox(path).emails).toSet

  println(emails.size)
  //  mailboxes.foreach { z =>
  //    println(z)
  //  }


}
