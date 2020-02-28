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
import org.terasology.blockGraphs.NodeLinkHelper.NodePosition;
import org.terasology.blockGraphs.graphDefinitions.BlockGraph;
import org.terasology.math.geom.Vector3i;
import org.terasology.world.BlockEntityRegistry;

import java.util.List;

import static org.terasology.blockGraphs.NodeLinkHelper.splitEdgeAt;

public class GraphChangeTests extends WorldBasedTests {
    Logger logger = LoggerFactory.getLogger(GraphChangeTests.class);

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


    /**
     * A Terminus -> Edge -> Terminus that is split in the middle of the edge
     */
    @Test
    public void testSplittingEdgeMiddle() {
        List<Vector3i> points = pointsToVectors(new int[][]{
                {0, 0, 0}, //Terminus
                {0, 1, 0},
                {0, 2, 0},
                {0, 3, 0}, // <- Split
                {0, 4, 0},
                {0, 5, 0} //Terminus
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
                {0, 0, 0}, //TERMINUS
                {0, 1, 0},
                {0, 2, 0},
                {0, 3, 0},
                {0, 4, 0}, // <- split
                {0, 5, 0} //TERMINUS
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
                {0, 0, 0}, //TERMINUS
                {0, 1, 0}, // <- split
                {0, 2, 0},
                {0, 3, 0},
                {0, 4, 0},
                {0, 5, 0} //TERMINUS
        });
        BlockGraph graph = constructAndCrunchPoints(points);

        NodePosition position = new NodePosition();
        position.graph = graph;
        position.pos = points.get(1);
        position.node = getNodeAt(position.pos, position.graph);
        splitEdgeAt(position, entityRegistry);

        assertNodePath(graph, points, 0, 1, 2, 5);
    }

}
