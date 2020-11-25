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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.blockGraphs.graphDefinitions.BlockGraph;
import org.terasology.blockGraphs.graphDefinitions.GraphUri;
import org.terasology.blockGraphs.graphDefinitions.NodeRef;
import org.terasology.blockGraphs.graphDefinitions.nodes.NodeType;
import org.terasology.math.Side;
import org.terasology.math.geom.Vector3i;
import org.terasology.moduletestingenvironment.ModuleTestingHelper;
import org.terasology.moduletestingenvironment.extension.Dependencies;
import org.terasology.registry.In;

import java.util.List;

import static org.hamcrest.core.AnyOf.anyOf;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;

@Dependencies({"engine", "BlockGraphs"})
public class GraphConstructionTests extends WorldBasedTests {
    private static final Logger logger = LoggerFactory.getLogger(GraphConstructionTests.class);


    @In
    protected ModuleTestingHelper localHelper;

    @BeforeEach
    public void initialize() {
        graphManager.clear();
        super.initialize();

    }

    /**
     * Only one valid block
     */
    @Test
    public void testSinglePoint() {
        List<Vector3i> points = pointsToVectors(new int[][]{{0, 0, 0}});
        localHelper.forceAndWaitForGeneration(Vector3i.zero());
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
        helper.forceAndWaitForGeneration(Vector3i.zero());
        setAllTo(upwardsBlock, points);

        GraphUri graphUri = graphConstructor.constructEntireGraph(points.get(0));
        BlockGraph graph = graphManager.getGraphInstance(graphUri);

        assertEquals(graphUri.toString(), "BlockGraphs:TestGraph.1"); // Graph was made with right URI
        assertEquals(points.size(), graph.getNodeCount()); // Has the right number of nodes
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
        helper.forceAndWaitForGeneration(Vector3i.zero());
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
        helper.forceAndWaitForGeneration(Vector3i.zero());
        setAllTo(upwardsBlock, points);

        GraphUri graphUri = graphConstructor.constructEntireGraph(points.get(0));
        BlockGraph graph = graphManager.getGraphInstance(graphUri);

        assertEquals(graphUri.toString(), "BlockGraphs:TestGraph.1"); // Graph was made with right URI
        assertEquals(graph.getNodeCount(), points.size()); // Has the right number of nodes
        assertSame(getNodeAt(points.get(3), graph).getNodeType(), NodeType.JUNCTION); //The node was made a junction
        // properly
        for (Vector3i point : points) {
            assertNotNull(getNodeAt(point, graph)); // Each position has a relevant node
        }

        /* Test that the nodes are linked up properly */
        testPath(points, graph, new int[]{0, 1, 2, 3, 4}); // The last point shouldn't be traced
    }

    /**
     * A path with multiple graph types x x x x x x x U x x x U L U x x x x L x x x x L x x x x x x
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

        helper.forceAndWaitForGeneration(Vector3i.zero());
        setAllTo(upwardsBlock, points);
        setAllTo(leftBlock, pointsToVectors(new int[][]{{5, 5, 5}, {4, 7, 5}, {5, 6, 5}}));

        GraphUri graphUri = graphConstructor.constructEntireGraph(points.get(0));
        BlockGraph graph = graphManager.getGraphInstance(graphUri);

        assertEquals(graphUri.toString(), "BlockGraphs:TestGraph.1"); // Graph was made with right URI
        assertEquals(graph.getNodeCount(), points.size()); // Has the right number of nodes
        assertSame(getNodeAt(points.get(3), graph).getNodeType(), NodeType.JUNCTION); //The node was made a junction
        // properly
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

        helper.forceAndWaitForGeneration(Vector3i.zero());
        setAllTo(upwardsBlock, points);

        GraphUri graphUri = graphConstructor.constructEntireGraph(points.get(0));
        BlockGraph graph = graphManager.getGraphInstance(graphUri);

        assertEquals(graphUri.toString(), "BlockGraphs:TestGraph.1"); // Graph was made with right URI
        assertEquals(points.size(), graph.getNodeCount()); // Has the right number of nodes

        graphConstructor.crunchGraph(graph);

        assertEquals(3, graph.getNodeCount()); // Has the right number of nodes
        assertThat(getNodeAt(points.get(3), graph), is(getNodeAt(points.get(6), graph))); // All blocks point to the
        // same edge

        NodeRef front = getNodeAt(points.get(0), graph);
        NodeRef back = getNodeAt(points.get(9), graph);
        NodeRef middle = getNodeAt(points.get(4), graph);
        assertNotNull(front);
        assertNotNull(back);
        assertNotNull(middle);
        assertThat(front.asTerminus().connectionNode, is(middle));
        assertThat(back.asTerminus().connectionNode, is(middle));
        assertThat(middle.asEdge().frontNode, is(front));
        assertThat(middle.asEdge().backNode, is(back));

        /* Assert the edge has the right points */
        assertWorldPoints(middle.asEdge().worldPositions, points, 1, points.size() - 1);
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

