package de.mm.gradoop.operators;

import de.mm.gradoop.AbstractRunner;
import org.gradoop.flink.model.impl.epgm.LogicalGraph;
import org.gradoop.flink.model.impl.functions.epgm.ByLabel;
import org.gradoop.flink.model.impl.operators.aggregation.functions.count.Count;
import org.gradoop.flink.model.impl.operators.grouping.Grouping;
import org.gradoop.flink.model.impl.operators.grouping.GroupingStrategy;

import java.time.LocalDateTime;
import java.util.Arrays;

import static java.util.Collections.singletonList;

public class GroupingExp extends AbstractRunner {

	public static void main(String[] args) throws Exception {

		if (args.length != 2) {
			System.err.println("Usage: <inputPath> <outputPath>");
			return;
		}

		String inputPath = args[0];
		String outputPath = args[1];

		LogicalGraph inputGraph = readLogicalGraph(inputPath, "csv");

		LogicalGraph outputGraph = execute(inputGraph);
		outputGraph.print();

		writeLogicalGraph(outputGraph, outputPath, "csv");
	}

	// calculate decade und group people by that
	private static LogicalGraph execute(LogicalGraph socialNetwork) {
		LogicalGraph preprocessedGraph = socialNetwork
				.vertexInducedSubgraph(new ByLabel<>("person"))
				.transformVertices((currentVertex, transformedVertex) -> {
					int birthday = currentVertex.getPropertyValue("birthday").getDate().getYear();
					int age = LocalDateTime.now().getYear() - birthday;

					currentVertex.setProperty("age_rounded", age - (age % 10));
					currentVertex.setProperty("decade", birthday - (birthday % 10));

					return currentVertex;
				});

		return preprocessedGraph
				.groupBy(
						Arrays.asList(Grouping.LABEL_SYMBOL, "age_rounded", Grouping.LABEL_SYMBOL, "decade"),
						singletonList(new Count("count")),
						singletonList(Grouping.LABEL_SYMBOL),
						singletonList(new Count("count")),
						GroupingStrategy.GROUP_REDUCE);
	}
}
