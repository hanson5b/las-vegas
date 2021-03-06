package edu.brown.lasvegas.costmodels.recovery.sim;

import org.junit.Test;

import edu.brown.lasvegas.costmodels.recovery.sim.FailureSchedule.FailureEvent;
import static org.junit.Assert.*;

/**
 * Testcase for {@link FailureSchedule}.
 */
public class FailureScheduleTest {
	@Test
	public void testDefault () {
		ExperimentalConfiguration config = new ExperimentalConfiguration();
		FailureSchedule schedule = new FailureSchedule(config, 12345L);
		for (int i = 0; i < 30000; ++i) {
			FailureEvent event = schedule.getNextEvent();
			schedule.peekNextEvent();
			assertTrue (event.interval > 0);
			if (event.rackFailure) {
				assertTrue (event.failedNode >= 0);
				assertTrue (event.failedNode < config.racks);
			} else {
				assertTrue (event.failedNode >= 0);
				assertTrue (event.failedNode < config.nodes);
			}
		}
		schedule.debugOut();
	}
}
