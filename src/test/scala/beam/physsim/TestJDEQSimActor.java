package beam.physsim;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import beam.physsim.jdeqsim.akka.JDEQSimulation;
import beam.router.BeamRouter;
import beam.utils.DebugLib;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.mobsim.jdeqsim.JDEQSimConfigGroup;
import org.matsim.core.trafficmonitoring.TravelTimeCalculator;
import org.matsim.core.utils.collections.Tuple;

import java.util.logging.Logger;


public class TestJDEQSimActor extends UntypedActor {

    final static Logger logger = Logger.getLogger(TestJDEQSimActor.class.getName());


    public static final String START_PHYSSIM = "startPhyssim";
    private JDEQSimulation jdeqSimulation;
    private JDEQSimConfigGroup config;
    private Scenario agentSimScenario;
    private EventsManager events;

    private ActorRef beamRouterRef;
    private Population jdeqSimPopulation;

    private ActorRef listener;

    public TestJDEQSimActor(final JDEQSimConfigGroup config, final Scenario agentSimScenario, final EventsManager events, final ActorRef beamRouterRef) {
        this.config = config;
        this.agentSimScenario = agentSimScenario;
        this.events = events;
        this.beamRouterRef = beamRouterRef;
    }

    // TODO: reset handler properly after each iteration
    private void runPhysicalSimulation(){


        jdeqSimulation = new JDEQSimulation(config, jdeqSimPopulation , events, agentSimScenario.getNetwork(), agentSimScenario.getConfig().plans().getActivityDurationInterpretation());
        jdeqSimulation.run();
        events.finishProcessing();
    }


    @Override
    public void onReceive(Object message) throws Exception {

        if (message instanceof Tuple) {
            Tuple tuple = (Tuple) message;
            String messageString = (String) tuple.getFirst();

            if (messageString.equalsIgnoreCase(START_PHYSSIM)){
                //logger.log(Level.INFO, "Message received -> " + START_PHYSSIM);
                this.jdeqSimPopulation = (Population) tuple.getSecond();
                listener.tell(START_PHYSSIM, getSelf());
                runPhysicalSimulation();
            } else {
                DebugLib.stopSystemAndReportUnknownMessageType();
            }

        } else if (message instanceof TravelTimeCalculator){
            //logger.log(Level.INFO, "Message received -> TravelTimeCalculator going to tell BeamRouter");

            TravelTimeCalculator travelTimeCalculator = (TravelTimeCalculator) message;
            beamRouterRef.tell(new BeamRouter.UpdateTravelTime(travelTimeCalculator.getLinkTravelTimes()), getSelf());
        } else if(message instanceof String){
            if(message.equals("REGISTER_LISTENER")){
                listener = getSender();
            }else {
                DebugLib.stopSystemAndReportUnknownMessageType();
            }
        }
    }


    public static Props props(final JDEQSimConfigGroup config, final Scenario scenario, final EventsManager events, final ActorRef beamRouterRef) {
        return Props.create(TestJDEQSimActor.class, config, scenario, events, beamRouterRef);
    }

}
