package org.simlar;

public interface ProximityScreenLocker
{
	public void acquire();

	/// Not immediately means we wait till the proximity sensor signals that the user is not near anymore
	@SuppressWarnings("SameParameterValue")
	public void release(final boolean immediately);
}
