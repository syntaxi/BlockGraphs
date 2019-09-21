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

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.blockGraphs.dataMovement.GraphPositionComponent;
import org.terasology.blockGraphs.graphDefinitions.BlockGraph;
import org.terasology.blockGraphs.graphDefinitions.GraphNodeComponent;
import org.terasology.blockGraphs.graphDefinitions.GraphUri;
import org.terasology.blockGraphs.graphDefinitions.nodes.GraphNode;
import org.terasology.blockGraphs.graphDefinitions.nodes.JunctionNode;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.math.geom.Vector3i;
import org.terasology.world.BlockEntityRegistry;
import org.terasology.world.WorldProvider;
import org.terasology.world.block.Block;
import org.terasology.world.block.BlockManager;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class GraphConstructionTests extends GraphTesting {
    private static final Logger logger = LoggerFactory.getLogger(GraphConstructionTests.class);

    private WorldProvider worldProvider;
    private Block leftBlock;
    private Block upwardsBlock;
    private BlockEntityRegistry blockEntityRegistry;

    @Before
    public void initialize() {
        super.initialize();

        worldProvider = getHostContext().get(WorldProvider.class);
        blockEntityRegistry = getHostContext().get(BlockEntityRegistry.class);
        BlockManager blockManager = getHostContext().get(BlockManager.class);

        leftBlock = blockManager.getBlock("BlockGraphs:TestLeftBlock");
        leftBlock.setKeepActive(true);
        upwardsBlock = blockManager.getBlock("BlockGraphs:TestUpwardsBlock");
        upwardsBlock.setKeepActive(true);
    }

    private void setAllTo(Block block, Iterable<Vector3i> points) {
        points.forEach(point -> worldProvider.setBlock(point, block));
    }

    private List<Vector3i> pointsToVectors(int[][] points) {
        return Arrays.stream(points).map(coords -> new Vector3i(coords[0], coords[1], coords[2])).collect(Collectors.toList());
    }

    /**
     * Gets the node, if any, at the position in block space
     *
     * @param position The position to get the node at
     * @param graph    The graph the node would belong to
     * @return The node if it exists, null otherwise
     */
    private GraphNode getNodeAt(Vector3i position, BlockGraph graph) {
        EntityRef blockEntity = blockEntityRegistry.getExistingEntityAt(position);
        GraphNodeComponent component = blockEntity.getComponent(GraphNodeComponent.class);
        if (component != null && component.graphUri == graph.getUri()) {
            return graph.getNode(component.nodeId);
        } else {
            return null;
        }
    }

    /**
     * Only one valid block
     */
    @Test
    public void testSinglePoint() {
        List<Vector3i> points = pointsToVectors(new int[][]{{0, 0, 0}});
        forceAndWaitForGeneration(Vector3i.zero());
        setAllTo(upwardsBlock, points);

        GraphUri graphUri = graphConstructor.constructEntireGraph(points.get(0));
        BlockGraph graph = graphManager.getGraphInstance(graphUri);

        assertEquals(graphUri.toString(), "BlockGraphs:TestGraph.1");
        assertEquals(graph.getNodeCount(), points.size());
    }

    /**
     * A line of 4 blocks
     */
    @Test
    public void testLine() {
        List<Vector3i> points = pointsToVectors(new int[][]{
                {0, 0, 0},
                {0, 1, 0},
                {0, 2, 0},
                {0, 3, 0}
        });
        forceAndWaitForGeneration(Vector3i.zero());
        setAllTo(upwardsBlock, points);

        GraphUri graphUri = graphConstructor.constructEntireGraph(points.get(0));
        BlockGraph graph = graphManager.getGraphInstance(graphUri);

        assertEquals(graphUri.toString(), "BlockGraphs:TestGraph.1"); // Graph was made with right URI
        assertEquals(graph.getNodeCount(), points.size()); // Has the right number of nodes
        for (Vector3i point : points) {
            assertNotNull(getNodeAt(point, graph)); // Each position has a relevant node
        }

        /* Test that the nodes are linked up properly */
        testPath(points, graph);
    }

    /**
     * A non-straight line of 4 blocks
     */
    @Test
    public void testWibblyLine() {
        List<Vector3i> points = pointsToVectors(new int[][]{
                {0, 0, 0},
                {0, 1, 0},
                {0, 2, 0},
                {1, 2, 0},
                {2, 2, 0},
                {2, 3, 0}
        });
        forceAndWaitForGeneration(Vector3i.zero());
        setAllTo(upwardsBlock, points);

        GraphUri graphUri = graphConstructor.constructEntireGraph(points.get(0));
        BlockGraph graph = graphManager.getGraphInstance(graphUri);

        assertEquals(graphUri.toString(), "BlockGraphs:TestGraph.1"); // Graph was made with right URI
        assertEquals(graph.getNodeCount(), points.size()); // Has the right number of nodes
        for (Vector3i point : points) {
            assertNotNull(getNodeAt(point, graph)); // Each position has a relevant node
        }

        /* Test that the nodes are linked up properly */
        testPath(points, graph);
    }

    /**
     * A path with a junction in it
     */
    @Test
    public void testJunction() {
        List<Vector3i> points = pointsToVectors(new int[][]{
                {0, 0, 0},
                {0, 1, 0},
                {0, 2, 0},
                {1, 2, 0}, // This should be a junction
                {1, 3, 0},
                {2, 2, 0}
        });
        forceAndWaitForGeneration(Vector3i.zero());
        setAllTo(upwardsBlock, points);

        GraphUri graphUri = graphConstructor.constructEntireGraph(points.get(0));
        BlockGraph graph = graphManager.getGraphInstance(graphUri);

        assertEquals(graphUri.toString(), "BlockGraphs:TestGraph.1"); // Graph was made with right URI
        assertEquals(graph.getNodeCount(), points.size()); // Has the right number of nodes
        assertTrue(getNodeAt(points.get(3), graph) instanceof JunctionNode); //The node was made a junction properly
        for (Vector3i point : points) {
            assertNotNull(getNodeAt(point, graph)); // Each position has a relevant node
        }

        /* Test that the nodes are linked up properly */
        testPath(points, graph, new int[]{0, 1, 2, 3, 4}); // The last point shouldn't be traced
    }

    /**
     * A path with multiple graph types
     * x x x x x
     * x x U x x
     * x U L U x
     * x x x L x
     * x x x L x
     * x x x x x
     */
    @Test
    public void testMultipleType() {
        List<Vector3i> points = pointsToVectors(new int[][]{
                {5, 5, 5}, // LEFT  Start
                {5, 6, 5}, // LEFT
                {5, 7, 5}, // UP
                {4, 7, 5}, // LEFT  This should be a junction
                {3, 7, 5}, // UP    End
                {4, 8, 5}  // UP
        });

        forceAndWaitForGeneration(Vector3i.zero());
        setAllTo(upwardsBlock, points);
        setAllTo(leftBlock, pointsToVectors(new int[][]{{5, 5, 5}, {4, 7, 5}, {5, 6, 5}}));

        GraphUri graphUri = graphConstructor.constructEntireGraph(points.get(0));
        BlockGraph graph = graphManager.getGraphInstance(graphUri);

        assertEquals(graphUri.toString(), "BlockGraphs:TestGraph.1"); // Graph was made with right URI
        assertEquals(graph.getNodeCount(), points.size()); // Has the right number of nodes
        assertTrue(getNodeAt(points.get(3), graph) instanceof JunctionNode); //The node was made a junction properly
        for (Vector3i point : points) {
            assertNotNull(getNodeAt(point, graph)); // Each position has a relevant node
        }

        /* Test that the nodes are linked up properly */
        testPath(points, graph, new int[]{0, 1, 2, 3, 4}); // The last point shouldn't be traced
    }

    @SuppressWarnings("ConstantConditions") // We assert that each pos is not null
    private void testPath(List<Vector3i> points, BlockGraph graph, int[] path) {
        EntityRef testData = buildData();
        movementSystem.insertData(getNodeAt(points.get(path[0]), graph), testData);
        runUntil(() -> testData.getComponent(NodePathTestComponent.class).isFinished);

        /* Test the path travelled */
        List<Integer> dataPath = testData.getComponent(NodePathTestComponent.class).nodePath;
        List<Integer> expectedPath = Arrays.stream(path)
                .mapToObj(points::get)
                .map(pos -> getNodeAt(pos, graph))
                .map(node -> node.nodeId)
                .collect(Collectors.toList());

        assertThat(dataPath, is(expectedPath)); // Check the paths are the same
        assertFalse(testData.hasComponent(GraphPositionComponent.class)); // Should be removed when the data is
    }

    private void testPath(List<Vector3i> points, BlockGraph graph) {
        testPath(points, graph, IntStream.range(0, points.size()).toArray());
    }
}
