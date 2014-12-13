/*
 * Copyright (c) 2010-2014. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.commandhandling.gateway;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.common.lock.DeadlockException;
import org.axonframework.unitofwork.CurrentUnitOfWork;

import java.util.ArrayList;
import java.util.List;

/**
 * Callback implementation that will invoke a retry scheduler if a command results in a runtime exception.
 * <p/>
 * Generally, it is not necessary to use this class directly. It is used by CommandGateway implementations to support
 * retrying of commands.
 *
 * @param <R> The type of return value expected by the callback
 * @author Allard Buijze
 * @see DefaultCommandGateway
 * @since 2.0
 */
public class RetryingCallback<R> implements CommandCallback<R> {

    private final CommandCallback<R> delegate;
    private final CommandMessage commandMessage;
    private final RetryScheduler retryScheduler;
    private final CommandBus commandBus;
    private final List<Class<? extends Throwable>[]> history;
    private final Runnable dispatchCommand;

    /**
     * Initialize the RetryingCallback with the given <code>delegate</code>, representing the actual callback passed as
     * a parameter to dispatch, the given <code>commandMessage</code>, <code>retryScheduler</code> and
     * <code>commandBus</code>.
     *
     * @param delegate       The callback to invoke when the command succeeds, or when retries are rejected.
     * @param commandMessage The message being dispatched
     * @param retryScheduler The scheduler that decides if and when a retry should be scheduled
     * @param commandBus     The commandBus on which the command must be dispatched
     */
    public RetryingCallback(CommandCallback<R> delegate, CommandMessage commandMessage, RetryScheduler retryScheduler,
                            CommandBus commandBus) {
        this.delegate = delegate;
        this.commandMessage = commandMessage;
        this.retryScheduler = retryScheduler;
        this.commandBus = commandBus;
        this.history = new ArrayList<>();
        this.dispatchCommand = new RetryDispatch();
    }

    @Override
    public void onSuccess(R result) {
        delegate.onSuccess(result);
    }

    @Override
    public void onFailure(Throwable cause) {
        history.add(simplify(cause));
        try {
            // we fail immediately when the exception is checked,
            // or when it is a Deadlock Exception and we have an active unit of work
            if (!(cause instanceof RuntimeException)
                    || (isCausedBy(cause, DeadlockException.class) && CurrentUnitOfWork.isStarted())
                    || !retryScheduler.scheduleRetry(commandMessage, (RuntimeException) cause,
                                                     new ArrayList<>(history),
                                                     dispatchCommand)) {
                delegate.onFailure(cause);
            }
        } catch (Exception e) {
            delegate.onFailure(e);
        }
    }

    private boolean isCausedBy(Throwable exception, Class<? extends Throwable> causeType) {
        return causeType.isInstance(exception)
                || (exception.getCause() != null && isCausedBy(exception.getCause(), causeType));
    }

    @SuppressWarnings("unchecked")
    private Class<? extends Throwable>[] simplify(Throwable cause) {
        List<Class<? extends Throwable>> types = new ArrayList<>();
        types.add(cause.getClass());
        Throwable rootCause = cause;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
            types.add(rootCause.getClass());
        }
        return types.toArray(new Class[types.size()]);
    }

    private class RetryDispatch implements Runnable {

        @Override
        public void run() {
            try {
                commandBus.dispatch(commandMessage, RetryingCallback.this);
            } catch (Exception e) {
                RetryingCallback.this.onFailure(e);
            }
        }
    }
}
