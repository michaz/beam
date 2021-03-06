package beam.physsim.jdeqsim;

import akka.actor.ActorRef;
import beam.agentsim.events.PathTraversalEvent;
import beam.router.BeamRouter;
import beam.sim.common.GeoUtils;
import com.conveyal.r5.transit.TransportNetwork;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.events.EventsManagerImpl;
import org.matsim.core.events.handler.BasicEventHandler;
import org.matsim.core.mobsim.jdeqsim.JDEQSimulation;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.scenario.MutableScenario;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.trafficmonitoring.TravelTimeCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;


/**
 * @Authors asif and rwaraich.
 */
public class AgentSimToPhysSimPlanConverter implements BasicEventHandler {


    public static final String CAR = "car";
    public static final String BUS = "bus";
    public static final String DUMMY_ACTIVITY = "DummyActivity";
    private final ActorRef router;
    private final OutputDirectoryHierarchy controlerIO;
    private Logger log = LoggerFactory.getLogger(AgentSimToPhysSimPlanConverter.class);
    private Scenario agentSimScenario;
    private Population jdeqsimPopulation;

    private int numberOfLinksRemovedFromRouteAsNonCarModeLinks;
    private AgentSimPhysSimInterfaceDebugger agentSimPhysSimInterfaceDebugger;

    public AgentSimToPhysSimPlanConverter(EventsManager eventsManager, TransportNetwork transportNetwork, OutputDirectoryHierarchy controlerIO, Scenario scenario, GeoUtils geoUtils, ActorRef router) {
        eventsManager.addHandler(this);
        this.controlerIO = controlerIO;
        this.router = router;
        agentSimScenario = scenario;

        if (AgentSimPhysSimInterfaceDebugger.DEBUGGER_ON){
            log.warn("AgentSimPhysSimInterfaceDebugger is enabled");
            agentSimPhysSimInterfaceDebugger=new AgentSimPhysSimInterfaceDebugger(geoUtils, transportNetwork);
        }

        preparePhysSimForNewIteration();
    }

    private void preparePhysSimForNewIteration() {
        jdeqsimPopulation = PopulationUtils.createPopulation(agentSimScenario.getConfig());
    }


    @Override
    public void reset(int iteration) {

    }

    private void initializeActorsAndRunPhysSim() {
        MutableScenario jdeqSimScenario = (MutableScenario) ScenarioUtils.createScenario(ConfigUtils.createConfig());
        jdeqSimScenario.setNetwork(agentSimScenario.getNetwork());
        jdeqSimScenario.setPopulation(jdeqsimPopulation);
        EventsManager jdeqsimEvents = new EventsManagerImpl();
        TravelTimeCalculator travelTimeCalculator = new TravelTimeCalculator(agentSimScenario.getNetwork(), agentSimScenario.getConfig().travelTimeCalculator());
        jdeqsimEvents.addHandler(travelTimeCalculator);

        JDEQSimulation jdeqSimulation = new JDEQSimulation(agentSimScenario.getConfig().jdeqSim(), jdeqSimScenario, jdeqsimEvents);
        jdeqSimulation.run();
        router.tell(new BeamRouter.UpdateTravelTime(travelTimeCalculator.getLinkTravelTimes()), ActorRef.noSender());
    }

    private void writePhyssimPlans(IterationEndsEvent event) {
        String plansFilename = controlerIO.getIterationFilename(event.getIteration(), "physsim-plans.xml");
        new PopulationWriter(jdeqsimPopulation).write(plansFilename);
    }

