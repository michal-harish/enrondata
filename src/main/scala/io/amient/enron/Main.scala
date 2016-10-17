package io.amient.enron

import java.nio.file.{FileSystems, Files, Paths}

import scala.collection.JavaConverters._
import scala.collection.parallel.ForkJoinTaskSupport

/**
  * Created by mharis on 17/10/2016.
  */
object Main extends App {

  //process arguments
  if (args.length != 1) {
    println("Usage: ./build/scripts/enronapp <DATA-LOCATION>\n\tDATA-LOCATION is the mount location of the Enron ESB snapshot")
    System.exit(1)
  }
  val dataDir = args(0)
  val parallelism = 8
  val startTime = System.currentTimeMillis

  //parallelise mailbox processing
  val zipMailBoxMatcher = FileSystems.getDefault().getPathMatcher(s"glob:**/*_xml.zip")
  val mailboxes = Files.walk(Paths.get(dataDir)).iterator().asScala.toList.filter(zipMailBoxMatcher.matches).par
  mailboxes.tasksupport = new ForkJoinTaskSupport(new scala.concurrent.forkjoin.ForkJoinPool(parallelism))

  //extract and deduplicate emails
  val emails = mailboxes.flatMap(path => new Mailbox(path).emails).toSet.seq.toList

  //calculate stats
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


}
