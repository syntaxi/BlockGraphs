// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.blockGraphs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.blockGraphs.dataMovement.GraphPositionComponent;
import org.terasology.blockGraphs.dataMovement.OnLeaveGraphEvent;
import org.terasology.blockGraphs.graphDefinitions.BlockGraph;
import org.terasology.blockGraphs.graphDefinitions.GraphType;
import org.terasology.blockGraphs.graphDefinitions.NodeRef;
import org.terasology.blockGraphs.testDefinitions.EjectOnTriggerComponent;
import org.terasology.blockGraphs.testDefinitions.NodePathTestComponent;
import org.terasology.blockGraphs.testDefinitions.TestRandomDefinition;
import org.terasology.blockGraphs.testDefinitions.TestRemoveDefinition;
import org.terasology.blockGraphs.testDefinitions.TestUpwardsDefinition;
import org.terasology.engine.SimpleUri;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.math.Side;
import org.terasology.moduletestingenvironment.TestEventReceiver;
import org.terasology.moduletestingenvironment.extension.Dependencies;
import org.terasology.world.block.BlockUri;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.core.AnyOf.anyOf;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;


@Dependencies({"engine", "BlockGraphs"})
public class DataMovementTest extends GraphTesting {
    private static final Logger logger = LoggerFactory.getLogger(DataMovementTest.class);

    private BlockGraph graph;

    @BeforeEach
    public void initialize() {
        super.initialize();

        GraphType graphType = new GraphType(new SimpleUri("BlockGraphs:TestGraph"));
        graphType.addNodeType(new TestUpwardsDefinition());
        graphType.addNodeType(new TestRandomDefinition());
        graphType.addNodeType(new TestRemoveDefinition());

        graph = graphManager.newGraphInstance(graphType);
    }

    /**
     * Tests that the graph is registered into the manager properly
     */
    @Test
    public void testRegistration() {
        assertNotNull(graphManager.getGraphInstance(graph.getUri()));
    }

    /**
     * Tests a simple graph case where the data moves sequentially through a series of nodes. With the last being a
     * terminus
     * <code>1 -> 2 -> 3 -> 4</code>
     */
    @Test
    public void testSimpleGraph() {
        EntityRef testData = buildData(new NodePathTestComponent());

        /* Build Graph */
        NodeRef[] terminusNodes = createTerminus(2, TestUpwardsDefinition.BLOCK_URI);
        NodeRef[] nodes = createJunctions(2, TestUpwardsDefinition.BLOCK_URI);

        terminusNodes[0].asTerminus().linkNode(nodes[0], Side.TOP);

        nodes[0].asJunction().linkNode(terminusNodes[0], Side.BOTTOM);
        nodes[0].asJunction().linkNode(nodes[1], Side.TOP);

        nodes[1].asJunction().linkNode(nodes[0], Side.BOTTOM);
        nodes[1].asJunction().linkNode(terminusNodes[1], Side.TOP);

        terminusNodes[1].asTerminus().linkNode(nodes[1], Side.BOTTOM);

        /* Insert & let the data travel through the system */
        movementSystem.insertData(terminusNodes[0], testData);
        helper.runUntil(() -> testData.getComponent(NodePathTestComponent.class).isFinished);

        /* Test the path travelled */
        List<Integer> dataPath = testData.getComponent(NodePathTestComponent.class).nodePath;
        List<Integer> expectedPath = Arrays.asList(
                terminusNodes[0].getNodeId(),
                nodes[0].getNodeId(),
                nodes[1].getNodeId(),
                terminusNodes[1].getNodeId());

        assertThat(dataPath, is(expectedPath));
        assertFalse(testData.hasComponent(GraphPositionComponent.class));
    }

    /**
     * Tests a simple graph case where the data moves sequentially through a series of nodes. There is one interior
     * node, an edge
     * <code>1 -> [..2..] -> 3</code>
     */
    @Test
    public void testEdgeGraph() {
        EntityRef testData = buildData(new NodePathTestComponent());

        /* Build Graph */
        NodeRef[] nodes = createTerminus(2, TestUpwardsDefinition.BLOCK_URI);
        NodeRef edgeNode = graph.createEdgeNode(TestUpwardsDefinition.BLOCK_URI);

        /* Always link from the edge node */
        nodes[0].asTerminus().linkNode(edgeNode, Side.TOP);
        edgeNode.asEdge().linkNode(nodes[0], Side.BACK, Side.BOTTOM);
        edgeNode.asEdge().linkNode(nodes[1], Side.FRONT, Side.TOP);
        nodes[1].asTerminus().linkNode(edgeNode, Side.BOTTOM);

        /* Insert & let the data travel through the system */
        movementSystem.insertData(nodes[0], testData);
        helper.runUntil(() -> testData.getComponent(NodePathTestComponent.class).isFinished);

        /* Test the path travelled */
        List<Integer> dataPath = testData.getComponent(NodePathTestComponent.class).nodePath;
        List<Integer> expectedPath = Arrays.asList(
                nodes[0].getNodeId(),
                edgeNode.getNodeId(),
                nodes[1].getNodeId()); // Edge node is created after the other two
        assertThat(dataPath, is(expectedPath));
        assertFalse(testData.hasComponent(GraphPositionComponent.class));
    }

