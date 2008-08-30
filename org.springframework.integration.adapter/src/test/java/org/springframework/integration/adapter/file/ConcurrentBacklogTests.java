package org.springframework.integration.adapter.file;

import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;
import org.springframework.beans.DirectFieldAccessor;

@SuppressWarnings("unchecked")
public class ConcurrentBacklogTests {

	@Test(timeout = 1000)
	public void simultaniousPreparation() throws Exception {
		final Backlog backlog = new Backlog();
		backlog.processSnapshot(Arrays.asList(new String[] { "bert", "ernie", "pino", "whatsherface" }));
		Runnable todo = new Runnable() {
			public void run() {
				backlog.prepareForProcessing(1);
			}
		};
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = doConcurrently(5, todo, start);
		start.countDown();
		done.await();
		assertTrue(backlog.isEmpty());
	}

	@Test(timeout = 1000)
	public void concurrentUnloading() throws Exception {
		final Backlog backlog = new Backlog();
		List<String> items = Arrays.asList(new String[] { "bert", "ernie", "pino", "whatsherface", "kaas", "pasf" });
		backlog.processSnapshot(items);
		final AtomicBoolean properlyUnloaded = new AtomicBoolean(false);
		Runnable todo = new Runnable() {
			public void run() {
				backlog.prepareForProcessing(2);
				backlog.processed();
				properlyUnloaded.set(backlog.isEmpty() && backlog.getProcessingBuffer().isEmpty());
			}
		};
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = doConcurrently(3, todo, start);
		start.countDown();
		done.await();
		assertTrue("currentlyProcessing not emptied correctly", ((Collection) new DirectFieldAccessor(backlog)
				.getPropertyValue("currentlyProcessing")).isEmpty());
		assertTrue("doneProcessing not populated correctly", ((Collection) new DirectFieldAccessor(backlog)
				.getPropertyValue("doneProcessing")).containsAll(items));
	}

	@Test(timeout = 1000)
	public void concurrentFailing() throws Exception {
		final Backlog backlog = new Backlog();
		List<String> items = Arrays.asList(new String[] { "bert", "ernie", "pino", "whatsherface", "kaas", "pasf" });
		backlog.processSnapshot(items);
		final AtomicBoolean properlyBackedUp = new AtomicBoolean(false);
		Runnable todo = new Runnable() {
			public void run() {
				backlog.prepareForProcessing(2);
				backlog.processingFailed();
				properlyBackedUp.set(backlog.getProcessingBuffer().isEmpty());
			}
		};
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = doConcurrently(3, todo, start);
		start.countDown();
		done.await();
		assertTrue("currentlyProcessing not emptied correctly", ((Collection) new DirectFieldAccessor(backlog)
				.getPropertyValue("currentlyProcessing")).isEmpty());
		assertTrue("backlog not repopulated correctly", ((Collection) new DirectFieldAccessor(backlog)
				.getPropertyValue("backlog")).containsAll(items));
	}

	@Test(timeout = 1000)
	public void concurrentSuccessFailure() throws Exception {
		final Backlog backlog = new Backlog();
		List<String> items = Arrays.asList(new String[] { "bert", "ernie", "pino", "whatsherface" });
		backlog.processSnapshot(items);
		final AtomicBoolean properlyBackedUp = new AtomicBoolean(true);
		final AtomicBoolean properlyUnloaded = new AtomicBoolean(true);
		Runnable doFailure = new Runnable() {
			public void run() {
				backlog.prepareForProcessing(1);
				backlog.processingFailed();
				properlyBackedUp.set(backlog.getProcessingBuffer().isEmpty() && properlyBackedUp.get());
			}
		};
		Runnable doSuccess = new Runnable() {
			public void run() {
				backlog.prepareForProcessing(1);
				backlog.processed();
				properlyUnloaded.set(backlog.getProcessingBuffer().isEmpty() && properlyUnloaded.get());
			}
		};
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch doneSuccess = doConcurrently(2, doSuccess, start);
		CountDownLatch doneFailure = doConcurrently(3, doFailure, start);
		start.countDown();
		doneSuccess.await();
		doneFailure.await();
		assertTrue(properlyBackedUp.get());
		assertTrue(properlyUnloaded.get());
		assertTrue("currentlyProcessing not emptied correctly", ((Collection) new DirectFieldAccessor(backlog)
				.getPropertyValue("currentlyProcessing")).isEmpty());
		Collection backlogQueue = (Collection) new DirectFieldAccessor(backlog).getPropertyValue("backlog");
		assertTrue("backlog not repopulated correctly size is " + backlogQueue.size(), backlogQueue.size() == 2);
		Collection doneProcessing = (Collection) new DirectFieldAccessor(backlog).getPropertyValue("doneProcessing");
		assertTrue("doneProcessing not repopulated correctly size is " + doneProcessing.size(),
				doneProcessing.size() == 2);
	}

	/**
	 * Convenience method to run part of a test concurrently in multiple threads
	 * 
	 * @param numberOfThreads
	 * @param todo the runnable that should be run by all the threads
	 * @return a latch that will be counted down once all threads have run their
	 * runnable.
	 */
	private CountDownLatch doConcurrently(int numberOfThreads, final Runnable todo, final CountDownLatch start) {
		final CountDownLatch started = new CountDownLatch(numberOfThreads);
		final CountDownLatch done = new CountDownLatch(numberOfThreads);
		for (int i = 0; i < numberOfThreads; i++) {
			new Thread(new Runnable() {

				public void run() {
					started.countDown();
					try {
						started.await();
						start.await();
					}
					catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
					todo.run();
					done.countDown();
				}
			}).start();
		}
		return done;
	}
}
