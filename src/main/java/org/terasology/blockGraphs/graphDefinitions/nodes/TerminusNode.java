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
package org.terasology.blockGraphs.graphDefinitions.nodes;

import com.google.common.collect.ImmutableSet;
import org.terasology.blockGraphs.graphDefinitions.GraphUri;
import org.terasology.blockGraphs.graphDefinitions.NodeRef;
import org.terasology.math.Side;
import org.terasology.math.geom.Vector3i;

import java.util.Collection;

public class TerminusNode extends GraphNode {
    /**
     * The position of this node in the world
     */
    public Vector3i worldPos;

    /**
     * The side that this node's connection is on.
     */
    public Side connectionSide;

    /**
     * The node this terminus connects to
     */
    public NodeRef connectionNode;

    public TerminusNode(GraphUri graphUri, int nodeId, int definitionId) {
        super(graphUri, nodeId, definitionId);
    }

    @Override
    public NodeType getNodeType() {
        return NodeType.TERMINUS;
    }

    public void linkNode(NodeRef node, Side onSide) {
        connectionNode = node;
        connectionSide = onSide;
    }

    public void linkNode(NodeRef node, Side onSide, Vector3i pos) {
        linkNode(node, onSide);
        worldPos = pos;
    }

    @Override
    public void unlinkNode(NodeRef node) {
        if (node == connectionNode) {
            unlinkAll();
        }
    }

    @Override
    public Collection<NodeRef> getConnections() {
        if (connectionNode != null) {
            return ImmutableSet.of(connectionNode);
        } else {
            return ImmutableSet.of();
        }
    }

    @Override
    public Side getSideForNode(NodeRef node) {
        if (node == connectionNode) {
            return connectionSide;
        } else {
            return null;
        }
    }

    @Override
    public void unlinkAll() {
        connectionNode = null;
        connectionSide = null;
    }

    /**
     * @return True if this terminus has a connection, false otherwise
     */
    public boolean isConnected() {
        return connectionNode != null;
    }

}