    /**
     * Tests a graph with a single choice Consists of three junctions, three edges and one
     *
     * <code>               ↱ [..5..] → 2 </code>
     * <code> 1 → [..4..] → 7             </code>
     * <code                ↳ [..6..] → 3 </code>
     * <p>
     * This leads to 3 options for path
     * <code>1 → 4 → 7 → 4 → 1</code>
     * <code>1 → 4 → 7 → 5 → 2</code>
     * <code>1 → 4 → 7 → 6 → 3</code>
     */
    @Test
    public void testBranchedGraph() {
        EntityRef testData = buildData(new NodePathTestComponent());
        NodeRef[] terminusNodes = createTerminus(3, TestRandomDefinition.BLOCK_URI);
        NodeRef[] edgeNodes = createEdges(3, TestRandomDefinition.BLOCK_URI);
        NodeRef junctionNode = createJunctions(1, TestRandomDefinition.BLOCK_URI)[0];

        /* Make the graph */
        terminusNodes[0].asTerminus().linkNode(edgeNodes[0], Side.TOP);
        edgeNodes[0].asEdge().linkNode(terminusNodes[0], Side.BACK, Side.BOTTOM);
        edgeNodes[0].asEdge().linkNode(junctionNode, Side.FRONT, Side.TOP);
        junctionNode.asJunction().linkNode(edgeNodes[0], Side.BOTTOM);

        junctionNode.asJunction().linkNode(edgeNodes[1], Side.LEFT);
        edgeNodes[1].asEdge().linkNode(junctionNode, Side.BACK, Side.RIGHT);
        edgeNodes[1].asEdge().linkNode(terminusNodes[1], Side.FRONT, Side.TOP);
        terminusNodes[1].asTerminus().linkNode(edgeNodes[1], Side.BOTTOM);

        junctionNode.asJunction().linkNode(edgeNodes[2], Side.RIGHT);
        edgeNodes[2].asEdge().linkNode(junctionNode, Side.BACK, Side.LEFT);
        edgeNodes[2].asEdge().linkNode(terminusNodes[2], Side.FRONT, Side.TOP);
        terminusNodes[2].asTerminus().linkNode(edgeNodes[2], Side.BOTTOM);

        /* Insert & let the data travel through the system */
        movementSystem.insertData(terminusNodes[0], testData);
        helper.runUntil(() -> testData.getComponent(NodePathTestComponent.class).isFinished);



        /* Test the path travelled */
        List<Integer> dataPath = testData.getComponent(NodePathTestComponent.class).nodePath;
        List<List<Integer>> expectedPaths = Arrays.asList(
                Arrays.asList(
                        terminusNodes[0].getNodeId(),
                        edgeNodes[0].getNodeId(),
                        junctionNode.getNodeId(),
                        edgeNodes[0].getNodeId(),
                        terminusNodes[0].getNodeId()),
                Arrays.asList(
                        terminusNodes[0].getNodeId(),
                        edgeNodes[0].getNodeId(),
                        junctionNode.getNodeId(),
                        edgeNodes[1].getNodeId(),
                        terminusNodes[1].getNodeId()),
                Arrays.asList(
                        terminusNodes[0].getNodeId(),
                        edgeNodes[0].getNodeId(),
                        junctionNode.getNodeId(),
                        edgeNodes[2].getNodeId(),
                        terminusNodes[2].getNodeId())
        );
        assertThat(dataPath, anyOf(
                is(expectedPaths.get(0)),
                is(expectedPaths.get(1)),
                is(expectedPaths.get(2))
        ));
        assertFalse(testData.hasComponent(GraphPositionComponent.class));
    }