    @Override
    public void handleEvent(Event event) {

        if (AgentSimPhysSimInterfaceDebugger.DEBUGGER_ON){
            agentSimPhysSimInterfaceDebugger.handleEvent(event);
        }

        if (event instanceof PathTraversalEvent) {
            PathTraversalEvent pathTraversalEvent = (PathTraversalEvent) event;
            String mode = pathTraversalEvent.getAttributes().get(PathTraversalEvent.ATTRIBUTE_MODE);

            if (mode != null && (mode.equalsIgnoreCase(CAR) || mode.equalsIgnoreCase(BUS))) {

                String links = pathTraversalEvent.getAttributes().get(PathTraversalEvent.ATTRIBUTE_LINK_IDS);
                double departureTime = Double.parseDouble(pathTraversalEvent.getAttributes().get(PathTraversalEvent.ATTRIBUTE_DEPARTURE_TIME));
                String vehicleId = pathTraversalEvent.getAttributes().get(PathTraversalEvent.ATTRIBUTE_VEHICLE_ID);

                Id<Person> personId = Id.createPersonId(vehicleId);
                initializePersonAndPlanIfNeeded(personId);

                // add previous activity and leg to plan
                Person person=jdeqsimPopulation.getPersons().get(personId);
                Plan plan=person.getSelectedPlan();
                Leg leg=createLeg(mode, links, departureTime);

                if (leg==null){
                    return; // dont't process leg further, if empty
                }

                Activity previousActivity = jdeqsimPopulation.getFactory().createActivityFromLinkId(DUMMY_ACTIVITY, leg.getRoute().getStartLinkId());
                previousActivity.setEndTime(departureTime);
                plan.addActivity(previousActivity);
                plan.addLeg(leg);
            }
        }
    }

    private void initializePersonAndPlanIfNeeded(Id<Person> personId) {
        if (!jdeqsimPopulation.getPersons().containsKey(personId)){
            Person person = jdeqsimPopulation.getFactory().createPerson(personId);
            Plan plan = jdeqsimPopulation.getFactory().createPlan();
            plan.setPerson(person);
            person.addPlan(plan);
            person.setSelectedPlan(plan);
            jdeqsimPopulation.addPerson(person);
        }
    }

    private Leg createLeg(String mode, String links, double departureTime) {
        List<Id<Link>> linkIds = new ArrayList<>();

        for (String link : links.equals("") ? new String[]{} : links.split(",")) {
            Id<Link> linkId = Id.createLinkId(link.trim());
            linkIds.add(linkId);
        }


        // hack: removing non-road links from route
        // TODO: debug problem properly, so that no that no events for physsim contain non-road links
        List<Id<Link>> removeLinks = new ArrayList<>();
        for (Id<Link> linkId : linkIds) {
            if (!agentSimScenario.getNetwork().getLinks().containsKey(linkId)) {
                throw new RuntimeException("Link not found: "+linkId);
            }
        }
        numberOfLinksRemovedFromRouteAsNonCarModeLinks += removeLinks.size();
        linkIds.removeAll(removeLinks);

        if (linkIds.size() == 0) {
            return null;
        }
        // end of hack


        Route route = RouteUtils.createNetworkRoute(linkIds, agentSimScenario.getNetwork());
        Leg leg = jdeqsimPopulation.getFactory().createLeg(mode);
        leg.setDepartureTime(departureTime);
        leg.setTravelTime(0);
        leg.setRoute(route);
        return leg;
    }



    public void startPhysSim(IterationEndsEvent iterationEndsEvent) {
        createLastActivityOfDayForPopulation();
        writePhyssimPlans(iterationEndsEvent);
        if (numberOfLinksRemovedFromRouteAsNonCarModeLinks > 0) {
            log.error("number of links removed from route because they are not in the matsim network:" + numberOfLinksRemovedFromRouteAsNonCarModeLinks);
        }
        initializeActorsAndRunPhysSim();

        preparePhysSimForNewIteration();
    }

    private void createLastActivityOfDayForPopulation() {
        for (Person p : jdeqsimPopulation.getPersons().values()) {
            Plan plan = p.getSelectedPlan();
            if (!plan.getPlanElements().isEmpty()) {
                Leg leg = (Leg) plan.getPlanElements().get(plan.getPlanElements().size() - 1);
                plan.addActivity(jdeqsimPopulation.getFactory().createActivityFromLinkId(DUMMY_ACTIVITY, leg.getRoute().getEndLinkId()));
            }
        }
    }
}

