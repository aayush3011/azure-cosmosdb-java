// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.
package com.azure.data.cosmos.internal.changefeed.implementation;

import com.azure.data.cosmos.internal.changefeed.CancellationToken;
import com.azure.data.cosmos.internal.changefeed.CancellationTokenSource;
import com.azure.data.cosmos.internal.changefeed.ChangeFeedObserver;
import com.azure.data.cosmos.internal.changefeed.ChangeFeedObserverCloseReason;
import com.azure.data.cosmos.internal.changefeed.ChangeFeedObserverContext;
import com.azure.data.cosmos.internal.changefeed.Lease;
import com.azure.data.cosmos.internal.changefeed.LeaseRenewer;
import com.azure.data.cosmos.internal.changefeed.PartitionProcessor;
import com.azure.data.cosmos.internal.changefeed.PartitionSupervisor;
import com.azure.data.cosmos.internal.changefeed.exceptions.LeaseLostException;
import com.azure.data.cosmos.internal.changefeed.exceptions.ObserverException;
import com.azure.data.cosmos.internal.changefeed.exceptions.PartitionSplitException;
import com.azure.data.cosmos.internal.changefeed.exceptions.TaskCancelledException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;

/**
 * Implementation for {@link PartitionSupervisor}.
 */
class PartitionSupervisorImpl implements PartitionSupervisor, Closeable {
    private final Lease lease;
    private final ChangeFeedObserver observer;
    private final PartitionProcessor processor;
    private final LeaseRenewer renewer;
    private CancellationTokenSource renewerCancellation;
    private CancellationTokenSource processorCancellation;

    private volatile RuntimeException resultException;

    private Scheduler scheduler;

    public PartitionSupervisorImpl(Lease lease, ChangeFeedObserver observer, PartitionProcessor processor, LeaseRenewer renewer, Scheduler scheduler) {
        this.lease = lease;
        this.observer = observer;
        this.processor = processor;
        this.renewer = renewer;
        this.scheduler = scheduler;

        if (scheduler == null) {
            this.scheduler = Schedulers.elastic();
        }
    }

    @Override
    public Mono<Void> run(CancellationToken shutdownToken) {
        this.resultException = null;

        ChangeFeedObserverContext context = new ChangeFeedObserverContextImpl(this.lease.getLeaseToken());

        this.observer.open(context);

        this.processorCancellation = new CancellationTokenSource();

        this.scheduler.schedule(() -> this.processor.run(this.processorCancellation.getToken())
            .subscribe());

        this.renewerCancellation = new CancellationTokenSource();

        this.scheduler.schedule(() -> this.renewer.run(this.renewerCancellation.getToken())
            .subscribe());

        return Mono.just(this)
            .delayElement(Duration.ofMillis(100))
            .repeat( () -> !shutdownToken.isCancellationRequested() && this.processor.getResultException() == null && this.renewer.getResultException() == null)
            .last()
            .flatMap( value -> this.afterRun(context, shutdownToken));
    }

    private Mono<Void> afterRun(ChangeFeedObserverContext context, CancellationToken shutdownToken) {
        ChangeFeedObserverCloseReason closeReason = ChangeFeedObserverCloseReason.UNKNOWN;

        try {
//            while (!shutdownToken.isCancellationRequested() && this.processor.getResultException() == null && this.renewer.getResultException() == null) {
//                try {
//                    Thread.sleep(100);
//                } catch (InterruptedException iex) {
//                    break;
//                }
//            }

            this.processorCancellation.cancel();
            this.renewerCancellation.cancel();

            if (this.processor.getResultException() != null) {
                throw this.processor.getResultException();
            }

            if (this.renewer.getResultException() != null) {
                throw this.renewer.getResultException();
            }

            closeReason = shutdownToken.isCancellationRequested() ?
                ChangeFeedObserverCloseReason.SHUTDOWN :
                ChangeFeedObserverCloseReason.UNKNOWN;

        } catch (LeaseLostException llex) {
            closeReason = ChangeFeedObserverCloseReason.LEASE_LOST;
            this.resultException = llex;
        } catch (PartitionSplitException pex) {
            closeReason = ChangeFeedObserverCloseReason.LEASE_GONE;
            this.resultException = pex;
        } catch (TaskCancelledException tcex) {
            closeReason = ChangeFeedObserverCloseReason.SHUTDOWN;
            this.resultException = null;
        } catch (ObserverException oex) {
            closeReason = ChangeFeedObserverCloseReason.OBSERVER_ERROR;
            this.resultException = oex;
        } catch (Exception ex) {
            closeReason = ChangeFeedObserverCloseReason.UNKNOWN;
        } finally {
            this.observer.close(context, closeReason);
        }

        if (this.resultException != null) {
            return Mono.error(this.resultException);
        } else {
            return Mono.empty();
        }
    }

    @Override
    public RuntimeException getResultException() {
        return this.resultException;
    }

    @Override
    public void close() throws IOException {
        if (this.processorCancellation != null) {
            this.processorCancellation.close();
        }

        this.renewerCancellation.close();
    }
}
