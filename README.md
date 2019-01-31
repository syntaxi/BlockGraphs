# Block Graphs
Block Graphs allows you to, as the name suggests, build graphs of blocks, with each block corresponding to a node.  
This module does also support fully abstract graphs, without any blocks involved and for that see **Abstract Graphs** below.

It is intended to be a successor to [BlockNetworks](http://github.com/Terasology/BlockNetworks) and _if/when_ they reach feature parity can be considered a replacement. 
For more alternatives to this module check out the aforementioned BlockNetworks as well as [Machines](https://github com/Terasology/Machines).

**This module is currently under development.**  
All information herein is likely to change. I shall attempt to keep the architecture unchanged but api details like class names and how things are set/registered/etc will likely be in flux.

## Basic Setup
This is the quickest and simplest method of getting a block graph registered.

0. Add the module to your dependencies in the `module.txt`.
1. Create a _subclass_ of `NodeDefinition`, implement the required methods.
2. Repeat 1 for as many different node types you want in your graph.
3. Create a _new instance_ of `GraphType`.
4. Add all of the NodeDefinitions to the GraphType using `GraphType#addNodeType`
5. Register the graph type using `BlockGraphManager#addGraphType`
6. Use the `BlockGraphManager#injectData` methods to add data into the graph

If the module is functioning correctly now you can place any of the blocks you referenced in step 1 and it will build graphs automatically, linking them
