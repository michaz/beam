package beam.integration

import beam.sim.RunBeam
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class MultinomialCustomConfigSpec extends WordSpecLike with Matchers with RunBeam with BeforeAndAfterAll with IntegrationSpecCommon {


  "Running beam with Multinomial ModeChoice custom config" must {
    "Prefer mode choice car type in positive values than negative values " in {

      val routeConfig1 = Some(s"${System.getenv("PWD")}/test/input/beamville/r5/ModeChoiceParametersTest/modeChoiceParametersCar1.xml")
      val routeConfig2 = Some(s"${System.getenv("PWD")}/test/input/beamville/r5/ModeChoiceParametersTest/modeChoiceParametersCar2.xml")

      val carConfigPositive = new StartWithCustomConfig(modeChoice = Some("ModeChoiceMultinomialLogit") ,modeChoiceParameters = routeConfig1)
      val carConfigNegative = new StartWithCustomConfig(modeChoice = Some("ModeChoiceMultinomialLogit") ,modeChoiceParameters = routeConfig2)

      val countPositive = carConfigPositive.groupedCount.get("car").getOrElse(0);
      val countNegative = carConfigNegative.groupedCount.get("car").getOrElse(0);

      println("CAR __________>")
      println("Positive: " + countPositive)
      println("Negative: " + countNegative)
      println("__________________________________")

      countPositive should be >= countNegative
    }

    "Prefer mode choice bike type in positive values than negative values " in {

      val routeConfig1 = Some(s"${System.getenv("PWD")}/test/input/beamville/r5/ModeChoiceParametersTest/modeChoiceParametersBike1.xml")
      val routeConfig2 = Some(s"${System.getenv("PWD")}/test/input/beamville/r5/ModeChoiceParametersTest/modeChoiceParametersBike2.xml")

      val carConfigPositive = new StartWithCustomConfig(modeChoice = Some("ModeChoiceMultinomialLogit") ,modeChoiceParameters = routeConfig1)
      val carConfigNegative = new StartWithCustomConfig(modeChoice = Some("ModeChoiceMultinomialLogit") ,modeChoiceParameters = routeConfig2)

      val countPositive = carConfigPositive.groupedCount.get("bike").getOrElse(0);
      val countNegative = carConfigNegative.groupedCount.get("bike").getOrElse(0);

      println("Bike __________>")
      println("Positive: " + countPositive)
      println("Negative: " + countNegative)
      println("__________________________________")

      countPositive should be >= countNegative
    }

    "Prefer mode choice ride hailing type in positive values than negative values " in {

      val routeConfig1 = Some(s"${System.getenv("PWD")}/test/input/beamville/r5/ModeChoiceParametersTest/modeChoiceParametersRideHailing1.xml")
      val routeConfig2 = Some(s"${System.getenv("PWD")}/test/input/beamville/r5/ModeChoiceParametersTest/modeChoiceParametersRideHailing2.xml")

      val carConfigPositive = new StartWithCustomConfig(modeChoice = Some("ModeChoiceMultinomialLogit") ,modeChoiceParameters = routeConfig1)
      val carConfigNegative = new StartWithCustomConfig(modeChoice = Some("ModeChoiceMultinomialLogit") ,modeChoiceParameters = routeConfig2)

      val countPositive = carConfigPositive.groupedCount.get("ride_hailing").getOrElse(0);
      val countNegative = carConfigNegative.groupedCount.get("ride_hailing").getOrElse(0);

      println("Ride Hailing __________>")
      println("Positive: " + countPositive)
      println("Negative: " + countNegative)
      println("__________________________________")

      countPositive should be >= countNegative
    }

    "Prefer mode choice transit type in positive values than negative values " in {

      val routeConfig1 = Some(s"${System.getenv("PWD")}/test/input/beamville/r5/ModeChoiceParametersTest/modeChoiceParametersTransit1.xml")
      val routeConfig2 = Some(s"${System.getenv("PWD")}/test/input/beamville/r5/ModeChoiceParametersTest/modeChoiceParametersTransit2.xml")

      val carConfigPositive = new StartWithCustomConfig(modeChoice = Some("ModeChoiceMultinomialLogit") ,modeChoiceParameters = routeConfig1)
      val carConfigNegative = new StartWithCustomConfig(modeChoice = Some("ModeChoiceMultinomialLogit") ,modeChoiceParameters = routeConfig2)

      val countPositive = carConfigPositive.groupedCount.get("transit").getOrElse(0);
      val countNegative = carConfigNegative.groupedCount.get("transit").getOrElse(0);

      println("Transit __________>")
      println("Positive: " + countPositive)
      println("Negative: " + countNegative)
      println("__________________________________")

      countPositive should be >= countNegative
    }

    "Prefer mode choice walk type in positive values than negative values " in {

      val routeConfig1 = Some(s"${System.getenv("PWD")}/test/input/beamville/r5/ModeChoiceParametersTest/modeChoiceParametersWalk1.xml")
      val routeConfig2 = Some(s"${System.getenv("PWD")}/test/input/beamville/r5/ModeChoiceParametersTest/modeChoiceParametersWalk2.xml")

      val carConfigPositive = new StartWithCustomConfig(modeChoice = Some("ModeChoiceMultinomialLogit") ,modeChoiceParameters = routeConfig1)
      val carConfigNegative = new StartWithCustomConfig(modeChoice = Some("ModeChoiceMultinomialLogit") ,modeChoiceParameters = routeConfig2)

      val countPositive = carConfigPositive.groupedCount.get("walk").getOrElse(0);
      val countNegative = carConfigNegative.groupedCount.get("walk").getOrElse(0);

      println("WAlk __________>")
      println("Positive: " + countPositive)
      println("Negative: " + countNegative)
      println("__________________________________")

      countPositive should be >= countNegative
    }





  }

}
