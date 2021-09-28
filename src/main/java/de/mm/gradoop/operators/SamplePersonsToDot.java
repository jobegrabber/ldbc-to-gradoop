package de.mm.gradoop.operators;

import de.mm.gradoop.AbstractRunner;
import org.gradoop.flink.io.impl.dot.DOTDataSink;
import org.gradoop.flink.model.impl.epgm.LogicalGraph;
import org.gradoop.flink.model.impl.functions.epgm.ByLabel;

import java.time.LocalDateTime;

public class SamplePersonsToDot extends AbstractRunner {

	public static void main(String[] args) throws Exception {

		if (args.length != 2) {
			System.err.println("Usage: <inputPath> <outputPath>");
			return;
		}

		String inputPath = args[0];
		String outputPath = args[1];

		LogicalGraph inputGraph = readLogicalGraph(inputPath, "csv");

		LogicalGraph outputGraph = execute(inputGraph);

		DOTDataSink dotDataSink = new DOTDataSink(outputPath, true, DOTDataSink.DotFormat.HTML);
		dotDataSink.write(outputGraph, true);

		convertDotToPNG(outputPath, outputPath + ".png");

		getExecutionEnvironment().execute();
	}

	// group people by age
	private static LogicalGraph execute(LogicalGraph socialNetwork) {
		// Perform grouping on preprocessedGraph
		LogicalGraph preprocessedGraph = socialNetwork
				.vertexInducedSubgraph(new ByLabel<>("Person"))
				.transformVertices((currentVertex, transformedVertex) -> {
					currentVertex.setProperty(
							"age", LocalDateTime.now().getYear() - currentVertex.getPropertyValue("birthday").getDate()
									.getYear());
					return transformedVertex;
				});
		return preprocessedGraph;

//		return new Grouping.GroupingBuilder()
//				.setStrategy(GroupingStrategy.GROUP_REDUCE)
//				.addVertexGroupingKey("age")
//				.useVertexLabel(true)
//				.useEdgeLabel(true)
//				.addVertexAggregateFunction(new Count())
//				.addEdgeAggregateFunction(new Count())
//				.build()
//				.execute(preprocessedGraph);
	}
}
