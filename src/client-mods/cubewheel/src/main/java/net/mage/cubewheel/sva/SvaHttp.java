package net.mage.cubewheel.sva;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/**
 * {@link SvaService.Http} over java.net.http: async, 10 s timeouts, "CubeWheel/<version>" user agent, no
 * redirects (a 3xx is just a failed request) and bodies capped at {@link #MAX_BODY_BYTES}.
 */
public final class SvaHttp implements SvaService.Http {
	private static final Duration TIMEOUT = Duration.ofSeconds(10);
	/** The full Survival catalog is ~0.7 MB; anything past this is not an API reply we want in memory. */
	public static final int MAX_BODY_BYTES = 4 * 1024 * 1024;

	private final HttpClient client = HttpClient.newBuilder()
			.connectTimeout(TIMEOUT)
			.followRedirects(HttpClient.Redirect.NEVER)
			.build();
	private final String userAgent;

	public SvaHttp(String version) {
		this.userAgent = "CubeWheel/" + (version == null || version.isBlank() ? "dev" : version);
	}

	@Override
	public CompletableFuture<SvaService.Response> get(String url) {
		try {
			HttpRequest req = HttpRequest.newBuilder(URI.create(url))
					.timeout(TIMEOUT)
					.header("User-Agent", userAgent)
					.header("Accept", "application/json")
					.GET()
					.build();
			return client.sendAsync(req, info -> new CappedString(MAX_BODY_BYTES))
					.thenApply(r -> new SvaService.Response(r.statusCode(), r.body()));
		} catch (RuntimeException e) {
			return CompletableFuture.failedFuture(e);
		}
	}

	/** Collects a UTF-8 body, failing (and cancelling the download) once it passes {@code cap} bytes. */
	static final class CappedString implements HttpResponse.BodySubscriber<String> {
		private final int cap;
		private final CompletableFuture<String> result = new CompletableFuture<>();
		private final ByteArrayOutputStream buf = new ByteArrayOutputStream();
		private Flow.Subscription subscription;

		CappedString(int cap) {
			this.cap = cap;
		}

		@Override
		public CompletionStage<String> getBody() {
			return result;
		}

		@Override
		public void onSubscribe(Flow.Subscription s) {
			subscription = s;
			s.request(Long.MAX_VALUE);
		}

		@Override
		public void onNext(List<ByteBuffer> items) {
			if (result.isDone()) return;
			for (ByteBuffer b : items) {
				int n = b.remaining();
				if ((long) buf.size() + n > cap) {
					if (subscription != null) subscription.cancel();
					result.completeExceptionally(new IOException("response larger than " + (cap / (1024 * 1024)) + " MB"));
					return;
				}
				byte[] bytes = new byte[n];
				b.get(bytes);
				buf.write(bytes, 0, n);
			}
		}

		@Override
		public void onError(Throwable t) {
			result.completeExceptionally(t);
		}

		@Override
		public void onComplete() {
			result.complete(buf.toString(StandardCharsets.UTF_8));
		}
	}
}
