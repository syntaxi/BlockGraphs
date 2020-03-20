# Structure
- Graph Manager
    - Main API interaction point
- Graph Node
    _ A single node in the specific graph
- Block Graph
    - A distinct graph type
- Graph Constructor
    - Responsible for creating the new graphs automatically when a block is added

# API
- Get which node a block is in
- Add items into the graph at a specific block
- Automatically add blocks with a component to the graph
- Add new graph types with fancier nodes
    - A base node included by default.
    - Able to register a new graph type with a string name
    - Able to register specific nodes with that graph type
    
