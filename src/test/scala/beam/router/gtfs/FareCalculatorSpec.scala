package beam.router.gtfs

import java.nio.file.{Path, Paths}

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import beam.router.gtfs.FareCalculator._
import org.scalatest.WordSpecLike

import scala.language.postfixOps

class FareCalculatorSpec extends WordSpecLike {

  var path: Path = Paths.get("test", "input", "fares")

  "calculate fare from 55448 to 55450" should {
    "return 5.5 fare" in {
      val fareCalculator = new FareCalculator(path.toString)
      assert(fareCalculator.calcFare("CE", "ACE", "55448", "55450") == 5.5)
    }
  }

  "calculate fare with null route id, it" should {
    "return proper fare" in {
      val fareCalculator = new FareCalculator(path.toString)
      assert(fareCalculator.calcFare("CE", null, "55448", "55643") == 9.5)

    }
  }

  "calculate fare from 55448 to 55449 against contains_id" should {
    "return 4.5 fare" in {
      val fareCalculator = new FareCalculator(path.toString)
      assert(fareCalculator.calcFare("CE", "ACE", "55448", "55449") == 4.5)

    }
  }

  "calculate fare with null agency id, it" should {
    "return zero fare" in {
      val fareCalculator = new FareCalculator(path.toString)
      assert(fareCalculator.calcFare(null, null, "55448", "55643") == 0.0)
    }
  }

  "calculate fare with wrong route id, it" should {
    "return zero fare" in {
      val fareCalculator = new FareCalculator(path.toString)
      assert(fareCalculator.calcFare("CE", "1", "55448", "55449") == 0.0)
    }
  }

  "getFareSegments with null origin and destination and provided contains" should {
    "return a segment fare" in {
      val fareCalculator = new FareCalculator(path.toString)
      assert(fareCalculator.calcFare("CE", "ACE", null, null, Set("55448", "55449", "55643")) == 13.75)
    }
  }

  "filterTransferFares with four segments" should {
    "return 3 segments within transfer duration" in {
      val fareCalculator = new FareCalculator(path.toString)
      val fr = fareCalculator.getFareSegments("CE", "ACE", null, null, Set("55448", "55449", "55643")).map(BeamFareSegment(_, 0, 3200)) ++
        fareCalculator.getFareSegments("CE", "ACE", "55643", "55644").map(BeamFareSegment(_, 0, 3800)) ++
        fareCalculator.getFareSegments("CE", "ACE", "55644", "55645").map(BeamFareSegment(_, 0, 4300)) ++
        fareCalculator.getFareSegments("CE", "ACE", "55645", "55645").map(BeamFareSegment(_, 0, 4700))
      val fsf = filterTransferFares(fr)
      assert(fsf.nonEmpty)
      assert(fsf.size == 3)
      assert(fsf.head.fare.transferDuration == 3800)
      assert(fsf.map(_.fare.price).sum == 23.0)
    }
  }

}
