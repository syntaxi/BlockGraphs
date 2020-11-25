// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.blockGraphs;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.blockGraphs.NodeLinkHelper.NodePosition;
import org.terasology.blockGraphs.graphDefinitions.BlockGraph;
import org.terasology.math.geom.Vector3i;
import org.terasology.moduletestingenvironment.extension.Dependencies;
import org.terasology.registry.In;
import org.terasology.world.BlockEntityRegistry;

import java.util.List;

import static org.terasology.blockGraphs.NodeLinkHelper.splitEdgeAt;

@Dependencies({"engine", "BlockGraphs"})
public class GraphChangeTests extends WorldBasedTests {
    Logger logger = LoggerFactory.getLogger(GraphChangeTests.class);

    @In
    private GraphChangeManager changeManager;
    @In
    private BlockEntityRegistry entityRegistry;

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
