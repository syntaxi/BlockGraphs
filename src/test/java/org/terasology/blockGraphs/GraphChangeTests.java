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
import org.terasology.blockGraphs.NodeLinkHelper.NodePosition;
import org.terasology.blockGraphs.graphDefinitions.BlockGraph;
import org.terasology.blockGraphs.graphDefinitions.GraphUri;
import org.terasology.blockGraphs.graphDefinitions.nodes.EdgeNode;
import org.terasology.blockGraphs.graphDefinitions.nodes.GraphNode;
import org.terasology.blockGraphs.graphDefinitions.nodes.JunctionNode;
import org.terasology.blockGraphs.graphDefinitions.nodes.TerminusNode;
import org.terasology.math.geom.Vector3i;
import org.terasology.world.BlockEntityRegistry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.terasology.blockGraphs.NodeLinkHelper.splitEdgeAt;

public class GraphChangeTests extends WorldBasedTests {

    private GraphChangeManager changeManager;
    private BlockEntityRegistry entityRegistry;

    @Before
    public void initialize() {
        super.initialize();

        changeManager = getHostContext().get(GraphChangeManager.class);
        entityRegistry = getHostContext().get(BlockEntityRegistry.class);
    }

    @Test
    public void testGraphOverwriting() {
        List<Vector3i> points1 = pointsToVectors(new int[][]{
                {0, 0, 0},
                {0, 1, 0},
                {0, 2, 0},
                {0, 3, 0},
                {0, 4, 0},
                {0, 5, 0}
        });
        BlockGraph graph1 = constructAndCrunchPoints(points1);
        BlockGraph graph2 = graphManager.getGraphInstance(graph1.getUri());

    }

    @Test
    public void testMergingEdgeEdge() {
        List<Vector3i> points1 = pointsToVectors(new int[][]{
                {0, 0, 0},
                {0, 1, 0},
                {0, 2, 0},
                {0, 3, 0},
                {0, 4, 0},
                {0, 5, 0}
        });
        BlockGraph graph1 = constructAndCrunchPoints(points1);
        List<Vector3i> points2 = points1.stream().map(Vector3i::new).collect(Collectors.toList());
        points2.forEach(pos -> pos.addX(1));
        BlockGraph graph2 = constructAndCrunchPoints(points2);

        NodePosition from = new NodePosition();
        NodePosition to = new NodePosition();
        changeManager.mergeInto(from, to);
        //TODO test
    }

    /**
     * A Terminus -> Edge -> Terminus that is split in the middle of the edge
     */
    @Test
    public void testSplittingEdgeMiddle() {
        List<Vector3i> points = pointsToVectors(new int[][]{
                {0, 0, 0},
                {0, 1, 0},
                {0, 2, 0},
                {0, 3, 0},
                {0, 4, 0},
                {0, 5, 0}
        });
        BlockGraph graph = constructAndCrunchPoints(points);


        NodePosition position = new NodePosition();
        position.graph = graph;
        position.pos = points.get(3);
        position.node = getNodeAt(position.pos, position.graph);
        splitEdgeAt(position, entityRegistry);

        assertNodePath(graph, points, 0, 2, 3, 4, 5);
    }


    /**
     * A Terminus -> Edge -> Terminus that is split at the "back" of the edge
     */
    @Test
    public void testSplittingEdgeBack() {
        List<Vector3i> points = pointsToVectors(new int[][]{
                {0, 0, 0},
                {0, 1, 0},
                {0, 2, 0},
                {0, 3, 0},
                {0, 4, 0},
                {0, 5, 0}
        });
        BlockGraph graph = constructAndCrunchPoints(points);

        NodePosition position = new NodePosition();
        position.graph = graph;
        position.pos = points.get(4);
        position.node = getNodeAt(position.pos, position.graph);
        splitEdgeAt(position, entityRegistry);

        assertNodePath(graph, points, 0, 3, 4, 5);
    }


    /**
     * A Terminus -> Edge -> Terminus that is split at the "front" of the edge
     */
    @Test
    public void testSplittingEdgeFront() {
        List<Vector3i> points = pointsToVectors(new int[][]{
                {0, 0, 0},
                {0, 1, 0},
                {0, 2, 0},
                {0, 3, 0},
                {0, 4, 0},
                {0, 5, 0}
        });
        BlockGraph graph = constructAndCrunchPoints(points);

        NodePosition position = new NodePosition();
        position.graph = graph;
        position.pos = points.get(1);
        position.node = getNodeAt(position.pos, position.graph);
        splitEdgeAt(position, entityRegistry);

        assertNodePath(graph, points, 0, 1, 2, 5);
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
    private void assertNodePath(BlockGraph graph, List<Vector3i> points, int... sequence) {
        Map<Integer, GraphNode> nodeMap = new HashMap<>();
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
    private BlockGraph constructAndCrunchPoints(List<Vector3i> points) {
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
    private void checkBiConnection(GraphNode nodeA, GraphNode nodeB) {
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
    private void checkConnection(GraphNode nodeA, GraphNode nodeB) {
        switch (nodeA.getNodeType()) {
            case TERMINUS:
                assertThat(nodeB, is(((TerminusNode) nodeA).connectionNode));
                break;
            case EDGE:
                assertThat(nodeB, anyOf(is(((EdgeNode) nodeA).frontNode), is(((EdgeNode) nodeA).backNode)));
                break;
            case JUNCTION:
                assertThat(((JunctionNode) nodeA).nodes.values(), hasItem(nodeB));
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + nodeA.getNodeType());
        }
    }

}
