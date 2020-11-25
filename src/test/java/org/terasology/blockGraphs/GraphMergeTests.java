/*
 * Copyright 2020 MovingBlocks
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
import org.terasology.blockGraphs.graphDefinitions.BlockGraph;
import org.terasology.math.geom.Vector3i;
import org.terasology.registry.In;
import org.terasology.world.BlockEntityRegistry;

import java.util.Collections;
import java.util.List;

/**
 * Tests all around merging different graph types together. Very closely related to {@link GraphChangeTests} but is it's own class due to the very large number of different situations
 */
public class GraphMergeTests extends WorldBasedTests {

    @In
    private GraphChangeManager changeManager;
    @In
    private BlockEntityRegistry entityRegistry;

    /**
     * Test merging two single terminus graphs
     */
    @Test
    public void testMergingTTSingle() {
        Vector3i point1 = new Vector3i(0, 0, 0);
        Vector3i point2 = new Vector3i(0, 1, 0);

        quickMergeTest(Collections.singletonList(point1), Collections.singletonList(point2));
    }

    /**
     * Test merging one single terminus graph with one dual terminus graph
     */
    @Test
    public void testMergingTTSingleDouble() {
        List<Vector3i> points1 = pointsToVectors(new int[][]{
                {0, 2, 0}, // TERMINUS
                {0, 3, 0}// TERMINUS
        });
        Vector3i point2 = new Vector3i(0, 1, 0);

        quickMergeTest(points1, Collections.singletonList(point2));
    }


    /**
     * Given two sets of points, construct graphs and try merging them.
     * The merging will happen at the first point of each graph.
     *
     * @param points1 The first set of points.
     * @param points2 The second set of points.
     */
    private void quickMergeTest(List<Vector3i> points1, List<Vector3i> points2) {
        BlockGraph graph1 = constructAndCrunchPoints(points1);
        BlockGraph graph2 = constructAndCrunchPoints(points2);

        NodeLinkHelper.NodePosition from = new NodeLinkHelper.NodePosition(getNodeAt(points1.get(0), graph1), points1.get(0), graph1); // End of first
        NodeLinkHelper.NodePosition to = new NodeLinkHelper.NodePosition(getNodeAt(points2.get(0), graph2), points2.get(0), graph2); // Start of second
        changeManager.mergeInto(from, to);

        // Test they are connected
        checkBiConnection(from.node, to.node);
        //TODO: Test graphs merging
    }
}
