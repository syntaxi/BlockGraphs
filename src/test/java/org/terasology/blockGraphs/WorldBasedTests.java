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
import org.junit.Before;
import org.terasology.blockGraphs.dataMovement.GraphPositionComponent;
import org.terasology.blockGraphs.graphDefinitions.BlockGraph;
import org.terasology.blockGraphs.graphDefinitions.GraphNodeComponent;
import org.terasology.blockGraphs.graphDefinitions.nodes.GraphNode;
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

    void setAllTo(Block block, Iterable<Vector3i> points) {
        points.forEach(point -> worldProvider.setBlock(point, block));
    }

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
    GraphNode getNodeAt(Vector3i position, BlockGraph graph) {
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


    void testPath(List<Vector3i> points, BlockGraph graph, int[] path) {
        EntityRef testData = buildData(new NodePathTestComponent());
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

    void testPath(List<Vector3i> points, BlockGraph graph) {
        testPath(points, graph, IntStream.range(0, points.size()).toArray());
    }

}
