package net.mage.cubewheel.sva;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;

class SvaHttpTest {
	static final class Sub implements Flow.Subscription {
		boolean cancelled;

		@Override public void request(long n) {}

		@Override public void cancel() {
			cancelled = true;
		}
	}

	private static ByteBuffer bytes(String s) {
		return ByteBuffer.wrap(s.getBytes(StandardCharsets.UTF_8));
	}

	@Test void collectsBodiesUnderTheCap() throws Exception {
		SvaHttp.CappedString c = new SvaHttp.CappedString(16);
		c.onSubscribe(new Sub());
		c.onNext(List.of(bytes("[\"✦\","), bytes("1]")));
		c.onComplete();
		assertEquals("[\"✦\",1]", c.getBody().toCompletableFuture().get());
	}

	@Test void failsAndCancelsPastTheCap() {
		SvaHttp.CappedString c = new SvaHttp.CappedString(8);
		Sub sub = new Sub();
		c.onSubscribe(sub);
		c.onNext(List.of(bytes("12345")));
		c.onNext(List.of(bytes("6789")));
		c.onNext(List.of(bytes("more")));
		c.onComplete();
		assertTrue(sub.cancelled);
		ExecutionException e = assertThrows(ExecutionException.class, () -> c.getBody().toCompletableFuture().get());
		assertTrue(e.getCause() instanceof java.io.IOException);
	}
}
