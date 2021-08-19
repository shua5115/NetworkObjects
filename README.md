# Core Functionality
- NetworkIO class manages sending and recieving data over the internet
- Package interface requires classes to be converted to and from byte arrays
- ByteOps class helps convert primitives to and from byte arrays, along with other array operations
- NetAction interface to allows code to be run on certain network events within a NetworkIO
# How to Import
1. Download the <b>NetworkObjects-#.#.#.zip</b> file from the releases section.
2. Extract the folder into your sketchbook's library folder. On windows this is located in Documents/Processing/libraries by default.
Make sure that the topmost folder is named only "NetworkObjects" and going into that folder shows the "library" folder.
4. In processing's ribbon, navigate to Sketch -> Import Library -> NetworkObjects
# How to use
The provided MouseSharing example exemplifies a basic Server-Client networking system using the this library:
- The clients send their current mouse position to the server
- The server sends all clients mouse positions to each client
- Each client displays every mouse cursor with a trail
