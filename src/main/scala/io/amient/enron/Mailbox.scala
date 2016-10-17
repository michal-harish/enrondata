package io.amient.enron

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, File}
import java.nio.ByteBuffer
import java.nio.file._
import java.security.MessageDigest
import java.util.zip.ZipFile

import org.apache.commons.io.IOUtils

import scala.xml.XML

/**
  * Created by mharis on 17/10/2016.
  */
case class Email(digest: ByteBuffer, wordCount: Long, to: Set[String], cc: Set[String])

class Mailbox(val zipFilePath: Path) {

  def this(dataRootPath: Path, mailboxName: String) = {
    this(dataRootPath.resolve(s"edrm-enron-v2/edrm-enron-v2_${mailboxName}_xml.zip"))
  }

  private val srcZipFile = new ZipFile(zipFilePath.toString)

  private val xmlPathMatcher = FileSystems.getDefault().getPathMatcher(s"glob:**/*.xml")

  private val destDir: Path = Files.createTempDirectory(this.getClass.getSimpleName)

  @volatile private var extracted: List[Email] = null

  /**
    *
    * @return (number of messages, number of words)
    */
  def emails: List[Email] = {
    if (extracted == null) {
      println(s"processing $zipFilePath ...")
      extract()
      extracted = zipCache.toList.filter(x => xmlPathMatcher.matches(x._1)).flatMap { case (path, data) =>
        val xml = XML.load(new ByteArrayInputStream(data))
        val documents = xml \ "Batch" \ "Documents" \ "Document"
        val messages = documents filter (_ \@ "MimeType" == "message/rfc822")

        messages.map { case message =>

          val digest = MessageDigest.getInstance("MD5")

          val to = for (tag <- message \\ "Tag"; if (tag \@ "TagName" == "#To")) yield addresses(tag \@ "TagValue")

          val cc = for (tag <- message \\ "Tag"; if (tag \@ "TagName" == "#CC")) yield addresses(tag \@ "TagValue")

          var t = ""
          val subjectWordCount = for {
            tag <- message \\ "Tag"
            if (tag \@ "TagName" == "#Subject")
          } yield {
            val text = tag \@ "TagValue"
            t += text
            digest.update(text.getBytes)
            countWords(text)
          }

          val bodyWordCount = for {
            file <- message \\ "File"
            externalFile <- file \ "ExternalFile"
            if file \@ "FileType" == "Text"
          } yield {
            val textFilePath = destDir + "/" + (externalFile \@ "FilePath") + "/" + (externalFile \@ "FileName")
            val text = new String(zipCache(Paths.get(textFilePath))).split("\n\r", 2)(1)
            t += text
            digest.update(text.getBytes)
            countWords(text)
          }

          val messageWordCount = (subjectWordCount ++ bodyWordCount).sum
          Email(ByteBuffer.wrap(digest.digest), messageWordCount, to.flatten.toSet, cc.flatten.toSet)
        } filter {
          _.to.size > 0
        }
      }
      close()
      println(s"completed $zipFilePath, num.emails: " + extracted.size)
    }
    extracted
  }

  private def countWords(text: String): Long = text.split("\\s+").length

  val emailAddressPattern = "(([a-zA-Z0-9_\\-\\.]+)@([a-zA-Z0-9\\-\\.]+))".r

  def addresses(recipients: String): List[String] = emailAddressPattern.findAllIn(recipients).matchData.map {
    m => m.group(1).toLowerCase()
  }.toList

  var zipCache = scala.collection.mutable.Map[Path, Array[Byte]]()

  private def extract() = {
    val entries = srcZipFile.entries()
    while (entries.hasMoreElements()) {
      val entry = entries.nextElement()
      if (!entry.getName().contains("native_")) {
        val entryDestination = new File(destDir.toString, entry.getName())
        if (!entry.isDirectory()) {
          val in = srcZipFile.getInputStream(entry)
          val out = new ByteArrayOutputStream(1024)
          try {
            IOUtils.copy(in, out)
            IOUtils.closeQuietly(in)
            out.flush()
            zipCache.put(Paths.get(entryDestination.toString), out.toByteArray)
          } catch {
            case e: java.util.zip.ZipException => println("Invalid zip file: " + entry.getName())
          } finally {
            out.close()
          }
        }
      }
    }
  }

  private def close(): Unit = {
    try {
      srcZipFile.close()
    } finally {
      zipCache.clear()
    }
  }

  private def deleteDirectory(path: File) = if (path.exists()) {
    def getRecursively(f: File): Seq[File] = f.listFiles.filter(_.isDirectory).flatMap(getRecursively) ++ f.listFiles
    getRecursively(path).foreach(f => if (!f.delete()) throw new RuntimeException("Failed to delete " + f.getAbsolutePath))
  }

}
