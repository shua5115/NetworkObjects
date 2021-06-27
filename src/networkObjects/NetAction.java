package networkObjects;

import processing.net.*;

public interface NetAction {
	void act(NetworkIO net, Client c);
}
