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
import org.terasology.blockGraphs.graphDefinitions.BlockGraph;
import org.terasology.blockGraphs.graphDefinitions.GraphNodeComponent;
import org.terasology.blockGraphs.graphDefinitions.nodes.EdgeNode;
import org.terasology.blockGraphs.graphDefinitions.nodes.GraphNode;
import org.terasology.blockGraphs.graphDefinitions.nodes.JunctionNode;
import org.terasology.blockGraphs.graphDefinitions.nodes.TerminusNode;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.math.Side;
import org.terasology.math.geom.Vector3i;
import org.terasology.world.BlockEntityRegistry;

import java.util.ArrayList;
import java.util.List;

public final class NodeLinkHelper {
    /**
     * Links one node with another. Doesn't respect any pre-existing connections on the given side
     * TODO: Extract into GraphNode class?
     *
     * @param thisNode    The node to link from
     * @param otherNode   The node to link to
     * @param thisToOther The side to link via
     */
    static public void doUniLink(GraphNode thisNode, GraphNode otherNode, Side thisToOther) {
        switch (thisNode.getNodeType()) {
            case TERMINUS:
                ((TerminusNode) thisNode).linkNode(otherNode, thisToOther);
                break;
            case EDGE:
                EdgeNode thisEdge = (EdgeNode) thisNode;
                if (thisEdge.frontNode == null) {
                    thisEdge.linkNode(otherNode, Side.FRONT, thisToOther);
                } else if (thisEdge.backNode == null) {
                    thisEdge.linkNode(otherNode, Side.BACK, thisToOther);
                }
                break;
            case JUNCTION:
                JunctionNode thisJunction = (JunctionNode) thisNode;
                thisJunction.linkNode(otherNode, thisToOther);
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + thisNode.getNodeType());
        }
    }

    /**
     * Tries to link this node an another node.
     * If this node is a terminus, or junction try and link on the side it's physically on
     * If this node is an edge, try to link at the front, and then the back
     * Respect any existing connections
     * Repeat for the other node to make the link two way
     *
     * @param thisNode    The first node
     * @param otherNode   The second node to link
     * @param thisToOther The side from the first node to the second
     * @return True if they were linked, false otherwise
     */
    static public boolean tryBiLink(GraphNode thisNode, GraphNode otherNode, Side thisToOther) {
        if (canLinkNodes(thisNode, otherNode, thisToOther)) {
            doUniLink(thisNode, otherNode, thisToOther);
            doUniLink(otherNode, thisNode, thisToOther.reverse());
            return true;
        } else {
            return false;
        }
    }


    /**
     * Checks if we can link the nodes together in both directions
     *
     * @param thisNode    The first node to link
     * @param otherNode   The second node to link
     * @param thisToOther The side from the first node to the second
     * @return True if a bi-directional link can be made
     */
    static public boolean canLinkNodes(GraphNode thisNode, GraphNode otherNode, Side thisToOther) {
        return doesNodeHaveSpace(thisNode, thisToOther) && doesNodeHaveSpace(otherNode, thisToOther.reverse());
    }

    /**
     * Checks if a node can have a new node linked to on the given side.
     *
     * @param thisNode    The node to be linked
     * @param thisToOther The side to link on
     * @return True if the link can be made, false if there is already a connection
     */
    static public boolean doesNodeHaveSpace(GraphNode thisNode, Side thisToOther) {
        switch (thisNode.getNodeType()) {
            case TERMINUS:
                if (thisNode.getConnections().size() >= 1) {
                    return false;
                }
                break;
            case EDGE:
                if (thisNode.getConnections().size() >= 2) {
                    return false;
                }
                break;
            case JUNCTION:
                if (thisNode.getConnections().size() >= 6) {
                    return false;
                } else if (((JunctionNode) thisNode).getNodeForSide(thisToOther) != null) {
                    // Junction is tricky because it cares about the side
                    return false;
                }
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + thisNode.getNodeType());
        }
        return true;
    }


    static public Vector3i[] getSurroundingPos(Vector3i worldPos) {
        return Side.getAllSides().stream().map(side -> new Vector3i(side.getVector3i()).add(worldPos)).toArray(Vector3i[]::new);
    }

    /**
     * Gets the side that to is in relation two from
     * <p>
     * This side will be in relation to from
     * eg,
     * <0, 0, 0> & <1, 0, 0> will give RIGHT
     *
     * @param from The pos in relation to
     * @param to   The pos to find the side for
     * @return The side from from to to
     */
    public static Side getSideFrom(Vector3i from, Vector3i to) {
        return Side.inDirection(
                to.x - from.x,
                to.y - from.y,
                to.z - from.z);
    }

    public static <T> List<T> sublistCopy(List<T> list, int from, int to) {
        if (to < 0) {
            to = list.size() + to + 1;
        }
        return new ArrayList<>(list.subList(from, to));
    }

    public static class NodePosition {
        public NodePosition() {

        }

        public NodePosition(GraphNode node, Vector3i pos, BlockGraph graph) {
            this.node = node;
            this.pos = pos;
            this.graph = graph;
        }

        GraphNode node;
        Vector3i pos;
        BlockGraph graph;
    }


    public static void updatePosition(GraphNode node, BlockEntityRegistry entityRegistry) {
        switch (node.getNodeType()) {
            case TERMINUS:
                linkNodeToPosition(((TerminusNode) node).worldPos, node, entityRegistry);
                break;
            case EDGE:
                ((EdgeNode) node).worldPositions.forEach(pos -> linkNodeToPosition(pos, node, entityRegistry));
                break;
            case JUNCTION:
                linkNodeToPosition(((JunctionNode) node).worldPos, node, entityRegistry);
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + node.getNodeType());
        }
    }


    private static void linkNodeToPosition(Vector3i position, GraphNode node, BlockEntityRegistry entityRegistry) {
        EntityRef entity = entityRegistry.getExistingEntityAt(position);
        GraphNodeComponent component = new GraphNodeComponent();

        component.graphUri = node.graphUri;
        component.nodeId = node.nodeId;

        entity.addComponent(component);

        switch (node.getNodeType()) {
            case TERMINUS:
                ((TerminusNode) node).worldPos = position;
                break;
            case JUNCTION:
                ((JunctionNode) node).worldPos = position;
                break;
        }
    }


    public static void splitEdgeAt(NodePosition pos, BlockEntityRegistry entityRegistry) {
        splitEdgeAt(pos).forEach(graphNode -> updatePosition(graphNode, entityRegistry));
    }


    public static List<GraphNode> splitEdgeAt(NodePosition pos) {
        EdgeNode edgeNode = (EdgeNode) pos.node;

        // Firstly handle edge being 1 block
        if (edgeNode.worldPositions.size() == 1) {
            return convertEdgeNode(pos, edgeNode);
        } else {

            // Make the new junction node
            BlockGraph graph = pos.graph;
            JunctionNode junctionNode = graph.createJunctionNode(edgeNode.definitionId);
            junctionNode.worldPos = pos.pos;

            if (pos.pos.equals(edgeNode.frontPos)) { // Front end
                return shrinkAndLinkFront(edgeNode, junctionNode);
            } else if (pos.pos.equals(edgeNode.backPos)) { // Back end
                return shrinkAndLinkBack(edgeNode, junctionNode);
            } else { // Middle
                return splitInMiddle(pos, edgeNode, graph, junctionNode);
            }
        }
    }


    private static List<GraphNode> convertEdgeNode(NodePosition pos, EdgeNode edgeNode) {
        BlockGraph graph = pos.graph;
        JunctionNode newNode = graph.createJunctionNode(edgeNode.definitionId);

        GraphNode backNode = edgeNode.backNode;
        Side backSide = edgeNode.backSide;
        GraphNode frontNode = edgeNode.frontNode;
        Side frontSide = edgeNode.frontSide;

        graph.removeNode(edgeNode);

        tryBiLink(newNode, backNode, backSide);
        tryBiLink(newNode, frontNode, frontSide);

        return Lists.newArrayList(newNode);
    }

    private static List<GraphNode> splitInMiddle(NodePosition pos, EdgeNode edgeNode, BlockGraph graph, JunctionNode junctionNode) {
        // Calculate the positions of the split
        int indexOfPos = edgeNode.worldPositions.indexOf(pos.pos);
        if (indexOfPos < 0) {
            throw new IllegalStateException("Attempting to split an edge at point it doesn't cover");
        }
        Vector3i behindPos = edgeNode.worldPositions.get(indexOfPos - 1);
        Vector3i infrontPos = edgeNode.worldPositions.get(indexOfPos + 1);

        GraphNode frontNode = edgeNode.frontNode;
        Side frontSide = edgeNode.frontSide;
        Vector3i frontPos = edgeNode.frontPos;

        // Unlink the front of this edgeNode
        edgeNode.unlinkNode(frontNode);
        frontNode.unlinkNode(edgeNode);

        // Make the edge infront
        EdgeNode edgeInfront = graph.createEdgeNode(edgeNode.definitionId);
        edgeInfront.worldPositions = sublistCopy(edgeNode.worldPositions, indexOfPos + 1, -1);
        edgeInfront.frontPos = frontPos;
        tryBiLink(edgeInfront, frontNode, frontSide);
        edgeInfront.backPos = infrontPos;
        tryBiLink(edgeInfront, junctionNode, getSideFrom(edgeInfront.backPos, junctionNode.worldPos));

        // Shrink the node behind and link it to the junction
        edgeNode.worldPositions = sublistCopy(edgeNode.worldPositions, 0, indexOfPos);
        edgeNode.frontPos = behindPos;
        tryBiLink(edgeNode, junctionNode, getSideFrom(edgeNode.frontPos, junctionNode.worldPos));

        // Link the new edge
        return Lists.newArrayList(edgeNode, junctionNode, edgeInfront);
    }


    private static void flipNode(EdgeNode node) {
        GraphNode tempNode = node.frontNode;
        node.frontNode = node.backNode;
        node.backNode = tempNode;

        Vector3i tempPos = node.frontPos;
        node.frontPos = node.backPos;
        node.backPos = tempPos;

        Side tempSide = node.frontSide;
        node.frontSide = node.backSide;
        node.backSide = tempSide;

        node.worldPositions = Lists.reverse(node.worldPositions);
    }

    private static List<GraphNode> shrinkAndLinkFront(EdgeNode edgeNode, JunctionNode junctionNode) {
        // Record the details of the old front
        GraphNode frontNode = edgeNode.frontNode;
        Side frontSide = edgeNode.frontSide;

        // Separate the edge node and it's link
        edgeNode.unlinkNode(frontNode);
        frontNode.unlinkNode(edgeNode);

        // Shrink edge by one
        Vector3i newFrontPos = edgeNode.worldPositions.get(edgeNode.worldPositions.size() - 2);
        edgeNode.worldPositions.remove(edgeNode.worldPositions.size() - 1);
        edgeNode.frontPos = newFrontPos;

        // Link the junction node to the two connections
        tryBiLink(junctionNode, frontNode, frontSide); // link it to what the edge linked to
        tryBiLink(junctionNode, edgeNode, getSideFrom(junctionNode.worldPos, newFrontPos)); // link it the edge

        return Lists.newArrayList(edgeNode, junctionNode);
    }

    private static List<GraphNode> shrinkAndLinkBack(EdgeNode edgeNode, JunctionNode junctionNode) {
        flipNode(edgeNode);
        return shrinkAndLinkFront(edgeNode, junctionNode);
    }

}
