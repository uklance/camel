/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.processor.aggregate.tarfile;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.util.IOHelper;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AggregationStrategyWithFilenameHeaderTest extends CamelTestSupport {

    private static final List<String> FILE_NAMES = Arrays.asList("foo", "bar");

    private TarAggregationStrategy tar = new TarAggregationStrategy(false, true);

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        tar.setParentDir("target/temp");
        deleteDirectory("target/temp");
        deleteDirectory("target/out");
        super.setUp();
    }

    @Test
    public void testSplitter() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:aggregateToTarEntry");
        mock.expectedMessageCount(1);

        template.setDefaultEndpointUri("direct:start");
        template.sendBodyAndHeader("foo", Exchange.FILE_NAME, FILE_NAMES.get(0));
        template.sendBodyAndHeader("bar", Exchange.FILE_NAME, FILE_NAMES.get(1));
        assertMockEndpointsSatisfied();

        Thread.sleep(500);

        File[] files = new File("target/out").listFiles();
        assertNotNull(files);
        assertTrue(files.length > 0, "Should be a file in target/out directory");

        File resultFile = files[0];

        final TarArchiveInputStream tis
                = (TarArchiveInputStream) new ArchiveStreamFactory().createArchiveInputStream(ArchiveStreamFactory.TAR,
                        new BufferedInputStream(new FileInputStream(resultFile)));
        try {
            int fileCount = 0;
            for (TarArchiveEntry entry = tis.getNextTarEntry(); entry != null; entry = tis.getNextTarEntry()) {
                fileCount++;
                assertTrue(FILE_NAMES.contains(entry.getName()), "Tar entry file name should be on of: " + FILE_NAMES);
            }
            assertEquals(FILE_NAMES.size(), fileCount, "Tar file should contain " + FILE_NAMES.size() + " files");
        } finally {
            IOHelper.close(tis);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .aggregate(tar)
                        .constant(true)
                        .completionTimeout(50)
                        .to("file:target/out")
                        .to("mock:aggregateToTarEntry")
                        .log("Done processing tar file: ${header.CamelFileName}");
            }
        };

    }
}
