package beam.physsim

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Stash, Terminated}
import akka.routing._
import beam.router.BeamRouter._
import beam.router.RoutingWorker
import beam.router.gtfs.FareCalculator
import beam.router.r5.NetworkCoordinator
import beam.sim.BeamServices

class TestBeamRouter(services: BeamServices, fareCalculator: FareCalculator) extends Actor with Stash with ActorLogging  {
  var router: Router = _
  var networkCoordinator: ActorRef = _
  private var routerWorkers: Vector[Routee] = _

  var listener: ActorRef = _

  override def preStart(): Unit = {
    routerWorkers = (0 until services.beamConfig.beam.routing.workerNumber).map { workerId =>
      ActorRefRoutee(createAndWatch(workerId))
    }.toVector
    router = Router(SmallestMailboxRoutingLogic(), routerWorkers)
    networkCoordinator = context.actorOf(NetworkCoordinator.props(services))
  }

  def receive = uninitialized

  // Uninitialized state
  def uninitialized: Receive = {
    case InitializeRouter =>
      log.info("Initializing Router.")
      networkCoordinator.forward(InitializeRouter)
      context.become(initializing)
    case RoutingRequest =>
      sender() ! RouterNeedInitialization
    case Terminated(r) =>
      handleTermination(r)
    case msg =>
      log.info(s"Unknown message[$msg] received by Router.")
  }

  // Initializing state
  def initializing: Receive = {
    case InitializeRouter =>
      log.debug("Router initialization in-progress...")
      stash()
    case RoutingRequest =>
      stash()
    case InitTransit =>
      stash()
    case RouterInitialized if sender().path.parent == self.path =>
      unstashAll()
      context.become(initialized)
    case Terminated(r) =>
      handleTermination(r)
    case msg =>
      log.info(s"Unknown message[$msg] received by Router.")
  }

  // Initialized state
  def initialized: Receive = {
    case w: RoutingRequest =>
      log.debug(sender().path + " ****** RoutingRequest received")
      router.route(w, sender())
    case InitTransit =>
      networkCoordinator.forward(InitTransit)
    case InitializeRouter =>
      log.debug("Router already initialized.")
      sender() ! RouterInitialized
    case Terminated(r) =>
      handleTermination(r)
    case UpdateTravelTime(travelTime) =>
      listener ! UpdateTravelTime(travelTime)
      router.route(Broadcast(UpdateTravelTime(travelTime)), sender)
    case msg => {
      if(msg.equals("REGISTER_LISTENER")){
        listener = sender()
      }else {
        log.info(s"Unknown message[$msg] received by Router.")
      }
    }
  }

  def handleTermination(r: ActorRef): Unit = {
    if (r.path.name.startsWith("router-worker-")) {
      val workerId = r.path.name.substring("router-worker-".length).toInt
      router = router.removeRoutee(r)
      val workerActor = createAndWatch(workerId)
      router = router.addRoutee(workerActor)
    } else {
      log.warning(s"Can't resolve router workerId from ${r.path.name}. Invalid actor name")
    }
  }

  private def createAndWatch(workerId: Int): ActorRef = {
    val routerProps = RoutingWorker.getRouterProps(services.beamConfig.beam.routing.routerClass, services, fareCalculator, workerId)
    val r = context.actorOf(routerProps, s"router-worker-$workerId")
    context watch r
  }
}

