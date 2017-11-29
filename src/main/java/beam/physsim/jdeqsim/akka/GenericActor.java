package beam.physsim.jdeqsim.akka;

import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;

public abstract class GenericActor extends UntypedActor {

    LoggingAdapter log = Logging.getLogger(context().system(), this);

    @Override
    public void onReceive(Object msg) throws Exception {
        if(msg instanceof String) {
            log.debug("Logging here " + msg);
        }

        receive(msg);
    }

    public abstract void receive(Object msg);
}

