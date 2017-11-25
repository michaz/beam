package beam.physsim

import java.util
import java.util.concurrent.TimeUnit

import scala.concurrent.duration._
import akka.pattern.ask
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import beam.agentsim.events.PathTraversalEvent
import beam.physsim.jdeqsim.AgentSimToPhysSimPlanConverter
import beam.physsim.jdeqsim.akka.{AkkaEventHandlerAdapter, EventManagerActor, JDEQSimActor}
import beam.router.BeamRouter.UpdateTravelTime
import beam.sim._
import beam.sim.config.{BeamConfig, BeamLoggingSetup, ConfigModule}
import beam.sim.controler.BeamControler
import beam.sim.controler.corelisteners.{BeamControllerCoreListenersModule, BeamPrepareForSimImpl}
import beam.sim.modules.{AgentsimModule, BeamAgentModule, UtilsModule}
import beam.utils.FileUtils
import beam.utils.reflection.ReflectionUtils
import com.conveyal.r5.streets.StreetLayer
import glokka.Registry
import glokka.Registry.Created
import org.matsim.api.core.v01.{Id, Scenario}
import org.matsim.api.core.v01.events.Event
import org.matsim.api.core.v01.network.Link
import org.matsim.api.core.v01.population._
import org.matsim.core.api.experimental.events.EventsManager
import org.matsim.core.config.{Config, ConfigUtils}
import org.matsim.core.controler.events.{IterationEndsEvent, IterationStartsEvent}
import org.matsim.core.controler.listener.{IterationEndsListener, IterationStartsListener}
import org.matsim.core.controler.{AbstractModule, ControlerI, NewControlerModule, PrepareForSim}
import org.matsim.core.events.EventsUtils
import org.matsim.core.events.handler.{BasicEventHandler, EventHandler}
import org.matsim.core.mobsim.jdeqsim.JDEQSimConfigGroup
import org.matsim.core.population.routes.RouteUtils
import org.matsim.core.scenario.{ScenarioByInstanceModule, ScenarioUtils}
import org.matsim.core.utils.collections.Tuple
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{MustMatchers, WordSpecLike}
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.concurrent.Await

/**
  * Created by asif on 11/11/2017.
  *
  * This test will work as under
  *
  *
  * 1. A network is will be input to the Physsim (JDEQSim)
  * 2. The Physsim will run over this input via a TestJDEQSimActor and hand over events to the EventsManager
  * 3. When Physsim is completed, the events from the EventsManager are sent to the BeamRouter (Our test actor) via EventsManagerActor
  *
  * We need to have the following actors
  * 1. TestActor from TestKit
  * 2. The TestJDEQSimActor
  * 3. The EventsManagerActor
  * 4. The TestActor will be implicitly used to send start message to TestJDEQSimActor
  * 5. The TestJDEQSimActor will hand over events to the EventsManager and will call EventsManager.finishProcessing in the end
  * 6. The EventsManager will use the TestEventManagerActor ref to send a message to the TestActor
  * 7. TestActor will use expectMsg message to check for an UpdateTravelTime message
  *
  * This will conclude our test system
  */
