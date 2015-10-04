package org.simlar.proximityscreenlocker;

public interface ProximityScreenLocker
{
	void acquire();

	/// Not immediately means we wait till the proximity sensor signals that the user is not near anymore
	@SuppressWarnings("SameParameterValue")
	void release(final boolean immediately);
}
