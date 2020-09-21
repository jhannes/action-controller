package org.actioncontroller;

import java.time.Duration;

public interface TimerRegistry {
    TimerRegistry NULL = action -> value -> {};

    interface Timer {
        void update(Duration duration);
    }

    Timer getTimer(ApiControllerAction action);

}