    /**
     * Tests a graph made of two different types of nodes Still a simple graph of an edge, junction and two terminus
     * <p>
     * 1A -> 2B -> 3A
     */
    @Test
    public void testMultipleNodeTypes() {
        EntityRef testData = buildData(new NodePathTestComponent());
        NodeRef[] terminusNodes = createTerminus(2, TestUpwardsDefinition.BLOCK_URI);
        NodeRef edgeNode = createEdges(1, TestRandomDefinition.BLOCK_URI)[0];
        NodeRef junctionNode = createJunctions(1, TestUpwardsDefinition.BLOCK_URI)[0];

        terminusNodes[0].asTerminus().linkNode(edgeNode, Side.LEFT);
        edgeNode.asEdge().linkNode(terminusNodes[0], Side.FRONT, Side.RIGHT);
        edgeNode.asEdge().linkNode(junctionNode, Side.BACK, Side.FRONT);
        junctionNode.asJunction().linkNode(edgeNode, Side.BACK);
        junctionNode.asJunction().linkNode(terminusNodes[1], Side.TOP);
        terminusNodes[1].asTerminus().linkNode(junctionNode, Side.BOTTOM);

        /* Insert & let the data travel through the system */
        movementSystem.insertData(terminusNodes[0], testData);
        helper.runUntil(() -> testData.getComponent(NodePathTestComponent.class).isFinished);

        /* Test the path travelled */
        List<Integer> dataPath = testData.getComponent(NodePathTestComponent.class).nodePath;
        List<Integer> expectedPath = Arrays.asList(
                terminusNodes[0].getNodeId(),
                edgeNode.getNodeId(),
                junctionNode.getNodeId(),
                terminusNodes[1].getNodeId());
        assertThat(dataPath, is(expectedPath));
        assertFalse(testData.hasComponent(GraphPositionComponent.class));
    }

    @Test
    public void testDataRemoved() {
        EjectOnTriggerComponent component = new EjectOnTriggerComponent();
        EntityRef testData = buildData(component, new NodePathTestComponent());

        /* Build Graph */
        NodeRef[] terminusNodes = createTerminus(2, TestUpwardsDefinition.BLOCK_URI);
        NodeRef node = graph.createJunctionNode(TestUpwardsDefinition.BLOCK_URI);
        NodeRef removeNode = graph.createJunctionNode(TestRemoveDefinition.BLOCK_URI);

        terminusNodes[0].asTerminus().linkNode(node, Side.TOP);

        node.asJunction().linkNode(terminusNodes[0], Side.BOTTOM);
        node.asJunction().linkNode(removeNode, Side.TOP);

        removeNode.asJunction().linkNode(node, Side.BOTTOM);
        removeNode.asJunction().linkNode(terminusNodes[1], Side.TOP);

        terminusNodes[1].asTerminus().linkNode(removeNode, Side.BOTTOM);

        /* Setup event receiver to listen for data being ejected */
        AtomicBoolean wasRemoved = new AtomicBoolean(false);
        AtomicReference<NodeRef> removedFrom = new AtomicReference<>(null);
        AtomicBoolean wasEjected = new AtomicBoolean();
        new TestEventReceiver<>(helper.getHostContext(), OnLeaveGraphEvent.class, (event, entity) -> {
            wasRemoved.set(true);
            wasEjected.set(event.wasEjected);
            removedFrom.set(event.finalNode);
            // do something with the event or entity
        });

        /* Insert & let the data travel through the system */
        component.trigger = graphNode -> graphNode == removeNode;
        movementSystem.insertData(terminusNodes[0], testData);
        helper.runUntil(wasRemoved::get);


        /* Test the path travelled */
        List<Integer> dataPath = testData.getComponent(NodePathTestComponent.class).nodePath;
        List<Integer> expectedPath = Arrays.asList(
                terminusNodes[0].getNodeId(),
                node.getNodeId(),
                removeNode.getNodeId()); // We eject so we assume no final terminus

        assertThat(dataPath, is(expectedPath));
        assertTrue(testData.hasComponent(EjectOnTriggerComponent.class));
        assertFalse(testData.hasComponent(GraphPositionComponent.class));

        assertSame(removeNode, removedFrom.get());
        assertFalse(wasEjected.get());
    }

    private NodeRef[] createJunctions(int count, BlockUri block) {
        NodeRef[] nodes = new NodeRef[count];
        for (int i = 0; i < count; i++) {
            nodes[i] = graph.createJunctionNode(block); //This is fine
        }
        return nodes;
    }

    private NodeRef[] createTerminus(int count, BlockUri block) {
        NodeRef[] nodes = new NodeRef[count];
        for (int i = 0; i < count; i++) {
            nodes[i] = graph.createTerminusNode(block); //This is fine
        }
        return nodes;
    }

    private NodeRef[] createEdges(int count, BlockUri block) {
        NodeRef[] nodes = new NodeRef[count];
        for (int i = 0; i < count; i++) {
            nodes[i] = graph.createEdgeNode(block); //This is fine
        }
        return nodes;

    }


}
