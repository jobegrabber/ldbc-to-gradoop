package de.mm.gradoop.operators;

import de.mm.gradoop.AbstractRunner;
import org.gradoop.common.model.impl.pojo.EPGMGraphHead;
import org.gradoop.common.model.impl.pojo.EPGMVertex;
import org.gradoop.common.model.impl.properties.PropertyValue;
import org.gradoop.flink.model.api.functions.TransformationFunction;
import org.gradoop.flink.model.impl.epgm.LogicalGraph;

import java.util.*;

public class AllUsedLanguagesReduction extends AbstractRunner {

	public static void main(String[] args) throws Exception {

		if (args.length != 1) {
			System.err.println("Usage: <inputPath>");
			return;
		}
		String inputPath = args[0];
		LogicalGraph inputGraph = readLogicalGraph(inputPath, "csv");
		execute(inputGraph);
	}

	// reduce all person vertices to a single value with all spoken languages
	private static LogicalGraph execute(LogicalGraph socialNetwork) throws Exception {
		List<EPGMVertex> languageToCount = socialNetwork
				.getVertices()
				.filter(vertex -> vertex.getLabel().equalsIgnoreCase("person"))
				.reduce((v1, v2) -> {
					Set<PropertyValue> languages = new HashSet<>();
					languages.addAll(v1.getPropertyValue("language").getList());
					languages.addAll(v2.getPropertyValue("language").getList());
					v1.setProperty("language", new ArrayList<>(languages));
					return v1;
				})
				.collect();
				//.forEach(vertex -> {
				//	List<PropertyValue> language = vertex.getPropertyValue("language").getList();
				//	Collections.sort(language);
				//	System.out.println(language);
				//});

		assert languageToCount.size() == 1;


		return socialNetwork.transformGraphHead((TransformationFunction<EPGMGraphHead>) (current, transformed) -> {
			current.setProperty("languages", languageToCount.get(0).getPropertyValue("languages").getMap());
			return current;
		});
	}

}
