package de.mm.gradoop.operators;

import de.mm.gradoop.AbstractRunner;
import org.apache.commons.math3.util.Pair;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.GroupCombineFunction;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.api.java.tuple.Tuple1;
import org.apache.flink.api.java.tuple.Tuple2;
import org.gradoop.common.model.impl.pojo.EPGMGraphHead;
import org.gradoop.common.model.impl.pojo.EPGMVertex;
import org.gradoop.common.model.impl.properties.PropertyValue;
import org.gradoop.flink.model.api.functions.TransformationFunction;
import org.gradoop.flink.model.impl.epgm.LogicalGraph;

import java.util.Iterator;
import java.util.Map;
import java.util.stream.Collectors;

public class MostUsedLanguagesByCountCombineReduction extends AbstractRunner {

    public static void main(String[] args) throws Exception {

        if (args.length != 2) {
            System.err.println("Usage: <inputPath> <outputPath>");
            return;
        }
        String inputPath = args[0];
        LogicalGraph inputGraph = readLogicalGraph(inputPath, "csv");

        writeLogicalGraph(execute(inputGraph), args[1]);
    }

    // map all languages from all person vertices values to pairs of (language / 1) and reduce them to a single value
    private static LogicalGraph execute(LogicalGraph socialNetwork) throws Exception {
        Map<String, Long> languageToCount = socialNetwork.getVertices()
                .flatMap((FlatMapFunction<EPGMVertex, Tuple1<String>>) (vertex, out) -> {
                    if (vertex.getLabel().equalsIgnoreCase("person")) {
                        for (PropertyValue language : vertex.getPropertyValue("language").getList()) {
                            out.collect(new Tuple1<>(language.getString()));
                        }
                    }
                })
                .groupBy(tuple -> tuple.f0)
                .combineGroup((GroupCombineFunction<Tuple1<String>, Tuple2<String, Long>>) (values, out) -> {
                    Iterator<Tuple1<String>> iter = values.iterator();
                    if (iter.hasNext()) {
                        String language = iter.next().f0;
                        long count = 1L;
                        while (iter.hasNext()) {
                            count++;
                            iter.next();
                        }
                        out.collect(new Tuple2<>(language, count));
                    }
                })
                .groupBy(tuple -> tuple.f0)
                .reduce((ReduceFunction<Tuple2<String, Long>>) (v1, v2) -> new Tuple2<>(v1.f0, v1.f1 + v2.f1))
                .collect()
                .stream()
                .map(tuple -> Pair.create(tuple.f0, tuple.f1))
                .collect(Collectors.toMap(Pair::getFirst, Pair::getSecond));

        return socialNetwork.transformGraphHead((TransformationFunction<EPGMGraphHead>) (current, transformed) -> {
            current.setProperty("languages", languageToCount);
            return current;
        });
    }

}
