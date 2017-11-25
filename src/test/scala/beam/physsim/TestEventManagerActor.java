package beam.physsim;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import beam.utils.DebugLib;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.groups.TravelTimeCalculatorConfigGroup;
import org.matsim.core.events.EventsManagerImpl;
import org.matsim.core.trafficmonitoring.TravelTimeCalculator;

import java.util.logging.Logger;

public class TestEventManagerActor extends UntypedActor {
    final static Logger logger = Logger.getLogger(TestEventManagerActor.class.getName());

    ActorRef listener;
    ActorRef jdeqsimActorREF;
    public static final String LAST_MESSAGE = "lastMessage";
    public static final String REGISTER_JDEQSIM_REF = "registerJDEQSimREF";

    private TravelTimeCalculator travelTimeCalculator;
    private EventsManager eventsManager;
    private Network network;

    public TestEventManagerActor(Network network){
        this.network=network;
        resetEventsActor();
    }

    private void resetEventsActor(){
        eventsManager=new EventsManagerImpl();
        TravelTimeCalculatorConfigGroup ttccg = new TravelTimeCalculatorConfigGroup();
        travelTimeCalculator = new TravelTimeCalculator(network, ttccg);
        eventsManager.addHandler(travelTimeCalculator);
    }

    @Override
    public void onReceive(Object msg) throws Exception {
        if (msg instanceof Event) {
            //logger.log(Level.INFO, "Event received -> " + msg);
            eventsManager.processEvent((Event) msg);
        } else if (msg instanceof String) {
            String s = (String) msg;
            if (s.equalsIgnoreCase(LAST_MESSAGE)) {
                //logger.log(Level.INFO, "LAST_MESSAGE received -> going to tell jdeqsimActor about the travelTimeCalculator");
                listener.tell(LAST_MESSAGE, getSelf());
                jdeqsimActorREF.tell(travelTimeCalculator, getSelf());
                resetEventsActor();
            } else if (s.equalsIgnoreCase(REGISTER_JDEQSIM_REF)) {
                //logger.log(Level.INFO, "REGISTER_JDEQSIM_REF received " + REGISTER_JDEQSIM_REF);
                listener.tell(REGISTER_JDEQSIM_REF, getSelf());
                jdeqsimActorREF = getSender();
            } else if (s.equalsIgnoreCase("REGISTER_LISTENER")) {
                //logger.log(Level.INFO, "REGISTER_JDEQSIM_REF received " + REGISTER_JDEQSIM_REF);
                listener = getSender();
            }else {
                DebugLib.stopSystemAndReportUnknownMessageType();
            }
        }  else {
            DebugLib.stopSystemAndReportUnknownMessageType();
        }

    }

    public static Props props(Network network) {
        return Props.create(TestEventManagerActor.class,network);
    }

}
