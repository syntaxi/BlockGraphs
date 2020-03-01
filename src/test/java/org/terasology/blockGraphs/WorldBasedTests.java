/*
 * Copyright 2019 MovingBlocks
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

import com.google.common.collect.Lists;
import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.terasology.blockGraphs.dataMovement.GraphPositionComponent;
import org.terasology.blockGraphs.graphDefinitions.BlockGraph;
import org.terasology.blockGraphs.graphDefinitions.GraphNodeComponent;
import org.terasology.blockGraphs.graphDefinitions.GraphUri;
import org.terasology.blockGraphs.graphDefinitions.NodeRef;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.math.geom.Vector3i;
import org.terasology.world.BlockEntityRegistry;
import org.terasology.world.WorldProvider;
import org.terasology.world.block.Block;
import org.terasology.world.block.BlockManager;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.core.AnyOf.anyOf;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;

public class WorldBasedTests extends GraphTesting {

    private WorldProvider worldProvider;
    private BlockEntityRegistry blockEntityRegistry;

    Block randomBlock;
    Block leftBlock;
    Block upwardsBlock;


    @Before
    public void initialize() {
        super.initialize();

        worldProvider = getHostContext().get(WorldProvider.class);
        blockEntityRegistry = getHostContext().get(BlockEntityRegistry.class);

        BlockManager blockManager = getHostContext().get(BlockManager.class);

        randomBlock = blockManager.getBlock("BlockGraphs:TestRandomBlock");
        randomBlock.setKeepActive(true);
        leftBlock = blockManager.getBlock("BlockGraphs:TestLeftBlock");
        leftBlock.setKeepActive(true);
        upwardsBlock = blockManager.getBlock("BlockGraphs:TestUpwardsBlock");
        upwardsBlock.setKeepActive(true);

    }

    /**
     * Sets a number of points to a given block type
     *
     * @param block  The block to set
     * @param points The points to set at
     */
    void setAllTo(Block block, Iterable<Vector3i> points) {
        points.forEach(point -> worldProvider.setBlock(point, block));
    }

    /**
     * Converts a 2d array of numbers into a list of Vector3i's
     * This is used as a helper method to more easily specify points
     *
     * @param points The points to convert
     * @return The points as Vector3i's
     */
    List<Vector3i> pointsToVectors(int[][] points) {
        return Arrays.stream(points).map(coords -> new Vector3i(coords[0], coords[1], coords[2])).collect(Collectors.toList());
    }

    /**
     * Gets the node, if any, at the position in block space
     *
     * @param position The position to get the node at
     * @param graph    The graph the node would belong to
     * @return The node if it exists, null otherwise
     */
    NodeRef getNodeAt(Vector3i position, BlockGraph graph) {
        EntityRef blockEntity = blockEntityRegistry.getExistingEntityAt(position);
        GraphNodeComponent component = blockEntity.getComponent(GraphNodeComponent.class);
        if (component != null && component.graphUri == graph.getUri()) {
            return graph.getNode(component.nodeId);
        } else {
            return null;
        }
    }


    void assertWorldPoints(List<Vector3i> worldPoints, List<Vector3i> points, int from, int to) {
        Vector3i[] expectedPoints = Arrays.copyOfRange(points.toArray(new Vector3i[]{}), from, to);
        assertThat(Arrays.asList(expectedPoints), anyOf(is(worldPoints), is(Lists.reverse(worldPoints))));
    }

    /**
     * Tests that some data inserted at the start of the path, will follow that path through the graph
     *
     * @param points The points in the graph
     * @param graph  The graph to test
     * @param path   A list of indices for the points that the data should have taken
     */
    void testPath(List<Vector3i> points, BlockGraph graph, int[] path) {
        EntityRef testData = buildData(new NodePathTestComponent());
        movementSystem.insertData(getNodeAt(points.get(path[0]), graph), testData);
        runUntil(() -> testData.getComponent(NodePathTestComponent.class).isFinished);

        /* Test the path travelled */
        List<Integer> dataPath = testData.getComponent(NodePathTestComponent.class).nodePath;
        List<Integer> expectedPath = Arrays.stream(path)
                .mapToObj(points::get)
                .map(pos -> getNodeAt(pos, graph))
                .map(NodeRef::getNodeId)
                .collect(Collectors.toList());

        assertThat(dataPath, is(expectedPath)); // Check the paths are the same
        assertFalse(testData.hasComponent(GraphPositionComponent.class)); // Should be removed when the data is
    }

    void testPath(List<Vector3i> points, BlockGraph graph) {
        testPath(points, graph, IntStream.range(0, points.size()).toArray());
    }


    /**
     * Asserts that a chain of nodes is doubly linked.
     * <p>
     * For example:
     * If points contained [<1,0,0>, <0,0,0>, <0,1,0>, <0,2,0>, <1,2,0>]
     * and sequence was [0, 3, 2, 1]
     * then the path being asserted would be "<1,0,0> <-> <0,2,0> <-> <0,1,0> <-> <0,0,0>"
     *
     * @param graph    The graph the nodes belong to
     * @param points   The list of node positions
     * @param sequence The order the points should connect in
     */
    void assertNodePath(BlockGraph graph, List<Vector3i> points, int... sequence) {
        Map<Integer, NodeRef> nodeMap = new HashMap<>();
        for (int i : sequence) {
            nodeMap.put(i, getNodeAt(points.get(i), graph));
        }

        for (int i = 0; i < sequence.length - 1; i++) {
            checkBiConnection(nodeMap.get(sequence[i]), nodeMap.get(sequence[i + 1]));
        }
    }


    /**
     * Constructs and then crunches a graph from the set of points
     *
     * @param points A group of points
     * @return The new block graph covering those points
     */
    BlockGraph constructAndCrunchPoints(List<Vector3i> points) {
        forceAndWaitForGeneration(Vector3i.zero());
        setAllTo(upwardsBlock, points);

        GraphUri graphUri = graphConstructor.constructEntireGraph(points.get(0));
        BlockGraph graph = graphManager.getGraphInstance(graphUri);
        graphConstructor.crunchGraph(graph);
        return graph;
    }

    /**
     * Asserts that there is a double link between the two nodes. That is, A -> B and B -> A
     *
     * @param nodeA The first node to check
     * @param nodeB The second node to check
     */
    void checkBiConnection(NodeRef nodeA, NodeRef nodeB) {
        checkConnection(nodeA, nodeB);
        checkConnection(nodeB, nodeA);
    }

    /**
     * Checks if nodeA is linked to nodeB.
     * This check is only a one way check. Ie it ensures that A -> B but does not say anything about B -> A
     *
     * @param nodeA The node the connection should "leave from"
     * @param nodeB The node the connection should "enter"
     */
    void checkConnection(NodeRef nodeA, NodeRef nodeB) {
        switch (nodeA.getNodeType()) {
            case TERMINUS:
                assertThat(nodeB, is(nodeA.asTerminus().connectionNode));
                break;
            case EDGE:
                assertThat(nodeB, CoreMatchers.anyOf(is(nodeA.asEdge().frontNode), is(nodeA.asEdge().backNode)));
                break;
            case JUNCTION:
                assertThat(nodeA.asJunction().nodes.values(), hasItem(nodeB));
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + nodeA.getNodeType());
        }
    }


}
