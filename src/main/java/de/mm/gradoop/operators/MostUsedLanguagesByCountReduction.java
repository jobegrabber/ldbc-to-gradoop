package de.mm.gradoop.operators;

import de.mm.gradoop.AbstractRunner;
import org.apache.commons.math3.util.Pair;
import org.gradoop.common.model.impl.pojo.EPGMGraphHead;
import org.gradoop.common.model.impl.pojo.EPGMVertex;
import org.gradoop.common.model.impl.properties.PropertyValue;
import org.gradoop.flink.model.api.functions.TransformationFunction;
import org.gradoop.flink.model.impl.epgm.LogicalGraph;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MostUsedLanguagesByCountReduction extends AbstractRunner {

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
        List<EPGMVertex> languageToCount = socialNetwork
                .getVertices()
                .filter(vertex -> vertex.getLabel().equalsIgnoreCase("person"))
                .map(vertex -> {
                    List<PropertyValue> speaks = vertex.getPropertyValue("language").getList();
                    // gradoop cannot process pairs, so use a Map instead
                    Map<PropertyValue, PropertyValue> speakerCountByLanguage = speaks.stream()
                            .map(propertyValue -> Pair.create(propertyValue, PropertyValue.create(1)))
                            .collect(Collectors.toMap(Pair::getFirst, Pair::getSecond));
                    vertex.setProperty("languages", speakerCountByLanguage);
                    return vertex;
                })
                .reduce((v1, v2) -> {
                    Map<PropertyValue, PropertyValue> speaks = v1.getPropertyValue("languages").getMap();

                    v2.getPropertyValue("languages").getMap().forEach((key, value) ->
                            speaks.put(key, PropertyValue.create(speaks.getOrDefault(key, PropertyValue.create(0)).getInt() + value.getInt())));

                    v1.setProperty("languages", speaks);
                    return v1;
                })
                .collect();

        assert languageToCount.size() == 1;


        return socialNetwork.transformGraphHead((TransformationFunction<EPGMGraphHead>) (current, transformed) -> {
            current.setProperty("languages", languageToCount.get(0).getPropertyValue("languages").getMap());
            return current;
        });

        //.collect()
        //.forEach(vertex -> {
        //	Map<PropertyValue, PropertyValue> speaks = vertex.getPropertyValue("languages").getMap();
        //	speaks.entrySet().stream()
        //			.sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
        //			.forEach(System.out::println);
        //});
    }

}
