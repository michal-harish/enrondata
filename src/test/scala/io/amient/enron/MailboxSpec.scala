package io.amient.enron

import java.nio.file.Paths

import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}

/**
  * Created by mharis on 17/10/2016.
  */
class MailboxSpec extends FlatSpec with Matchers with BeforeAndAfterAll {


  val dataRootDirectory = Paths.get(this.getClass.getResource("/sample-data").toURI)

  val mailbox1 = new Mailbox(dataRootDirectory, "harris-s")
  val mailbox2 = new Mailbox(dataRootDirectory, "donoho-l")

  override def afterAll(): Unit = {
    mailbox1.close()
    mailbox2.close()
    super.afterAll()
  }

  "Email address extraction" should "work for mixed recipient lists" in {
    val r = "Barker  Courtney &lt;Courtney.Barker@ENRON.com&gt;, McGowan  Mike W. &lt;Mike.McGowan@ENRON.com&gt;, Chavez" +
      "Gabriel &lt;Gabriel.Chavez@ENRON.com&gt;, Dickerson  Steve V &lt;Steve.V.Dickerson@ENRON.com&gt;"
    mailbox1.addresses(r) should equal(
      List("courtney.barker@enron.com", "mike.mcgowan@enron.com", "gabriel.chavez@enron.com", "steve.v.dickerson@enron.com"))

  }

  "Only messages with parsa-ble recipients" should "be included in the calculation" in {
    mailbox1.emails.size should equal(557)
  }

  "Same message" should "appear in multiple mailboxes" in {
    val m1 = mailbox1.emails.filter(_.to == Set("lindy.donoho@enron.com", "mary.darveaux@enron.com", "mary.kay.miller@enron.com")).next
    val m2 = mailbox2.emails.filter(_.to == Set("lindy.donoho@enron.com", "mary.darveaux@enron.com", "mary.kay.miller@enron.com")).next
    m1 should equal(m2)
  }

}
