package de.mm.gradoop.operators;

import de.mm.gradoop.AbstractRunner;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.api.java.utils.ParameterTool;
import org.gradoop.flink.model.impl.epgm.LogicalGraph;
import org.gradoop.flink.model.impl.operators.sampling.RandomVertexNeighborhoodSampling;
import org.gradoop.flink.model.impl.operators.sampling.functions.Neighborhood;

public class SubgraphComparison extends AbstractRunner {

    public static void main(String[] args) throws Exception {
        ParameterTool parameterTool = ParameterTool.fromArgs(args);
        ExecutionEnvironment.getExecutionEnvironment().getConfig().setGlobalJobParameters(parameterTool);

        String inputPath = parameterTool.get("in");

        LogicalGraph inputGraph = readLogicalGraph(inputPath, "csv");
        execute(inputGraph);
    }

    private static void execute(LogicalGraph socialNetwork) throws Exception {
        // 2199023266220|Chris|Jasani|female|1981-05-22|2010-03-29T22:50:40.375+0000|41.89.103.231|Firefox
        LogicalGraph personKnows = socialNetwork
                .subgraph(
                        vertex -> vertex.getLabel().equalsIgnoreCase("person"),
                        edge -> edge.getLabel().equalsIgnoreCase("knows"));

        LogicalGraph sub1 = personKnows
                .sample(new RandomVertexNeighborhoodSampling(0.001f, Neighborhood.IN));

        LogicalGraph sub2 = personKnows
                .sample(new RandomVertexNeighborhoodSampling(0.001f, Neighborhood.IN));

        sub1.equalsByElementIds(sub2)
                .collect()
                .forEach(aBoolean ->
                        System.out.println("equalsByElementIds: " + aBoolean)
                );

        System.out.println("Sub1 #Persons=" + sub1.getVertices().count());
        System.out.println("Sub2 #Persons=" + sub2.getVertices().count());
    }

}