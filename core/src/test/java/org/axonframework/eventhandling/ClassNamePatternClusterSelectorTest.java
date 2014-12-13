/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.eventhandling;

import org.axonframework.domain.EventMessage;
import org.junit.*;

import java.util.regex.Pattern;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class ClassNamePatternClusterSelectorTest {

    @Test
    public void testMatchesPattern() throws Exception {
        Cluster cluster = mock(Cluster.class);
        Pattern pattern = Pattern.compile("org\\.axonframework.*\\$MyEventListener");
        ClassNamePatternClusterSelector testSubject = new ClassNamePatternClusterSelector(pattern, cluster);

        Cluster actual = testSubject.selectCluster(new MyEventListener());
        assertSame(cluster, actual);
    }

    private static class MyEventListener implements EventListener {

        @Override
        public void handle(EventMessage event) {
        }
    }
}
