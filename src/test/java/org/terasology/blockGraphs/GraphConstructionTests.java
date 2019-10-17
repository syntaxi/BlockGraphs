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
import org.terasology.blockGraphs.graphDefinitions.BlockGraph;
import org.terasology.blockGraphs.graphDefinitions.GraphUri;
import org.terasology.blockGraphs.graphDefinitions.nodes.EdgeNode;
import org.terasology.blockGraphs.graphDefinitions.nodes.GraphNode;
import org.terasology.blockGraphs.graphDefinitions.nodes.JunctionNode;
import org.terasology.blockGraphs.graphDefinitions.nodes.TerminusNode;
import org.terasology.math.Side;
import org.terasology.math.geom.Vector3i;

import java.util.List;

import static org.hamcrest.core.AnyOf.anyOf;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class GraphConstructionTests extends WorldBasedTests {
    private static final Logger logger = LoggerFactory.getLogger(GraphConstructionTests.class);

    @Before
    public void initialize() {
        super.initialize();

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

    /**
     * Tests crunching a simple graph with just one edge into a smaller graph
     */
    @Test
    public void testSimpleCrunch() {
        List<Vector3i> points = pointsToVectors(new int[][]{
                {0, 0, 0}, // Terminus
                {0, 1, 0},
                {0, 2, 0},
                {0, 3, 0},
                {0, 4, 0},
                {0, 5, 0},
                {0, 6, 0},
                {0, 7, 0},
                {0, 8, 0},
                {0, 9, 0}  // Terminus
        });

        forceAndWaitForGeneration(Vector3i.zero());
        setAllTo(upwardsBlock, points);

        GraphUri graphUri = graphConstructor.constructEntireGraph(points.get(0));
        BlockGraph graph = graphManager.getGraphInstance(graphUri);

        assertEquals(graphUri.toString(), "BlockGraphs:TestGraph.1"); // Graph was made with right URI
        assertEquals(graph.getNodeCount(), points.size()); // Has the right number of nodes

        graphConstructor.crunchGraph(graph);

        assertEquals(graph.getNodeCount(), 3); // Has the right number of nodes
        assertThat(getNodeAt(points.get(3), graph), is(getNodeAt(points.get(6), graph))); // All blocks point to the same edge

        TerminusNode front = (TerminusNode) getNodeAt(points.get(0), graph);
        TerminusNode back = (TerminusNode) getNodeAt(points.get(9), graph);
        EdgeNode middle = (EdgeNode) getNodeAt(points.get(4), graph);
        assertNotNull(front);
        assertNotNull(back);
        assertNotNull(middle);
        assertThat(front.connectionNode, is(middle));
        assertThat(back.connectionNode, is(middle));
        assertThat(middle.frontNode, is(front));
        assertThat(middle.backNode, is(back));

        /* Assert the edge has the right points */
        assertWorldPoints(middle.worldPositions, points, 1, points.size() - 1);
    }


    /**
     * Tests crunching a graph with a junction into a smaller graph
     */
    @Test
    public void testJunctionCrunch() {
        List<Vector3i> points = pointsToVectors(new int[][]{
                {0, 0, 0}, // Terminus
                {0, 1, 0},
                {0, 2, 0},
                {0, 3, 0},
                {0, 4, 0},
                {0, 5, 0}, // Junction
                {0, 6, 0},
                {0, 7, 0},
                {0, 8, 0},
                {0, 9, 0}, // Terminus
                {1, 5, 0}  // Terminus
        });

        forceAndWaitForGeneration(Vector3i.zero());
        setAllTo(upwardsBlock, points);

        GraphUri graphUri = graphConstructor.constructEntireGraph(points.get(0));
        BlockGraph graph = graphManager.getGraphInstance(graphUri);

        assertEquals(graphUri.toString(), "BlockGraphs:TestGraph.1"); // Graph was made with right URI
        assertEquals(graph.getNodeCount(), points.size()); // Has the right number of nodes

        graphConstructor.crunchGraph(graph);
        assertEquals(graph.getNodeCount(), 6); // Has the right number of nodes
        assertNotSame(getNodeAt(points.get(3), graph), getNodeAt(points.get(6), graph)); // Blocks either side of the junction are not the same

        TerminusNode front = (TerminusNode) getNodeAt(points.get(0), graph);
        EdgeNode neck = (EdgeNode) getNodeAt(points.get(2), graph);
        JunctionNode center = (JunctionNode) getNodeAt(points.get(5), graph);
        EdgeNode tail = (EdgeNode) getNodeAt(points.get(7), graph);
        TerminusNode back = (TerminusNode) getNodeAt(points.get(9), graph);
        for (GraphNode node : new GraphNode[]{front, neck, center, tail, back}) {
            assertNotNull(node);
        }

        /* Test that the connections were correct */
        assertThat(front.connectionNode, is(neck));
        assertThat(neck.frontNode, is(front));

        assertThat(neck.backNode, is(center));
        assertThat(center.getNodeForSide(Side.BOTTOM), is(neck));

        assertThat(center.getNodeForSide(Side.TOP), is(tail));
        assertThat(tail.frontNode, is(center));

        assertThat(tail.backNode, is(back));
        assertThat(back.connectionNode, is(tail));

        assertWorldPoints(neck.worldPositions, points, 1, 5);
        assertWorldPoints(tail.worldPositions, points, 6, 9);
    }

    /**
     * Tests crunching to edges with different definitions down
     */
    @Test
    public void testMixedDefCrunch() {
        List<Vector3i> points = pointsToVectors(new int[][]{
                {0, 0, 0}, // Terminus
                {0, 1, 0}, // A
                {0, 2, 0}, // A
                {0, 3, 0}, // A
                {0, 4, 0}, // B
                {0, 5, 0}, // B
                {0, 6, 0}, // Terminus
        });


        forceAndWaitForGeneration(Vector3i.zero());
        setAllTo(upwardsBlock, points);
        setAllTo(randomBlock, pointsToVectors(new int[][]{{0, 0, 0}, {0, 6, 0}}));
        setAllTo(leftBlock, pointsToVectors(new int[][]{{0, 4, 0}, {0, 5, 0}}));

        GraphUri graphUri = graphConstructor.constructEntireGraph(points.get(0));
        BlockGraph graph = graphManager.getGraphInstance(graphUri);

        assertEquals(graphUri.toString(), "BlockGraphs:TestGraph.1"); // Graph was made with right URI
        assertEquals(graph.getNodeCount(), points.size()); // Has the right number of nodes

        graphConstructor.crunchGraph(graph);
        assertEquals(graph.getNodeCount(), 4); // Has the right number of nodes
        assertNotSame(getNodeAt(points.get(1), graph), getNodeAt(points.get(5), graph)); // Two different edges

        TerminusNode front = (TerminusNode) getNodeAt(points.get(0), graph);
        EdgeNode neck = (EdgeNode) getNodeAt(points.get(1), graph);
        EdgeNode tail = (EdgeNode) getNodeAt(points.get(5), graph);
        TerminusNode back = (TerminusNode) getNodeAt(points.get(6), graph);
        for (GraphNode node : new GraphNode[]{front, neck, tail, back}) {
            assertNotNull(node);
        }

        /* Test that the connections were correct */
        assertThat(front.connectionNode, is(neck));
        assertThat(neck.frontNode, is(front));

        assertThat(neck.backNode, is(tail));
        assertThat(tail.frontNode, is(neck));

        assertThat(tail.backNode, is(back));
        assertThat(back.connectionNode, is(tail));

        assertWorldPoints(neck.worldPositions, points, 1, 4);
        assertWorldPoints(tail.worldPositions, points, 4, 6);
    }

    /**
     * Tests a circular situation
     */
    @Test
    public void testCircle() {
        List<Vector3i> points = pointsToVectors(new int[][]{
                {0, 0, 0},
                {0, 1, 0},
                {0, 2, 0},
                {1, 2, 0},
                {2, 2, 0},
                {2, 1, 0},
                {2, 0, 0},
                {1, 0, 0}
        });


        forceAndWaitForGeneration(Vector3i.zero());
        setAllTo(upwardsBlock, points);

        GraphUri graphUri = graphConstructor.constructEntireGraph(points.get(0));
        BlockGraph graph = graphManager.getGraphInstance(graphUri);

        assertEquals(graphUri.toString(), "BlockGraphs:TestGraph.1"); // Graph was made with right URI
        assertEquals(graph.getNodeCount(), points.size()); // Has the right number of nodes
        GraphNode front = getNodeAt(points.get(0), graph);
        GraphNode back = getNodeAt(points.get(7), graph);
        assertTrue(front instanceof EdgeNode);
        assertTrue(back instanceof EdgeNode);
        assertThat(back, anyOf(is(((EdgeNode) front).frontNode), is(((EdgeNode) front).backNode)));
        assertThat(front, anyOf(is(((EdgeNode) back).frontNode), is(((EdgeNode) back).backNode)));

        graphConstructor.crunchGraph(graph);
        assertEquals(graph.getNodeCount(), 1); // Has the right number of nodes
        assertSame(getNodeAt(points.get(0), graph), getNodeAt(points.get(7), graph)); // Two different edges
    }

}
