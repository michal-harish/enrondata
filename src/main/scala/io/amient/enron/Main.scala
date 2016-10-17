package io.amient.enron

import java.io.File
import java.nio.file.{FileSystems, Paths}

import scala.collection.parallel.ForkJoinTaskSupport
import scala.collection.parallel.immutable.ParSet
import scala.util.control.NonFatal

/**
  * Created by mharis on 17/10/2016.
  */
object Main extends App {

  //process arguments
  try {
    if (args.length != 2) throw new IllegalArgumentException
    val parallelism = args(0).toInt
    val dataDir = args(1)
    val startTime = System.currentTimeMillis

    //parallelise mailbox processing
    val dataFiles = new File(dataDir, "edrm-enron-v2").listFiles().toList.map(_.toString).map(Paths.get(_))
    val zipMailBoxMatcher = FileSystems.getDefault().getPathMatcher(s"glob:**/*_xml.zip")
    val mailboxes = dataFiles.filter(zipMailBoxMatcher.matches).par
    mailboxes.tasksupport = new ForkJoinTaskSupport(new scala.concurrent.forkjoin.ForkJoinPool(parallelism))

    //extract and deduplicate emails
    val emails: ParSet[Email] = mailboxes.flatMap(path => new Mailbox(path).emails).toSet

    //calculate stats
    println("calculating stats...")
    val avgWordCount = emails.map(_.wordCount).sum / emails.size
    val top100 = emails.flatMap(email => email.to.map((_, 1.0)) ++ email.cc.map((_, 0.5)))
      .groupBy(_._1).toList.map { case (email, weights) => (weights.map(_._2).sum, email) }
      .sortBy(-_._1)
      .take(100)

    //print result
    println("=============================================================================================")
    println("Top 100 recipients (ascending ranking where the weight of regular recipient is 1.0 and cc = 0.5):")
    top100.reverse.foreach(println)
    println("Top 100 recipients (above) ")
    println("Num. of mailboxes: " + mailboxes.size)
    println("Parallelism: " + parallelism)
    println("Processing time: " + ((System.currentTimeMillis - startTime) / 6000000).toDouble / 100 + " minutes")
    println("Total unique messages: " + emails.size)
    println("Average num.of words per message: " + avgWordCount)

  } catch {
    case e: IllegalArgumentException =>
      println("Usage: ./build/scripts/enronapp <PARALLELISM> <DATA-LOCATION>" +
        "\n\tPARALLELISM\tis the number of mailboxes processed in parallel - depends on the memory capacity of the instance" +
        "\n\tDATA-LOCATION'tis the mount location of the Enron ESB snapshot")
      System.exit(1)

    case NonFatal(e) =>
      e.printStackTrace()
      System.exit(2)
  }
}
