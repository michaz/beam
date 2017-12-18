package beam.integration

import MultinomialCustomConfigSpec.Utility
import beam.integration
import beam.sim.RunBeam
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.xml.{Elem, Node, Text}
import scala.xml.transform.{RewriteRule, RuleTransformer}


object MultinomialCustomConfigSpec  {
  case class Utility(name: String, utype: String, value: String)
  class CustomAlternative(alternativeName: String, utilityValues: Seq[Utility]) extends RewriteRule {
    override def transform(n: Node): Seq[Node] = n match {
      case elem: Elem if elem.label == "alternative" && elem.attributes.exists(m => m.value.text.equals(alternativeName)) =>
        val utilityChild = utilityValues.map{ uv =>
          <param name={uv.name} type={uv.utype}>{uv.value}</param>
        }
        val utility = <utility>{utilityChild}</utility>
        elem.copy(child = utility)
      case n => n
    }
  }


  def fullXml(parameters: Node) = <?xml version="1.0" encoding="utf-8"?>
    <modeChoices>
      <lccm>
        <name>Latent Class Choice Model</name>
        <parameters>lccm-long.csv</parameters>
      </lccm>
      <mnl>
        <name>Multinomial Logit</name>
        <parameters>
          {parameters}
        </parameters>
      </mnl>
    </modeChoices>

  val baseXml =  <multinomialLogit name="mnl">
    <alternative name="car">
      <utility>
        <param name="intercept" type="INTERCEPT">-1</param>
        <param name="cost" type="MULTIPLIER">-1</param>
        <param name="time" type="MULTIPLIER">-1</param>
      </utility>
    </alternative>
    <alternative name="transit">
      <utility>
        <param name="intercept" type="INTERCEPT">-1</param>
        <param name="cost" type="MULTIPLIER">-1</param>
        <param name="time" type="MULTIPLIER">-1</param>
        <param name="transfer" type="MULTIPLIER">-1.4</param>
      </utility>
    </alternative>
    <alternative name="ride_hailing">
      <utility>
        <param name="intercept" type="INTERCEPT">-1</param>
        <param name="cost" type="MULTIPLIER">-1</param>
        <param name="time" type="MULTIPLIER">-1</param>
      </utility>
    </alternative>
    <alternative name="walk">
      <utility>
        <param name="intercept" type="INTERCEPT">-1</param>
        <param name="cost" type="MULTIPLIER">-1</param>
        <param name="time" type="MULTIPLIER">-1</param>
      </utility>
    </alternative>
    <alternative name="bike">
      <utility>
        <param name="intercept" type="INTERCEPT">1</param>
        <param name="cost" type="MULTIPLIER">1</param>
        <param name="time" type="MULTIPLIER">1</param>
      </utility>
    </alternative>
  </multinomialLogit>

}

class MultinomialCustomConfigSpec extends WordSpecLike with Matchers with RunBeam with BeforeAndAfterAll with IntegrationSpecCommon {

  "Running beam with Multinomial ModeChoice custom config" must {
    "Prefer mode choice car type in positive values than negative values " in {

      val transformer1 = new RuleTransformer(
        new MultinomialCustomConfigSpec.CustomAlternative("car", Seq(
          MultinomialCustomConfigSpec.Utility("intercept", "INTERCEPT", "-100"),
          MultinomialCustomConfigSpec.Utility("cost", "MULTIPLIER", "-100"),
          MultinomialCustomConfigSpec.Utility("time", "MULTIPLIER", "-100")
        ))
      )

      val transformer2 = new RuleTransformer(
        new MultinomialCustomConfigSpec.CustomAlternative("car", Seq(
          MultinomialCustomConfigSpec.Utility("intercept", "INTERCEPT", "100"),
          MultinomialCustomConfigSpec.Utility("cost", "MULTIPLIER", "100"),
          MultinomialCustomConfigSpec.Utility("time", "MULTIPLIER", "100")
        ))
      )

      val transformed1 = transformer1(MultinomialCustomConfigSpec.baseXml)
      val transformed2 = transformer2(MultinomialCustomConfigSpec.baseXml)

      val routeConfig1 = Some(MultinomialCustomConfigSpec.fullXml(transformed1).toString())
      val routeConfig2 = Some(MultinomialCustomConfigSpec.fullXml(transformed2).toString())

      val carConfigPositive = new StartWithCustomConfig(modeChoice = Some("ModeChoiceMultinomialLogitTest") ,modeChoiceParameters = routeConfig1)
      val carConfigNegative = new StartWithCustomConfig(modeChoice = Some("ModeChoiceMultinomialLogitTest") ,modeChoiceParameters = routeConfig2)

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
