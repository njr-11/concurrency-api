/*
 * Copyright (c) 2013, 2021 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package jakarta.enterprise.concurrent.api.ManagedTaskListener;

import java.util.List;
import java.util.concurrent.Future;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import jakarta.enterprise.concurrent.tck.framework.TestClient;
import jakarta.enterprise.concurrent.tck.framework.TestUtil;
import jakarta.enterprise.concurrent.api.common.Util;
import jakarta.enterprise.concurrent.api.common.managedTaskListener.ListenerEvent;
import jakarta.enterprise.concurrent.api.common.managedTaskListener.ManagedTaskListenerImpl;
import jakarta.enterprise.concurrent.tck.framework.TestLogger;
import jakarta.enterprise.concurrent.ManagedExecutorService;
import jakarta.enterprise.concurrent.ManagedExecutors;

public class ManagedTaskListenerTests extends TestClient {

	private static final TestLogger log = TestLogger.get(ManagedTaskListenerTests.class);

	private static final long LISTENER_MAX_WAIT_MILLIS = 3 * 1000;// (ms)

	private static final int LISTENER_POOL_INTERVAL_MILLIS = 100;// (ms)

	private ManagedExecutorService managedExecutorSvc;

	private ManagedTaskListenerImpl managedTaskListener;

	@BeforeClass // TODO BeforeClass or BeforeTest
	public void setup() {
		managedExecutorSvc = Util.getManagedExecutorService();
		managedTaskListener = new ManagedTaskListenerImpl();
	}

	@AfterClass // TODO AfterClass or AfterTest
	public void cleanup() {
		managedTaskListener.clearEvents();
	}

	/*
	 * @testName: TaskAborted
	 * 
	 * @assertion_ids:
	 * CONCURRENCY:JAVADOC:38;CONCURRENCY:SPEC:7;CONCURRENCY:SPEC:7.1;CONCURRENCY:
	 * SPEC:45.3;
	 * 
	 * @test_Strategy: taskAborted of ManagedTaskListener is Called when a task's
	 * Future has been cancelled anytime during the life of a task.
	 */
	@Test
	public void TaskAborted() {
		int blockTime = 3000;// (ms)
		Runnable runnableTask = new RunnableTaskWithStatus(managedTaskListener, blockTime);
		Runnable taskWithListener = ManagedExecutors.managedTask(runnableTask, managedTaskListener);
		Future<?> futureResult = managedExecutorSvc.submit(taskWithListener);
		TestUtil.sleepMsec(1000);
		futureResult.cancel(true);
		Util.waitForListenerComplete(managedTaskListener, blockTime + LISTENER_MAX_WAIT_MILLIS,
				LISTENER_POOL_INTERVAL_MILLIS);
		List<ListenerEvent> events = managedTaskListener.events();
		assertTrue("Listener taskAborted failed", events.contains(ListenerEvent.ABORTED));
		assertTrue("Listener taskAborted failed", futureResult.isCancelled());
	}

	/*
	 * @testName: TaskDone
	 * 
	 * @assertion_ids:
	 * CONCURRENCY:JAVADOC:39;CONCURRENCY:SPEC:13.3;CONCURRENCY:SPEC:45.3;
	 * 
	 * @test_Strategy: TaskDone is called when a submitted task has completed
	 * running, either successfully or failed .
	 */
	@Test
	public void TaskDone() {
		// in cancel case
		final int blockTime = 3000;// (ms)
		Runnable taskToCancelled = new RunnableTaskWithStatus(managedTaskListener, blockTime);
		Runnable taskToCancelledWithListener = ManagedExecutors.managedTask(taskToCancelled, managedTaskListener);
		Future<?> futureResult = managedExecutorSvc.submit(taskToCancelledWithListener);
		TestUtil.sleepMsec(1000);
		futureResult.cancel(true);
		Util.waitForListenerComplete(managedTaskListener, blockTime + LISTENER_MAX_WAIT_MILLIS,
				LISTENER_POOL_INTERVAL_MILLIS);
		List<ListenerEvent> events = managedTaskListener.events();
		assertTrue("Listener taskDone failed in cancel case.", events.contains(ListenerEvent.DONE));
		managedTaskListener.clearEvents();

		// in normal case
		Runnable runTask = new RunnableTaskWithStatus(managedTaskListener);
		Runnable runtaskWithListener = ManagedExecutors.managedTask(runTask, managedTaskListener);
		managedExecutorSvc.submit(runtaskWithListener);
		Util.waitForListenerComplete(managedTaskListener, LISTENER_MAX_WAIT_MILLIS, LISTENER_POOL_INTERVAL_MILLIS);
		List<ListenerEvent> runevents = managedTaskListener.events();
		assertTrue("Listener TaskDone failed", runevents.contains(ListenerEvent.DONE));
		managedTaskListener.clearEvents();

		// in exception case
		Runnable taskWithException = new RunnableTaskWithException(managedTaskListener);
		Runnable taskWithExceptionListener = ManagedExecutors.managedTask(taskWithException, managedTaskListener);
		managedExecutorSvc.submit(taskWithExceptionListener);
		Util.waitForListenerComplete(managedTaskListener, LISTENER_MAX_WAIT_MILLIS, LISTENER_POOL_INTERVAL_MILLIS);
		List<ListenerEvent> runeventsWithException = managedTaskListener.events();
		log.fine("++ runeventsWithException : " + runeventsWithException);
		assertTrue("Listener TaskDone failed with exception task.",
				runeventsWithException.contains(ListenerEvent.DONE));
	}

	/*
	 * @testName: TaskStarting
	 * 
	 * @assertion_ids:
	 * CONCURRENCY:JAVADOC:40;CONCURRENCY:SPEC:7;CONCURRENCY:SPEC:7.3;CONCURRENCY:
	 * SPEC:45.3;
	 * 
	 * @test_Strategy: TaskStarting is called before the task is about to start. The
	 * task will not enter the starting state until the taskSubmitted listener has
	 * completed.
	 */
	@Test
	public void TaskStarting() {
		Runnable runnableTask = new RunnableTaskWithStatus(managedTaskListener);
		Runnable taskWithListener = ManagedExecutors.managedTask(runnableTask, managedTaskListener);
		managedExecutorSvc.submit(taskWithListener);
		Util.waitForListenerComplete(managedTaskListener, LISTENER_MAX_WAIT_MILLIS, LISTENER_POOL_INTERVAL_MILLIS);
		List<ListenerEvent> events = managedTaskListener.events();
		int submitAt = events.indexOf(ListenerEvent.SUBMITTED);
		int startAt = events.indexOf(ListenerEvent.STARTING);
		int runAt = events.indexOf(ListenerEvent.TASK_RUN);
		if (!(submitAt == 0 && startAt == 1) && runAt == 2) {
			fail("Listener TaskStarting failed to run in expected order");
		}
	}

	/*
	 * @testName: TaskSubmitted
	 * 
	 * @assertion_ids:
	 * CONCURRENCY:JAVADOC:41;CONCURRENCY:SPEC:7;CONCURRENCY:SPEC:7.2;CONCURRENCY:
	 * SPEC:45.3;
	 * 
	 * @test_Strategy: TaskSubmitted is called after the task has been submitted to
	 * the Executor. The task will not enter the starting state until the
	 * taskSubmitted listener has completed.
	 */
	@Test
	public void TaskSubmitted() {
		Runnable runnableTask = new RunnableTaskWithStatus(managedTaskListener);
		Runnable taskWithListener = ManagedExecutors.managedTask(runnableTask, managedTaskListener);
		managedExecutorSvc.submit(taskWithListener);
		Util.waitForListenerComplete(managedTaskListener, LISTENER_MAX_WAIT_MILLIS, LISTENER_POOL_INTERVAL_MILLIS);
		List<ListenerEvent> events = managedTaskListener.events();
		int submitAt = events.indexOf(ListenerEvent.SUBMITTED);
		assertEquals("Listener TaskSubmitted failed to run in expected order", 0, submitAt);
	}

}
