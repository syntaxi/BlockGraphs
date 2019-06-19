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

import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.blockGraphs.dataMovement.GraphMovementSystem;
import org.terasology.blockGraphs.dataMovement.GraphPositionComponent;
import org.terasology.blockGraphs.graphDefinitions.BlockGraph;
import org.terasology.blockGraphs.graphDefinitions.GraphType;
import org.terasology.blockGraphs.graphDefinitions.nodes.EdgeNode;
import org.terasology.blockGraphs.graphDefinitions.nodes.JunctionNode;
import org.terasology.blockGraphs.graphDefinitions.nodes.TerminusNode;
import org.terasology.engine.SimpleUri;
import org.terasology.entitySystem.entity.EntityBuilder;
import org.terasology.entitySystem.entity.EntityManager;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.entity.internal.EngineEntityManager;
import org.terasology.math.Side;
import org.terasology.moduletestingenvironment.ModuleTestingEnvironment;
import org.terasology.network.NetworkSystem;
import org.terasology.network.internal.NetworkSystemImpl;
import org.terasology.world.block.BlockUri;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.hamcrest.core.AnyOf.anyOf;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class DataMovementTest extends ModuleTestingEnvironment {
    private static final Logger logger = LoggerFactory.getLogger(DataMovementTest.class);

    private BlockGraph graph;
    private GraphMovementSystem movementSystem;
    private BlockGraphManager graphManager;

    @Override
    public Set<String> getDependencies() {
        return Sets.newHashSet("BlockGraphs");
    }

    @Before
    public void initialize() {
        movementSystem = getHostContext().get(GraphMovementSystem.class);
        graphManager = getHostContext().get(BlockGraphManager.class);

        /* Stops NPEs when adding a component to an entity (and possibly other related methods) */
        ((EngineEntityManager) getHostContext().get(EntityManager.class)).unsubscribe((NetworkSystemImpl) getHostContext().get(NetworkSystem.class));

        GraphType graphType = new GraphType(new SimpleUri("BlockGraphs:TestGraph"));
        graphType.addNodeType(new TestUpwardsDefinition());
        graphType.addNodeType(new TestRandomDefinition());

        graphManager.addGraphType(graphType);
        graph = graphManager.newGraphInstance(graphType);
    }

    /**
     * Tests that the graph is registered into the manager properly
     */
    @Test
    public void testRegistration() {
        assertNotNull(graphManager.getGraphInstance(graph.getUri()));
    }

    /**
     * Tests a simple graph case where the data moves sequentially through a series of nodes. With the last being a terminus
     * <code>1 -> 2 -> 3 -> 4</code>
     */
    @Test
    public void testSimpleGraph() {
        EntityRef testData = buildData();

        /* Build Graph */
        TerminusNode[] terminusNodes = createTerminus(2, TestUpwardsDefinition.BLOCK_URI);
        JunctionNode[] nodes = createJunctions(2, TestUpwardsDefinition.BLOCK_URI);

        terminusNodes[0].linkNode(nodes[0], Side.TOP);

        nodes[0].linkNode(terminusNodes[0], Side.BOTTOM);
        nodes[0].linkNode(nodes[1], Side.TOP);

        nodes[1].linkNode(nodes[0], Side.BOTTOM);
        nodes[1].linkNode(terminusNodes[1], Side.TOP);

        terminusNodes[1].linkNode(nodes[1], Side.BOTTOM);

        /* Insert & let the data travel through the system */
        movementSystem.insertData(nodes[0], testData);
        runUntil(() -> testData.getComponent(NodePathTestComponent.class).isFinished);

        /* Test the path travelled */
        List<Integer> dataPath = testData.getComponent(NodePathTestComponent.class).nodePath;
        List<Integer> expectedPath = Arrays.asList(
                terminusNodes[0].nodeId,
                nodes[0].nodeId,
                nodes[1].nodeId,
                terminusNodes[0].nodeId);

        assertThat(dataPath, is(expectedPath));
        assertTrue(!testData.hasComponent(GraphPositionComponent.class));
    }

    /**
     * Tests a simple graph case where the data moves sequentially through a series of nodes.
     * There is one interior node, an edge
     * <code>1 -> [..2..] -> 3</code>
     */
    @Test
    public void testEdgeGraph() {
        EntityRef testData = buildData();

        /* Build Graph */
        TerminusNode[] nodes = createTerminus(2, TestUpwardsDefinition.BLOCK_URI);
        EdgeNode edgeNode = graph.createEdgeNode(TestUpwardsDefinition.BLOCK_URI);

        /* Always link from the edge node */
        nodes[0].linkNode(edgeNode, Side.TOP);
        edgeNode.linkNode(nodes[0], Side.BACK, Side.BOTTOM);
        edgeNode.linkNode(nodes[1], Side.FRONT, Side.TOP);
        nodes[1].linkNode(edgeNode, Side.BOTTOM);

        /* Insert & let the data travel through the system */
        movementSystem.insertData(nodes[0], testData);
        runUntil(() -> testData.getComponent(NodePathTestComponent.class).isFinished);

        /* Test the path travelled */
        List<Integer> dataPath = testData.getComponent(NodePathTestComponent.class).nodePath;
        List<Integer> expectedPath = Arrays.asList(
                nodes[0].nodeId,
                edgeNode.nodeId,
                nodes[1].nodeId); // Edge node is created after the other two
        assertThat(dataPath, is(expectedPath));
        assertTrue(!testData.hasComponent(GraphPositionComponent.class));
    }

    /**
     * Tests a graph with a single choice
     * Consists of three junctions, three edges and one
     *
     * <code>               ↱ [..5..] → 2 </code>
     * <code> 1 → [..4..] → 7             </code>
     * <code                ↳ [..6..] → 3 </code>
     * <p>
     * This leads to 3 options for path
     * <code>1 → 4 → 7 → 4 → 1</code>
     * <code>1 → 4 → 7 → 5 → 2</code>
     * <code>1 → 4 → 7 → 6 → 3</code>
     */
    @Test
    public void testBranchedGraph() {
        EntityRef testData = buildData();
        TerminusNode[] terminusNodes = createTerminus(3, TestRandomDefinition.BLOCK_URI);
        EdgeNode[] edgeNodes = createEdges(3, TestRandomDefinition.BLOCK_URI);
        JunctionNode junctionNode = createJunctions(1, TestRandomDefinition.BLOCK_URI)[0];

        /* Make the graph */
        terminusNodes[0].linkNode(edgeNodes[0], Side.TOP);
        edgeNodes[0].linkNode(terminusNodes[0], Side.BACK, Side.BOTTOM);
        edgeNodes[0].linkNode(junctionNode, Side.FRONT, Side.TOP);
        junctionNode.linkNode(edgeNodes[0], Side.BOTTOM);

        junctionNode.linkNode(edgeNodes[1], Side.LEFT);
        edgeNodes[1].linkNode(junctionNode, Side.BACK, Side.RIGHT);
        edgeNodes[1].linkNode(terminusNodes[1], Side.FRONT, Side.TOP);
        terminusNodes[1].linkNode(edgeNodes[1], Side.BOTTOM);

        junctionNode.linkNode(edgeNodes[2], Side.RIGHT);
        edgeNodes[2].linkNode(junctionNode, Side.BACK, Side.LEFT);
        edgeNodes[2].linkNode(terminusNodes[2], Side.FRONT, Side.TOP);
        terminusNodes[2].linkNode(edgeNodes[2], Side.BOTTOM);

        /* Insert & let the data travel through the system */
        movementSystem.insertData(terminusNodes[0], testData);
        runUntil(() -> testData.getComponent(NodePathTestComponent.class).isFinished);



        /* Test the path travelled */
        List<Integer> dataPath = testData.getComponent(NodePathTestComponent.class).nodePath;
        List<List<Integer>> expectedPaths = Arrays.asList(
                Arrays.asList(
                        terminusNodes[0].nodeId,
                        edgeNodes[0].nodeId,
                        junctionNode.nodeId,
                        edgeNodes[0].nodeId,
                        terminusNodes[0].nodeId),
                Arrays.asList(
                        terminusNodes[0].nodeId,
                        edgeNodes[0].nodeId,
                        junctionNode.nodeId,
                        edgeNodes[1].nodeId,
                        terminusNodes[1].nodeId),
                Arrays.asList(
                        terminusNodes[0].nodeId,
                        edgeNodes[0].nodeId,
                        junctionNode.nodeId,
                        edgeNodes[2].nodeId,
                        terminusNodes[2].nodeId)
        );
        assertThat(dataPath, anyOf(
                is(expectedPaths.get(0)),
                is(expectedPaths.get(1)),
                is(expectedPaths.get(2))
        ));
        assertTrue(!testData.hasComponent(GraphPositionComponent.class));
    }

    private JunctionNode[] createJunctions(int count, BlockUri block) {
        JunctionNode[] nodes = new JunctionNode[count];
        for (int i = 0; i < count; i++) {
            nodes[i] = graph.createJunctionNode(block); //This is fine
        }
        return nodes;
    }

    private TerminusNode[] createTerminus(int count, BlockUri block) {
        TerminusNode[] nodes = new TerminusNode[count];
        for (int i = 0; i < count; i++) {
            nodes[i] = graph.createTerminusNode(block); //This is fine
        }
        return nodes;
    }

    private EdgeNode[] createEdges(int count, BlockUri block) {
        EdgeNode[] nodes = new EdgeNode[count];
        for (int i = 0; i < count; i++) {
            nodes[i] = graph.createEdgeNode(block); //This is fine
        }
        return nodes;

    }


    /**
     * Builds an entity data packet, already with appropriate components and values set
     *
     * @return A data packet to be passed around the graphs
     */
    private EntityRef buildData() {
        EntityBuilder builder = getHostContext().get(EntityManager.class).newBuilder();
        builder.setPersistent(true);
        builder.addComponent(new NodePathTestComponent());
        return builder.buildWithoutLifecycleEvents();
    }

}
