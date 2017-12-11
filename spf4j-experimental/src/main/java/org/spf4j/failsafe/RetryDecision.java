/*
 * Copyright (c) 2001-2017, Zoltan Farkas All Rights Reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * Additionally licensed with:
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.spf4j.failsafe;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A class that describes a "decision" that is returned by the RetryPredicate.
 * @author Zoltan Farkas
 */
public class RetryDecision<C extends Callable> {

  private static final RetryDecision<?> ABORT = new RetryDecision(Type.Abort, -1, TimeUnit.NANOSECONDS, null, null);

  public enum Type {
    Abort, Retry
  }

  private final Type decisionType;

  private final long delayNanos;

  private final Exception exception;

  private final C newCallable;

  private RetryDecision(final Type decisionType, final long delay,
          final TimeUnit timeUnit,
          @Nullable final Exception exception,
          final C newCallable) {
    if (decisionType == Type.Abort && delay > 0) {
      throw new IllegalArgumentException("Cannot add a delay to Abort " + delay);
    }
    this.decisionType = decisionType;
    this.delayNanos = timeUnit.toNanos(delay);
    this.exception = exception;
    this.newCallable = newCallable;
  }

  /**
   * Create a Abort decision with a custom Exception.
   * @param exception the custom exception.
   * @return a Abort decision with a custom Exception.
   */
  public static RetryDecision abort(@Nonnull final Exception exception) {
    return new RetryDecision(Type.Abort, -1, TimeUnit.NANOSECONDS, exception, null);
  }

  public static <C extends Callable> RetryDecision<C> retry(final long retryNanos, @Nonnull final C callable) {
    return new RetryDecision(Type.Retry, retryNanos, TimeUnit.NANOSECONDS,  null, callable);
  }

  /**
   * @return Create Abort retry decision. The last successful result or exception is returned.
   */
  public static RetryDecision abort() {
    return ABORT;
  }

  public final Type getDecisionType() {
    return decisionType;
  }

  /**
   * @return The delay in nanoseconds
   */
  public final long getDelayNanos() {
    return delayNanos;
  }

  public final Exception getException() {
    return exception;
  }

  public final RetryDecision<C> withDelayNanos(final int delayNanos) {
    return new RetryDecision<>(decisionType, delayNanos, TimeUnit.NANOSECONDS, exception, newCallable);
  }

  @Nonnull
  public C getNewCallable() {
    return newCallable;
  }

}
