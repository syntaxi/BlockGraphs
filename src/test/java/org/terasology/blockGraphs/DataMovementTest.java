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

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class DataMovementTest extends ModuleTestingEnvironment {
    private static final Logger logger = LoggerFactory.getLogger(DataMovementTest.class);
    private static final BlockUri BLOCK_URI = new BlockUri("BlockGraphs:TestBlock");

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
        JunctionNode[] nodes = createJunctions(4);
        nodes[0].linkNode(nodes[1], Side.TOP); // 0 → 1

        nodes[1].linkNode(nodes[0], Side.BOTTOM); // 1 → 0
        nodes[1].linkNode(nodes[3], Side.TOP); // 1 → 3

        nodes[3].linkNode(nodes[1], Side.BOTTOM); // 3 → 1
        nodes[3].linkNode(nodes[2], Side.TOP); // 3 → 2

        nodes[2].linkNode(nodes[3], Side.BOTTOM); // 2 → 3

        /* Insert & let the data travel through the system */
        movementSystem.insertData(nodes[0], testData);
        runUntil(() -> testData.getComponent(NodePathTestComponent.class).isFinished);

        /* Test the path travelled */
        List<Integer> dataPath = testData.getComponent(NodePathTestComponent.class).nodePath;
        List<Integer> expectedPath = Arrays.asList(1, 3, 2, 4); //Order nodes are created is tweaked
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
        JunctionNode[] nodes = createJunctions(2);
        EdgeNode edgeNode = graph.createEdgeNode(BLOCK_URI);

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
        List<Integer> expectedPath = Arrays.asList(1, 3, 2); // Edge node is created after the other two
        assertThat(dataPath, is(expectedPath));
        assertTrue(!testData.hasComponent(GraphPositionComponent.class));
    }

    @Test
    public void testBranchedGraph() {

    }

    private JunctionNode[] createJunctions(int count) {
        JunctionNode[] nodes = new JunctionNode[count];
        for (int i = 0; i < count; i++) {
            nodes[i] = graph.createJunctionNode(BLOCK_URI); //This is fine
        }
        return nodes;
    }

    private TerminusNode[] createTerminus(int count) {
        TerminusNode[] nodes = new TerminusNode[count];
        for (int i = 0; i < count; i++) {
            nodes[i] = graph.createTerminusNode(BLOCK_URI); //This is fine
        }
        return nodes;
    }

    private EdgeNode[] createEdges(int count) {
        EdgeNode[] nodes = new EdgeNode[count];
        for (int i = 0; i < count; i++) {
            nodes[i] = graph.createEdgeNode(BLOCK_URI); //This is fine
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
