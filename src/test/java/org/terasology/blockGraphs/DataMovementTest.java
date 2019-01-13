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
import org.terasology.blockGraphs.graphDefinitions.nodes.GraphNode;
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

    private BlockGraph graph;
    private GraphMovementSystem movementSystem;
    private BlockGraphManager graphManager;


    private GraphNode nodeOne;

    private static final BlockUri BLOCK_URI = new BlockUri("BlockGraphs:TestBlock");

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
        graphType.addNodeType(new TestNodeDefinition());

        graphManager.addGraphType(graphType);
        graph = graphManager.newGraphInstance(graphType);

        nodeOne = graph.createNode(BLOCK_URI);
        GraphNode nodeTwo = graph.createNode(BLOCK_URI);
        GraphNode nodeThree = graph.createNode(BLOCK_URI);
        GraphNode nodeFour = graph.createNode(BLOCK_URI);

        nodeOne.linkNode(nodeTwo, Side.TOP);
        nodeTwo.linkNode(nodeThree, Side.TOP);
        nodeThree.linkNode(nodeFour, Side.TOP);


    }


    @Test
    public void testRegistration() {
        assertNotNull(graphManager.getGraphInstance(graph.getUri()));
    }

    /**
     * Tests a simple graph case where the data moves sequentially through a series of nodes
     */
    @Test
    public void testGraphMovement() {
        /* Build the data */
        EntityBuilder builder = getHostContext().get(EntityManager.class).newBuilder();
        builder.setPersistent(true);
        builder.addComponent(new NodePathTestComponent());
        EntityRef testData = builder.buildWithoutLifecycleEvents();

        /* Insert & let the data travel through the system */
        movementSystem.insertData(nodeOne, testData, graphManager.getNodeDefinition(graph.getUri(), nodeOne.getNodeId()));
        runWhile(() -> testData.hasComponent(GraphPositionComponent.class));

        /* Test the path travelled */
        List<Integer> dataPath = testData.getComponent(NodePathTestComponent.class).nodePath;
        List<Integer> expectedPath = Arrays.asList(1, 2, 3, 4);
        assertThat(dataPath, is(expectedPath));
        assertTrue(!testData.hasComponent(GraphPositionComponent.class));
    }
}