        helper.forceAndWaitForGeneration(Vector3i.zero());
        setAllTo(upwardsBlock, points);

        GraphUri graphUri = graphConstructor.constructEntireGraph(points.get(0));
        BlockGraph graph = graphManager.getGraphInstance(graphUri);

        assertEquals(graphUri.toString(), "BlockGraphs:TestGraph.1"); // Graph was made with right URI
        assertEquals(graph.getNodeCount(), points.size()); // Has the right number of nodes

        graphConstructor.crunchGraph(graph);
        assertEquals(graph.getNodeCount(), 6); // Has the right number of nodes
        assertNotSame(getNodeAt(points.get(3), graph), getNodeAt(points.get(6), graph)); // Blocks either side of the
        // junction are not the same

        NodeRef front = getNodeAt(points.get(0), graph);
        NodeRef neck = getNodeAt(points.get(2), graph);
        NodeRef center = getNodeAt(points.get(5), graph);
        NodeRef tail = getNodeAt(points.get(7), graph);
        NodeRef back = getNodeAt(points.get(9), graph);
        for (NodeRef node : new NodeRef[]{front, neck, center, tail, back}) {
            assertNotNull(node);
        }

        /* Test that the connections were correct */
        assertThat(front.asTerminus().connectionNode, is(neck));
        assertThat(neck.asEdge().frontNode, is(front));

        assertThat(neck.asEdge().backNode, is(center));
        assertThat(center.asJunction().getNodeForSide(Side.BOTTOM), is(neck));

        assertThat(center.asJunction().getNodeForSide(Side.TOP), is(tail));
        assertThat(tail.asEdge().frontNode, is(center));

        assertThat(tail.asEdge().backNode, is(back));
        assertThat(back.asTerminus().connectionNode, is(tail));

        assertWorldPoints(neck.asEdge().worldPositions, points, 1, 5);
        assertWorldPoints(tail.asEdge().worldPositions, points, 6, 9);
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


        helper.forceAndWaitForGeneration(Vector3i.zero());
        setAllTo(upwardsBlock, points);
        setAllTo(randomBlock, pointsToVectors(new int[][]{{0, 0, 0}, {0, 6, 0}}));
        setAllTo(leftBlock, pointsToVectors(new int[][]{{0, 4, 0}, {0, 5, 0}}));

        GraphUri graphUri = graphConstructor.constructEntireGraph(points.get(0));
        BlockGraph graph = graphManager.getGraphInstance(graphUri);

        assertEquals(graphUri.toString(), "BlockGraphs:TestGraph.1"); // Graph was made with right URI
        assertEquals(points.size(), graph.getNodeCount()); // Has the right number of nodes

        graphConstructor.crunchGraph(graph);
        assertEquals(graph.getNodeCount(), 4); // Has the right number of nodes
        assertNotSame(getNodeAt(points.get(1), graph), getNodeAt(points.get(5), graph)); // Two different edges

        NodeRef front = getNodeAt(points.get(0), graph);
        NodeRef neck = getNodeAt(points.get(1), graph);
        NodeRef tail = getNodeAt(points.get(5), graph);
        NodeRef back = getNodeAt(points.get(6), graph);
        for (NodeRef node : new NodeRef[]{front, neck, tail, back}) {
            assertNotNull(node);
        }

        /* Test that the connections were correct */
        assertThat(front.asTerminus().connectionNode, is(neck));
        assertThat(neck.asEdge().frontNode, is(front));

        assertThat(neck.asEdge().backNode, is(tail));
        assertThat(tail.asEdge().frontNode, is(neck));

        assertThat(tail.asEdge().backNode, is(back));
        assertThat(back.asTerminus().connectionNode, is(tail));

        assertWorldPoints(neck.asEdge().worldPositions, points, 1, 4);
        assertWorldPoints(tail.asEdge().worldPositions, points, 4, 6);
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


        helper.forceAndWaitForGeneration(Vector3i.zero());
        setAllTo(upwardsBlock, points);

        GraphUri graphUri = graphConstructor.constructEntireGraph(points.get(0));
        BlockGraph graph = graphManager.getGraphInstance(graphUri);

        assertEquals(graphUri.toString(), "BlockGraphs:TestGraph.1"); // Graph was made with right URI
        assertEquals(graph.getNodeCount(), points.size()); // Has the right number of nodes
        NodeRef front = getNodeAt(points.get(0), graph);
        NodeRef back = getNodeAt(points.get(7), graph);
        assertSame(front.getNodeType(), NodeType.EDGE);
        assertSame(back.getNodeType(), NodeType.EDGE);
        assertThat(back, anyOf(is(front.asEdge().frontNode), is(front.asEdge().backNode)));
        assertThat(front, anyOf(is(back.asEdge().frontNode), is(back.asEdge().backNode)));

        graphConstructor.crunchGraph(graph);

        assertEquals(graph.getNodeCount(), 1); // Has the right number of nodes
        assertSame(getNodeAt(points.get(0), graph), getNodeAt(points.get(7), graph)); // Two different edges
    }

}
