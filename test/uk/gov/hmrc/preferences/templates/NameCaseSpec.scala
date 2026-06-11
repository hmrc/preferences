/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.templates

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class NameCaseSpec extends AnyWordSpec with Matchers {

  val properNames = List(
    "Keith",
    "Leigh-Williams",
    "McCarthy",
    "O'Callaghan",
    "St. John",
    "Wane von Streit",
    "Brian van Dyke",
    "Van",
    "ap Llwyd Dafydd",
    "Al",
    // "el Grecco",
    "Ben Gurion",
    "Ben",
    "Leonardo da Vinci",
    "Leonardo di Caprio",
    "John du Pont",
    "Rob de Legate",
    "Sole del Crond",
    "Mary der Sind",
    "Fred van der Post",
    "Matt von Trapp",
    "Elie le Figaro",
    "Mack Knife",
    "Dougal MacDonald"
  )

  val badNamesMap = Map(
    "DR SARAH BEETLE"        -> "Dr Sarah Beetle",
    "june O'LEARY"           -> "June O'Leary",
    "MICHAEL JOHN JACOBS JR" -> "Michael John Jacobs Jr",
    // "MR. jon whitacre iii" -> "Mr. Jon Whitacre III",
    "MARY BETH DAVIDSON MD" -> "Mary Beth Davidson MD",
    "MS LAURA CONLEY-ROSE"  -> "Ms Laura Conley-Rose",
    "LAURA&DAVID SMITH"     -> "Laura&David Smith",
    "ESTATE OF LAURA JONES" -> "Estate of Laura Jones",
    "MS MS. LAURA J BYRD"   -> "Ms Ms. Laura J Byrd",
    "ben mcgrath"           -> "Ben McGrath",
    "al gore"               -> "Al Gore",
    // "AHARON BEN AMRAM HA-KOHEIN" -> "Aharon ben Amram Ha-Kohein",
    // "MIRIAM BAT RIVKAH" -> "Miriam bat Rivkah",
    "anton macevicius"                           -> "Anton Macevicius",
    "kelechi okoro"                              -> "Kelechi Okoro",
    "kelechi okoro on behalf of alex olkhovskiy" -> "Kelechi Okoro on behalf of Alex Olkhovskiy",
    "vincent VAN gogh"                           -> "Vincent van Gogh",
    "VAN gogh"                                   -> "Van Gogh",
    "george herbert, 5th earl of carnarvon"      -> "George Herbert, 5th Earl of Carnarvon",
    "baldrick, son of robin the dung gatherer"   -> "Baldrick, Son of Robin the Dung Gatherer"
  )

  "NameCase nc" should {
    properNames.foreach { name =>
      s"fix '${name.toLowerCase}' to '$name' " in {
        NameCase.nc(name.toLowerCase) must be(name)
      }
    }

    badNamesMap.keys.foreach { name =>
      s"fix '$name' to '${badNamesMap(name)}' " in {
        NameCase.nc(name) must be(badNamesMap(name))
      }
    }
  }
}
