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

import org.terasology.blockGraphs.graphDefinitions.nodes.EdgeNode;
import org.terasology.blockGraphs.graphDefinitions.nodes.GraphNode;
import org.terasology.blockGraphs.graphDefinitions.nodes.JunctionNode;
import org.terasology.blockGraphs.graphDefinitions.nodes.TerminusNode;
import org.terasology.math.Side;
import org.terasology.math.geom.Vector3i;

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
     * @param to The pos to find the side for
     * @return The side from from to to
     */
    public static Side getSideFrom(Vector3i from, Vector3i to) {
        return Side.inDirection(
                to.x - from.x,
                to.y - from.y,
                to.z - from.z);
    }
}
