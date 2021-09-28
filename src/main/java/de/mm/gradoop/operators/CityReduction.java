package de.mm.gradoop.operators;

import de.mm.gradoop.AbstractRunner;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.api.java.utils.ParameterTool;
import org.gradoop.common.model.impl.properties.PropertyValue;
import org.gradoop.flink.model.impl.epgm.LogicalGraph;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class CityReduction extends AbstractRunner {

	public static void main(String[] args) throws Exception {

		ParameterTool parameterTool = ParameterTool.fromArgs(args);
		ExecutionEnvironment.getExecutionEnvironment().getConfig().setGlobalJobParameters(parameterTool);

		String inputPath = parameterTool.get("in");

		LogicalGraph inputGraph = readLogicalGraph(inputPath, "csv");
		execute(inputGraph);
	}

	private static void execute(LogicalGraph socialNetwork) throws Exception {
		socialNetwork
				.getVertices()
				.filter(vertex -> vertex.getLabel().equalsIgnoreCase("city"))
				.reduce((v1, v2) -> {
					Set<PropertyValue> cityNames = Optional.ofNullable(v1.getPropertyValue("cityList"))
							.map(PropertyValue::getSet)
							.orElse(new HashSet<>());

					cityNames.add(v1.getPropertyValue("name"));
					cityNames.add(v2.getPropertyValue("name"));

					v1.setProperty("cityList", cityNames);
					return v1;
				})
				.collect()
				.forEach(vertex -> {
					Set<PropertyValue> cityList = vertex.getPropertyValue("cityList").getSet();
					cityList.forEach(propertyValue -> {
						System.out.print("\"" + propertyValue.getString() + "\",");
					});
				});
	}

}
