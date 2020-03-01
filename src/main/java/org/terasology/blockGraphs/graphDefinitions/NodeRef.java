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
package org.terasology.blockGraphs.graphDefinitions;

import org.terasology.blockGraphs.graphDefinitions.nodes.EdgeNode;
import org.terasology.blockGraphs.graphDefinitions.nodes.GraphNode;
import org.terasology.blockGraphs.graphDefinitions.nodes.JunctionNode;
import org.terasology.blockGraphs.graphDefinitions.nodes.NodeType;
import org.terasology.blockGraphs.graphDefinitions.nodes.TerminusNode;
import org.terasology.math.Side;

import java.util.Collection;

/**
 * A reference to a node.
 * This class is used to allow the actually node object to be replaced but still have the code referencing the node work.
 */
public class NodeRef {
    private GraphNode node;

    public NodeRef(GraphNode source) {
        node = source;
    }

    @SuppressWarnings("unchecked")
    public <T extends GraphNode> T getNode() {
        if (!isValid()) {
            throw new InvalidNodeRefError();
        }
        return (T) node;
    }

    public TerminusNode asTerminus() {
        return getNode();
    }

    public EdgeNode asEdge() {
        return getNode();
    }

    public JunctionNode asJunction() {
        return getNode();
    }


    public boolean isValid() {
        return node != null;
    }

    void invalidate() {
        node = null;
    }

    void setNode(GraphNode node) {
        this.node = node;
    }


    public int getNodeId() {
        if (!isValid()) {
            throw new InvalidNodeRefError();
        }
        return node.nodeId;
    }

    public GraphUri getGraphUri() {
        if (!isValid()) {
            throw new InvalidNodeRefError();
        }
        return node.graphUri;
    }

    public int getDefinitionId() {
        if (!isValid()) {
            throw new InvalidNodeRefError();
        }
        return node.definitionId;
    }

    public NodeType getNodeType() {
        if (!isValid()) {
            throw new InvalidNodeRefError();
        }
        return node.getNodeType();
    }

    public void unlinkAll() {
        if (!isValid()) {
            throw new InvalidNodeRefError();
        }
        node.unlinkAll();
    }

    public void unlinkNode(NodeRef other) {
        if (!isValid()) {
            throw new InvalidNodeRefError();
        }
        node.unlinkNode(other);
    }

    public Collection<NodeRef> getConnections() {
        if (!isValid()) {
            throw new InvalidNodeRefError();
        }
        return node.getConnections();
    }

    public Side getSideForNode(NodeRef other) {
        if (!isValid()) {
            throw new InvalidNodeRefError();
        }
        return node.getSideForNode(other);
    }
}
