/*
 * Copyright 2018 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.blockGraphs;


import org.terasology.blockGraphs.graphDefinitions.BlockGraph;
import org.terasology.blockGraphs.graphDefinitions.GraphType;
import org.terasology.blockGraphs.graphDefinitions.GraphUri;
import org.terasology.blockGraphs.graphDefinitions.nodeDefinitions.NodeDefinition;
import org.terasology.blockGraphs.graphDefinitions.nodes.GraphNode;
import org.terasology.blockGraphs.graphDefinitions.nodes.JunctionNode;
import org.terasology.engine.SimpleUri;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterMode;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.registry.Share;
import org.terasology.world.block.BlockUri;

import java.util.HashMap;
import java.util.Map;

@Share(BlockGraphManager.class)
@RegisterSystem(RegisterMode.AUTHORITY)
public class BlockGraphManager extends BaseComponentSystem {
    private long nextGraphId = 1L;

    private Map<SimpleUri, GraphType> graphTypes = new HashMap<>();
    private Map<GraphUri, BlockGraph> graphRegistry = new HashMap<>();
    private Map<BlockUri, SimpleUri> blockToGraph = new HashMap<>();

    /**
     * Gets the graph type associated with the specific URI
     *
     * @param typeUri The URI for that graph type.
     * @return The graph type, if it exists
     */
    public GraphType getGraphType(SimpleUri typeUri) {
        return graphTypes.getOrDefault(typeUri, null);
    }

    public GraphType getGraphType(BlockUri blockInGraph) {
        return getGraphType(blockToGraph.getOrDefault(blockInGraph, new SimpleUri()));
    }

    /**
     * Adds a new graph type to the manager
     *
     * @param graphType The graph type to add
     */
    public void addGraphType(GraphType graphType) {
        graphTypes.put(graphType.getUri(), graphType);
        graphType.getAllBlocks().forEach(blockUri -> blockToGraph.put(blockUri, graphType.getUri()));
    }

    /**
     * Create and store a new graph instance using the specified type.
     *
     * @param graphType The graph type to use
     * @return The newly created graph
     */
    public BlockGraph newGraphInstance(GraphType graphType) {
        GraphUri uri = new GraphUri(graphType.getUri(), nextGraphId);
        nextGraphId++;
        BlockGraph newGraph = new BlockGraph(graphType, uri);
        graphRegistry.put(uri, newGraph);

        return newGraph;
    }

    /**
     * Get the graph instance for a given {@link GraphUri}
     * Will error if given a URI that there is no graph for
     *
     * @param graphUri The URI of the graph to retrieve
     * @return The Block Graph instance
     */
    public BlockGraph getGraphInstance(GraphUri graphUri) {
        return graphRegistry.get(graphUri);
    }

    /**
     * Helper Method to get a specific node on a specific graph
     *
     * @param graphUri The graph to get the node on
     * @param nodeId   The id of the node to get
     * @return The node specified if it could be found
     */
    public GraphNode getGraphNode(GraphUri graphUri, int nodeId) {
        return getGraphInstance(graphUri).getNode(nodeId);
    }

    /**
     * Helper method to get the node definition for a specified node type
     *
     * @param graph  The graph the node belongs to
     * @param nodeId The id of the node in question
     * @return The node definition linked to that node
     */
    public NodeDefinition getNodeDefinition(GraphUri graph, int nodeId) {
        BlockGraph graphInstance = getGraphInstance(graph);
        return graphInstance.getGraphType().getDefinition(graphInstance.getNode(nodeId));
    }
}
