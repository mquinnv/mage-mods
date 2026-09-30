package com.mage.cubewheel.sva;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/** {@link SvaService.Http} over java.net.http: async, 10 s timeouts, "CubeWheel/<version>" user agent. */
public final class SvaHttp implements SvaService.Http {
	private static final Duration TIMEOUT = Duration.ofSeconds(10);

	private final HttpClient client = HttpClient.newBuilder()
			.connectTimeout(TIMEOUT)
			.followRedirects(HttpClient.Redirect.NORMAL)
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
			return client.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
					.thenApply(r -> new SvaService.Response(r.statusCode(), r.body()));
		} catch (RuntimeException e) {
			return CompletableFuture.failedFuture(e);
		}
	}
}