class RunPhyssimSpecReal
  extends TestKit(ActorSystem("beam-physsim--actor-system"))
  with MustMatchers with WordSpecLike with ImplicitSender with MockitoSugar
  with RunBeam with BasicEventHandler with IterationStartsListener with IterationEndsListener{

  var services: BeamServices = _
  val eventsManager: EventsManager = EventsUtils.createEventsManager()

  /////////////////////////
  // These class variables need to be in place before runBeamWithConfigFile method is called
  implicit val timeout = Timeout(50000, TimeUnit.SECONDS)

  val CAR = "car"
  val BUS = "bus"
  val DUMMY_ACTIVITY = "DummyActivity"

  val log = LoggerFactory.getLogger(classOf[RunPhyssimSpecReal])
  var jdeqSimScenario: Scenario = _
  var populationFactory: PopulationFactory = _
  var psEventsManager: EventsManager = _
  var eventHandlerActorREF: ActorRef = _
  var jdeqsimActorREF: ActorRef = _
  var numberOfLinksRemovedFromRouteAsNonCarModeLinks = 0
  ////////////////////////////////

  var RunPhyssimSpecReal: RunPhyssimSpecReal = _
  val __this_instance = this


  override def beamInjector(scenario: Scenario,  matSimConfig: Config,mBeamConfig: Option[BeamConfig] = None): com.google.inject.Injector =
    org.matsim.core.controler.Injector.createInjector(matSimConfig, AbstractModule.`override`(ListBuffer(new AbstractModule() {

      override def install(): Unit = {
        // MATSim defaults
        install(new NewControlerModule)
        install(new ScenarioByInstanceModule(scenario))
        install(new controler.ControlerDefaultsModule)
        install(new BeamControllerCoreListenersModule)

        // Beam Inject below:
        install(new ConfigModule)
        install(new AgentsimModule)
        install(new BeamAgentModule)
        install(new UtilsModule)
      }
    }).asJava, new AbstractModule() {
      override def install(): Unit = {
        // Override MATSim Defaults
        bind(classOf[PrepareForSim]).to(classOf[BeamPrepareForSimImpl])

        // Beam -> MATSim Wirings
        bindMobsim().to(classOf[BeamMobsim]) //TODO: This will change
        //addControlerListenerBinding().to(classOf[AgentSimToPhysSimPlanConverter])
        addControlerListenerBinding().toInstance(__this_instance)
        //addControlerListenerBinding().to(classOf[BeamSim])
        addControlerListenerBinding().to(classOf[TestBeamSim])
        bind(classOf[EventsManager]).toInstance(eventsManager)
        bind(classOf[ControlerI]).to(classOf[BeamControler]).asEagerSingleton()
        mBeamConfig.foreach(beamConfig => bind(classOf[BeamConfig]).toInstance(beamConfig)) //Used for testing - if none passed, app will use factory BeamConfig
      }
    }))

  override def rumBeamWithConfigFile(configFileName: Option[String]) = {
    ReflectionUtils.setFinalField(classOf[StreetLayer], "LINK_RADIUS_METERS", 2000.0)

    //set config filename before Guice start init procedure
    ConfigModule.ConfigFileName = configFileName

    // Inject and use tsConfig instead here
    // Make implicit to be able to pass as implicit arg to constructors requiring config (no need for explicit imports).
    FileUtils.setConfigOutputFile(ConfigModule.beamConfig.beam.outputs.outputDirectory, ConfigModule.beamConfig.beam.agentsim.simulationName, ConfigModule.matSimConfig)

    BeamLoggingSetup.configureLogs(ConfigModule.beamConfig)

    lazy val scenario = ScenarioUtils.loadScenario(ConfigModule.matSimConfig)
    val injector = beamInjector(scenario, ConfigModule.matSimConfig)
    services = injector.getInstance(classOf[BeamServices])
    eventsManager.addHandler(this)
    services.controler.run()
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  override def notifyIterationStarts(event: IterationStartsEvent): Unit = {
    preparePhysSimForNewIteration()
  }

  override def notifyIterationEnds(event: IterationEndsEvent): Unit = {
    startPhysSim()
  }

  // Events Manager overriden methods
  override def reset(iteration: Int): Unit = {
    System.out.println("RunPhyssimSpecReal received reset call")
  }

  override def handleEvent(event: Event): Unit = {
    System.out.println("RunPhyssimSpecReal received event " + event.toString)

    if (event.isInstanceOf[PathTraversalEvent]) {
      val pathTraversalEvent = event.asInstanceOf[PathTraversalEvent]
      val mode = pathTraversalEvent.getAttributes.get(PathTraversalEvent.ATTRIBUTE_MODE)
      if (mode != null && (mode.equalsIgnoreCase(CAR) || mode.equalsIgnoreCase(BUS))) {
        val links = pathTraversalEvent.getAttributes.get(PathTraversalEvent.ATTRIBUTE_LINK_IDS)
        val departureTime = pathTraversalEvent.getAttributes.get(PathTraversalEvent.ATTRIBUTE_DEPARTURE_TIME).toDouble
        val vehicleId = pathTraversalEvent.getAttributes.get(PathTraversalEvent.ATTRIBUTE_VEHICLE_ID)
        val personId = Id.createPersonId(vehicleId)
        initializePersonAndPlanIfNeeded(personId)
        // add previous activity and leg to plan
        val person = jdeqSimScenario.getPopulation.getPersons.get(personId)
        val plan = person.getSelectedPlan
        val leg = createLeg(mode, links, departureTime)
        if (leg == null) return // dont't process leg further, if empty
        val previousActivity = populationFactory.createActivityFromLinkId(DUMMY_ACTIVITY, leg.getRoute.getStartLinkId)
        previousActivity.setEndTime(departureTime)
        plan.addActivity(previousActivity)
        plan.addLeg(leg)
      }
    }
  }

  // Business Logic
  private def initializePersonAndPlanIfNeeded(personId: Id[Person]): Unit = {
    if (!jdeqSimScenario.getPopulation.getPersons.containsKey(personId)) {
      val person = populationFactory.createPerson(personId)
      val plan = populationFactory.createPlan
      plan.setPerson(person)
      person.addPlan(plan)
      person.setSelectedPlan(plan)
      jdeqSimScenario.getPopulation.addPerson(person)
    }
  }

  private def createLeg(mode: String, links: String, departureTime: Double): Leg = {
    val linkIds: util.List[Id[Link]] = new util.ArrayList[Id[Link]]
    for (link <- links.split(",")) {
      val linkId: Id[Link] = Id.createLinkId(link.trim)
      linkIds.add(linkId)
    }
    // hack: removing non-road links from route
    // TODO: debug problem properly, so that no that no events for physsim contain non-road links
    val removeLinks: util.List[Id[Link]] = new util.ArrayList[Id[Link]]
    import scala.collection.JavaConversions._
    for (linkId <- linkIds) {
      if (!services.matsimServices.getScenario.getNetwork.getLinks.containsKey(linkId)) removeLinks.add(linkId)
    }
    numberOfLinksRemovedFromRouteAsNonCarModeLinks += removeLinks.size
    linkIds.removeAll(removeLinks)
    if (linkIds.size == 0) return null
    // end of hack
    val route: Route = RouteUtils.createNetworkRoute(linkIds, services.matsimServices.getScenario.getNetwork)
    val leg: Leg = populationFactory.createLeg(mode)
    leg.setDepartureTime(departureTime)
    leg.setTravelTime(0)
    leg.setRoute(route)
    leg
  }

  def startPhysSim(): Unit = {

    System.out.println("RunPhyssimSpecReal startPhysSim is called")
    createLastActivityOfDayForPopulation()
    //log.info("numberOfLinksRemovedFromRouteAsNonCarModeLinks (for physsim):" + numberOfLinksRemovedFromRouteAsNonCarModeLinks)
    initializeActorsAndRunPhysSim()
    preparePhysSimForNewIteration()
  }

  private def preparePhysSimForNewIteration(): Unit = {
    jdeqSimScenario = ScenarioUtils.createScenario(ConfigUtils.createConfig)
    populationFactory = jdeqSimScenario.getPopulation.getFactory
  }

  @throws[Exception]
  private def registerActor(actorName: String, props: Props) = {
    val future = services.registry ? Registry.Register(actorName, props)
    val actor: ActorRef = Await.result(future, timeout.duration).asInstanceOf[Created].ref
    actor
  }

  def initializeActorsAndRunPhysSim(): Unit = {

    implicit val timeout = Timeout(50000, TimeUnit.SECONDS)

    System.out.println("initializeActorsAndRunPhysSim is called")
    val jdeqSimConfigGroup = new JDEQSimConfigGroup

    try { // TODO: adapt code to send new scenario data to jdeqsim actor each time
      if (eventHandlerActorREF == null) {
      System.out.println("services is null" + services);

        eventHandlerActorREF = Await.result(services.registry ? Registry.Register("EventManagerActorTest",
          TestEventManagerActor.props(services.matsimServices.getScenario.getNetwork)), timeout.duration).asInstanceOf[Created].ref
        psEventsManager = new AkkaEventHandlerAdapter(eventHandlerActorREF)
      }
      if (jdeqsimActorREF == null){
        jdeqsimActorREF = Await.result(services.registry ? Registry.Register("JDEQSimActorTest",
          TestJDEQSimActor.props(jdeqSimConfigGroup, services.matsimServices.getScenario, psEventsManager, services.beamRouter)),
          timeout.duration).asInstanceOf[Created].ref
      }
      /*jdeqsimActorREF.tell(new Tuple[String, Population](TestJDEQSimActor.START_PHYSSIM, jdeqSimScenario.getPopulation), testActor)
      eventHandlerActorREF.tell(TestEventManagerActor.REGISTER_JDEQSIM_REF, jdeqsimActorREF)*/

      // Test cases
      jdeqsimActorREF ! "REGISTER_LISTENER"
      eventHandlerActorREF ! "REGISTER_LISTENER"
      services.beamRouter ! "REGISTER_LISTENER"

      jdeqsimActorREF.tell(new Tuple[String, Population](TestJDEQSimActor.START_PHYSSIM, jdeqSimScenario.getPopulation), ActorRef.noSender)
      eventHandlerActorREF.tell(TestEventManagerActor.REGISTER_JDEQSIM_REF, jdeqsimActorREF)
      ////////
      "TestJDEQSimActor" must {
        "indicate receipt of " + TestJDEQSimActor.START_PHYSSIM + " message" in {
          expectMsgAnyOf(TestJDEQSimActor.START_PHYSSIM, TestEventManagerActor.REGISTER_JDEQSIM_REF)
        }
      }

      "TestEventManagerActor" must {
        "indicate receipt of " + TestEventManagerActor.REGISTER_JDEQSIM_REF + " message" in {
          expectMsgAnyOf(TestEventManagerActor.REGISTER_JDEQSIM_REF, TestJDEQSimActor.START_PHYSSIM)
        }
      }

      "TestEventManagerActor" must {
        "indicates receipt of " + TestEventManagerActor.LAST_MESSAGE + " message" in {
          expectMsg(TestEventManagerActor.LAST_MESSAGE)
        }
      }

      "BeanRouter" must {
        "indicates receipt of TravelTimeCalculator" in {
          expectMsgPF() {
            case UpdateTravelTime(travelTime) => {
              log.info("UpdateTravelTime(travelTime) message is received")
              travelTime
            }
          }
        }
      }
    } catch {
      case e: Exception =>
        e.printStackTrace()
    }
  }

  private def createLastActivityOfDayForPopulation(): Unit = {
    import scala.collection.JavaConversions._
    for (p <- jdeqSimScenario.getPopulation.getPersons.values) {
      val plan = p.getSelectedPlan
      val leg = plan.getPlanElements.get(plan.getPlanElements.size - 1).asInstanceOf[Leg]
      plan.addActivity(populationFactory.createActivityFromLinkId(DUMMY_ACTIVITY, leg.getRoute.getEndLinkId))
    }
  }

  //
  rumBeamWithConfigFile(Option("test/input/beamville/beam.conf"))
}
