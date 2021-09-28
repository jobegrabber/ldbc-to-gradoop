package de.mm.gradoop;

/*
 * Copyright © 2014 - 2019 Leipzig University (Database Research Group)
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

import org.apache.flink.api.common.JobExecutionResult;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.gradoop.flink.io.api.DataSink;
import org.gradoop.flink.io.api.DataSource;
import org.gradoop.flink.io.impl.csv.CSVDataSink;
import org.gradoop.flink.io.impl.csv.CSVDataSource;
import org.gradoop.flink.io.impl.csv.indexed.IndexedCSVDataSink;
import org.gradoop.flink.io.impl.csv.indexed.IndexedCSVDataSource;
import org.gradoop.flink.model.impl.epgm.LogicalGraph;
import org.gradoop.flink.util.GradoopFlinkConfig;

import java.io.File;
import java.io.IOException;

/**
 * Base class for benchmarks.
 */
public abstract class AbstractRunner {

    /**
     * Graph format used as default
     */
    protected static final String DEFAULT_FORMAT = "csv";

    /**
     * Flink execution environment.
     */
    private static ExecutionEnvironment ENV;

    /**
     * Reads an EPGM database from a given directory  using a {@link CSVDataSource}.
     *
     * @param directory path to EPGM database
     * @return EPGM logical graph
     * @throws IOException on failure
     */
    protected static LogicalGraph readLogicalGraph(String directory) throws IOException {
        return readLogicalGraph(directory, DEFAULT_FORMAT);
    }

    /**
     * Reads an EPGM database from a given directory.
     *
     * @param directory path to EPGM database
     * @param format    format in which the graph is stored (csv, indexed)
     * @return EPGM logical graph
     * @throws IOException on failure
     */
    protected static LogicalGraph readLogicalGraph(String directory, String format)
            throws IOException {
        return getDataSource(directory, format).getLogicalGraph();
    }

    /**
     * Writes a logical graph into the specified directory using a {@link CSVDataSink}.
     *
     * @param graph     logical graph
     * @param directory output path
     * @throws Exception on failure
     */
    protected static void writeLogicalGraph(LogicalGraph graph, String directory) throws Exception {
        writeLogicalGraph(graph, directory, DEFAULT_FORMAT);
    }

    /**
     * Writes a logical graph into a given directory.
     *
     * @param graph     logical graph
     * @param directory output path
     * @param format    output format (csv, indexed)
     * @throws Exception on failure
     */
    protected static void writeLogicalGraph(LogicalGraph graph, String directory, String format)
            throws Exception {
        graph.writeTo(getDataSink(directory, format, graph.getConfig()), true);
        JobExecutionResult result = getExecutionEnvironment().execute();
    }

    /**
     * Returns a Flink execution environment.
     *
     * @return Flink execution environment
     */
    protected static ExecutionEnvironment getExecutionEnvironment() {
        if (ENV == null) {
            ENV = ExecutionEnvironment.getExecutionEnvironment();
        }
        return ENV;
    }

    /**
     * Appends a file separator to the given directory (if not already existing).
     *
     * @param directory directory
     * @return directory with OS specific file separator
     */
    protected static String appendSeparator(final String directory) {
        return directory.endsWith(File.separator) ? directory : directory + File.separator;
    }

    /**
     * Converts the given DOT file into a PNG image. Note that this method requires the "dot" command
     * to be available locally.
     *
     * @param dotFile path to DOT file
     * @param pngFile path to PNG file
     * @throws IOException on failure
     */
    protected static void convertDotToPNG(String dotFile, String pngFile) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("dot", "-Tpng", dotFile);
        File output = new File(pngFile);
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(output));
        pb.start();
    }

    /**
     * Returns an EPGM DataSource for a given directory and format.
     *
     * @param directory input path
     * @param format    format in which the data is stored (csv, indexed)
     * @return DataSource for EPGM Data
     */
    private static DataSource getDataSource(String directory, String format) {
        directory = appendSeparator(directory);
        GradoopFlinkConfig config = GradoopFlinkConfig.createConfig(getExecutionEnvironment());
        format = format.toLowerCase();

        switch (format) {
            case "csv":
                return new CSVDataSource(directory, config);
            case "indexed":
                return new IndexedCSVDataSource(directory, config);
            default:
                throw new IllegalArgumentException("Unsupported format: " + format);
        }
    }

    /**
     * Returns an EPGM DataSink for a given directory and format.
     *
     * @param directory output path
     * @param format    output format (csv, indexed)
     * @param config    gradoop config
     * @return DataSink for EPGM Data
     */
    private static DataSink getDataSink(String directory, String format, GradoopFlinkConfig config) {
        directory = appendSeparator(directory);
        format = format.toLowerCase();

        switch (format) {
            case "csv":
                return new CSVDataSink(directory, config);
            case "indexed":
                return new IndexedCSVDataSink(directory, config);
            default:
                throw new IllegalArgumentException("Unsupported format: " + format);
        }
    }
}