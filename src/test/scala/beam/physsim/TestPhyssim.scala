package beam.physsim

import java.io.File

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit}
import beam.physsim.jdeqsim.akka.{AkkaEventHandlerAdapter, EventManagerActor, JDEQSimActor}
import beam.router.BeamRouter.UpdateTravelTime
import beam.sim.BeamServices
import beam.sim.config.{BeamConfig, ConfigModule}
import com.typesafe.config.ConfigFactory
import org.matsim.api.core.v01.Scenario
import org.matsim.api.core.v01.population.Population
import org.matsim.core.api.experimental.events.EventsManager
import org.matsim.core.config.ConfigUtils
import org.matsim.core.mobsim.jdeqsim.JDEQSimConfigGroup
import org.matsim.core.scenario.ScenarioUtils
import org.matsim.core.utils.collections.Tuple
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSpecLike, MustMatchers, WordSpecLike}

/**
  * Created by asif on 11/11/2017.
  *
  * This test will work as under
  *
  *
  * 1. A network is will be input to the Physsim (JDEQSim)
  * 2. The Physsim will run over this input via a JDEQSimActor and hand over events to the EventsManager
  * 3. When Physsim is completed, the events from the EventsManager are sent to the BeamRouter (Our test actor) via EventsManagerActor
  *
  * We need to have the following actors
  * 1. TestActor from TestKit
  * 2. The JDEQSimActor
  * 3. The EventsManagerActor
  * 4. The TestActor will be implicitly used to send start message to JDEQSimActor
  * 5. The JDEQSimActor will hand over events to the EventsManager and will call EventsManager.finishProcessing in the end
  * 6. The EventsManager will use the EventManagerActor ref to send a message to the TestActor
  * 7. TestActor will use expectMsg message to check for an UpdateTravelTime message
  *
  * This will conclude our test system
  */
class TestPhyssim extends TestKit(ActorSystem("beam-physsim--actor-system")) with MustMatchers with WordSpecLike with ImplicitSender with MockitoSugar{

  val jdeqSimConfigGroup = new JDEQSimConfigGroup

  // The following four objects are needed in order to successfully run the JDEQSimulation
  val beamConfig = BeamConfig(ConfigFactory.parseFile(new File("test/input/beamville/beam_50.conf")).resolve())

  val agentSimScenario = ScenarioUtils.createScenario(ConfigUtils.createConfig())
  val network = agentSimScenario.getNetwork

  val jdeqSimScenario: Scenario = agentSimScenario
  val population = jdeqSimScenario.getPopulation

  //
  val eventHandlerActorREF = system.actorOf(EventManagerActor.props(network))
  val eventsManager = new AkkaEventHandlerAdapter(eventHandlerActorREF)
  val jdeqsimActorREF = system.actorOf(JDEQSimActor.props(jdeqSimConfigGroup, agentSimScenario, eventsManager, testActor))
  eventHandlerActorREF.tell(EventManagerActor.REGISTER_JDEQSIM_REF, jdeqsimActorREF)

  "Test Physsim" must {
    "Return TimeTravelCalculator" in {

      jdeqsimActorREF.tell(new Tuple[String, Population](JDEQSimActor.START_PHYSSIM, population), ActorRef.noSender)
      expectMsgPF() {
        case UpdateTravelTime(travelTime) => {

          System.out.println("UpdateTravelTime(travelTime) message is received")
          travelTime
        }
      }
    }
  }
}
