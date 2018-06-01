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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.concurrent.Callable;
import java.util.concurrent.RejectedExecutionException;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spf4j.test.log.Level;
import org.spf4j.test.log.LogAssert;
import org.spf4j.test.log.LogMatchers;
import org.spf4j.test.log.TestLoggers;

/**
 * @author Zoltan Farkas
 */
public class RateLimiterTest {

  private static final Logger LOG = LoggerFactory.getLogger(RateLimiterTest.class);

  @Test
  public void testRateLimit() throws Exception {
    LogAssert expect = TestLoggers.sys().expect(RateLimiterTest.class.getName(), Level.DEBUG,
            LogMatchers.hasFormat("executed nr {}"));
    try (RateLimiter<?, Callable<?>> limiter = new RateLimiter<>(10, 10)) {
      Assert.assertEquals(1d, limiter.getPermitsPerReplenishInterval(), 0.001);
      Assert.assertEquals(100, limiter.getPermitReplenishIntervalMillis(), 0.001);
      for (int i = 0; i < 10; i++) {
        final int val = i;
        limiter.execute(() -> {
          LOG.debug("executed nr {}", val);
          return null;
        });
      }
      Assert.fail();
    } catch (RejectedExecutionException ex) {
      expect.assertObservation();
    }
  }

  @Test
  public void testRateLimit2() throws Exception {
    LogAssert expect = TestLoggers.sys().expect(RateLimiterTest.class.getName(), Level.DEBUG, 10,
            LogMatchers.hasFormat("executed nr {}"));
    try (RateLimiter<?, Callable<?>> limiter = new RateLimiter<>(10, 10, new RateLimiter.RejectedExecutionHandler() {
      @Override
      @SuppressFBWarnings("MDM_THREAD_YIELD")
      public Object reject(final RateLimiter limiter, final Callable callable,
              final long msAfterWhichResourceAvailable)
              throws Exception {
        Thread.sleep(msAfterWhichResourceAvailable);
        return limiter.execute(callable);
      }
    }
    )) {
      for (int i = 0; i < 10; i++) {
        final int val = i;
        limiter.execute(() -> {
          LOG.debug("executed nr {}", val);
          return null;
        });
      }
    }
    expect.assertObservation();
  }

  @Test(expected = IllegalArgumentException.class)
  public void testRateLimitInvalid() {
    new RateLimiter(1000, 9);
  }


}
