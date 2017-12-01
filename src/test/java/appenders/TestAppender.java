package appenders;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.spi.LoggingEvent;

import java.util.ArrayList;

public class TestAppender extends AppenderSkeleton {

    ArrayList<LoggingEvent> eventsList = new ArrayList();

    @Override
    protected void append(LoggingEvent event) {
        eventsList.add(event);
    }

    public void close() {
    }

    public boolean requiresLayout() {
        return false;
    }
}
