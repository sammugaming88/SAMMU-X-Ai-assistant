package com.example

import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testWhatsAppMessageParsing() {
    val regex1 = Regex("""(?i)(?:whatsapp|व्हाट्सएप)\s*(?:पर|pe|par|me|mein)?\s*(?:jao\s*aur\s*|जाओ\s*और\s*)?(.+?)\s*(?:को|ko)\s+(.+?)\s*(?:message|msg|मैसेज)?\s*(?:करो|karo|भेजो|bhejo|send\s*karo|send\s*kar\s*do)""")
    
    val input1 = "WhatsApp पर जाओ और Anshu को Hello message करो"
    val m1 = regex1.find(input1)
    assertNotNull(m1)
    assertEquals("Anshu", m1?.groupValues?.get(1)?.trim()?.replace(Regex("""^(?:par|pe|me|jao|aur|पर|जाओ|और)\s+""", RegexOption.IGNORE_CASE), "")?.trim())
    assertEquals("Hello", m1?.groupValues?.get(2)?.trim())

    val input2 = "WhatsApp par Anshu ko Hello message karo"
    val m2 = regex1.find(input2)
    assertNotNull(m2)
    assertEquals("Anshu", m2?.groupValues?.get(1)?.trim())
    assertEquals("Hello", m2?.groupValues?.get(2)?.trim())

    val regex2 = Regex("""(?i)(?:whatsapp|व्हाट्सएप)\s*(?:par|pe|me)?\s*(.+?)\s*(?:ko|को)\s*(.+?)\s*(?:bhejo|भेजो|send\s*karo)""")
    val input3 = "WhatsApp par Anshu ko Hello bhejo"
    val m3 = regex2.find(input3)
    assertNotNull(m3)
    assertEquals("Anshu", m3?.groupValues?.get(1)?.trim())
    assertEquals("Hello", m3?.groupValues?.get(2)?.trim())
  }

  @Test
  fun testInstagramCommandRecognition() {
    val commands = listOf(
      "Instagram खोलो",
      "Open Instagram",
      "Instagram open करो",
      "Instagram चला दो",
      "open instagram",
      "instagram kholo"
    )

    for (cmd in commands) {
      val lower = cmd.lowercase()
      val isInsta = lower.contains("instagram") || lower.contains("insta")
      assertTrue("Command should contain instagram: $cmd", isInsta)
      val actionKeywords = listOf("खोलो", "kholo", "open", "चला दो", "chala do", "chalao", "start", "शुरू")
      assertTrue("Command should have action keyword: $cmd", actionKeywords.any { lower.contains(it) })
    }
  }
}
