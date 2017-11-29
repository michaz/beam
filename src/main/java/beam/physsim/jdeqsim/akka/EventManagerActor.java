package beam.physsim.jdeqsim.akka;

import akka.actor.ActorRef;
import akka.actor.Props;
import beam.utils.DebugLib;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.groups.TravelTimeCalculatorConfigGroup;
import org.matsim.core.events.EventsManagerImpl;
import org.matsim.core.trafficmonitoring.TravelTimeCalculator;

public class EventManagerActor extends GenericActor {

    ActorRef jdeqsimActorREF;
    public static final String LAST_MESSAGE = "lastMessage";
    public static final String REGISTER_JDEQSIM_REF = "registerJDEQSimREF";

    private TravelTimeCalculator travelTimeCalculator;
    private EventsManager eventsManager;
    private Network network;

    public EventManagerActor(Network network){
        this.network=network;
        resetEventsActor();
    }

    //An exception or error caused a run to abort: akka.event.jul.JavaLoggingFilter


    private void resetEventsActor(){
        eventsManager=new EventsManagerImpl();
        TravelTimeCalculatorConfigGroup ttccg = new TravelTimeCalculatorConfigGroup();
        travelTimeCalculator = new TravelTimeCalculator(network, ttccg);
        eventsManager.addHandler(travelTimeCalculator);
    }

    @Override
    public void receive(Object msg){
        if (msg instanceof Event) {
            eventsManager.processEvent((Event) msg);
        } else if (msg instanceof String) {
            String s = (String) msg;
            if (s.equalsIgnoreCase(LAST_MESSAGE)) {
                jdeqsimActorREF.tell(travelTimeCalculator, getSelf());
                resetEventsActor();
            } else if (s.equalsIgnoreCase(REGISTER_JDEQSIM_REF)) {
                jdeqsimActorREF = getSender();
            }else {
                DebugLib.stopSystemAndReportUnknownMessageType();
            }
        }  else {
            DebugLib.stopSystemAndReportUnknownMessageType();
        }

    }

    public static Props props(Network network) {
        return Props.create(EventManagerActor.class,network);
    }

}
