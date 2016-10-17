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
  *
  * @param digest hash of the message text for deduplication
  * @param wordCount number of words in the email
  * @param to regular recipients
  * @param cc cc recipients
  */
case class Email(digest: ByteBuffer, wordCount: Long, to: Set[String], cc: Set[String])


/**
  * A Mailbox Processor for enron v2 XML format
  * @param zipFilePath
  */
class Mailbox(val zipFilePath: Path) {

  def this(dataRootPath: Path, mailboxName: String) = {
    this(dataRootPath.resolve(s"edrm-enron-v2/edrm-enron-v2_${mailboxName}_xml.zip"))
  }

  private val srcZipFile = new ZipFile(zipFilePath.toString)

  private val xmlPathMatcher = FileSystems.getDefault().getPathMatcher(s"glob:**/*.xml")

  private val destDir: Path = Files.createTempDirectory(this.getClass.getSimpleName)

  private val emailAddressPattern = "(([a-zA-Z0-9_\\-\\.]+)@([a-zA-Z0-9\\-\\.]+))".r

  private val zipCache = scala.collection.mutable.Map[Path, Array[Byte]]()

  @volatile private var data: List[Email] = null

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

  def countWords(text: String): Long = text.split("\\s+").length

  def addresses(recipients: String): List[String] = {
    emailAddressPattern.findAllIn(recipients).matchData.map {
      m => m.group(1).toLowerCase()
    }.toList
  }

  /**
    * @return a List of Email objects extracted from the zip archive
    */
  def emails: List[Email] = {
    if (data == null) {
      println(s"processing $zipFilePath ...")
      extract()
      data = zipCache.toList.filter(x => xmlPathMatcher.matches(x._1)).flatMap { case (path, data) =>
        val xml = XML.load(new ByteArrayInputStream(data))
        val documents = xml \ "Batch" \ "Documents" \ "Document"
        val messages = documents filter (_ \@ "MimeType" == "message/rfc822")

        messages.map { case message =>

          val to = for (tag <- message \\ "Tag"; if (tag \@ "TagName" == "#To")) yield addresses(tag \@ "TagValue")
          val cc = for (tag <- message \\ "Tag"; if (tag \@ "TagName" == "#CC")) yield addresses(tag \@ "TagValue")
          val digest = MessageDigest.getInstance("MD5")
          val subjectWordCount = for {
            tag <- message \\ "Tag"
            if (tag \@ "TagName" == "#Subject")
          } yield {
            val text = tag \@ "TagValue"
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
      println(s"completed $zipFilePath, num.emails: " + data.size)
    }
    data
  }



}
