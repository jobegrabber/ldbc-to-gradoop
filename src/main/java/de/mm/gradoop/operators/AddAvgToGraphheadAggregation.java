package de.mm.gradoop.operators;

import de.mm.gradoop.AbstractRunner;
import org.gradoop.flink.model.impl.epgm.LogicalGraph;
import org.gradoop.flink.model.impl.operators.aggregation.functions.average.AverageVertexProperty;

public class AddAvgToGraphheadAggregation extends AbstractRunner {

	public static void main(String[] args) throws Exception {

		if (args.length != 2) {
			System.err.println("Usage: <inputPath> <outputPath>");
			return;
		}

		String inputPath = args[0];
		String outputPath = args[1];

		LogicalGraph inputGraph = readLogicalGraph(inputPath, "csv");

		LogicalGraph outputGraph = execute(inputGraph);

		writeLogicalGraph(outputGraph, outputPath, "csv");
	}

	private static LogicalGraph execute(LogicalGraph socialNetwork) {
		return socialNetwork
				.subgraph(v -> v.getLabel().equalsIgnoreCase("person"), e -> true)
				.aggregate(new AverageVertexProperty("age"))
				.transformGraphHead((currentGraphHead, transformedGraphHead) -> {
					double avgAge = currentGraphHead.getPropertyValue("avg_age").getDouble();
					currentGraphHead.setProperty("meanAge", avgAge);
					currentGraphHead.removeProperty("avg_age");
					return currentGraphHead;
				})
				.verify();

	}

}
