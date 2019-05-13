// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.cosmosdb.rx.internal.http.policy;

import com.microsoft.azure.cosmosdb.rx.internal.http.HttpPipelineCallContext;
import com.microsoft.azure.cosmosdb.rx.internal.http.HttpPipelineNextPolicy;
import com.microsoft.azure.cosmosdb.rx.internal.http.HttpResponse;
import com.microsoft.azure.cosmosdb.rx.internal.http.UrlBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.net.MalformedURLException;

/**
 * The Pipeline policy that adds the given host to each HttpRequest.
 */
public class HostPolicy implements HttpPipelinePolicy {
    private final String host;
    private static final Logger LOGGER = LoggerFactory.getLogger(HostPolicy.class);

    /**
     * Create HostPolicy.
     *
     * @param host The host to set on every HttpRequest.
     */
    public HostPolicy(String host) {
        this.host = host;
    }

    @Override
    public Mono<HttpResponse> process(HttpPipelineCallContext context, HttpPipelineNextPolicy next) {
        LOGGER.info("Setting host to {0}", host);

        Mono<HttpResponse> result;
        final UrlBuilder urlBuilder = UrlBuilder.parse(context.httpRequest().url());
        try {
            context.httpRequest().withUrl(urlBuilder.withHost(host).toURL());
            result = next.process();
        } catch (MalformedURLException e) {
            result = Mono.error(e);
        }
        return result;
    }
}