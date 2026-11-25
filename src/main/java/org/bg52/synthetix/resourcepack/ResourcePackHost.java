package org.bg52.synthetix.resourcepack;

/**
 * Interface representing a resource pack hosting endpoint.
 */
public interface ResourcePackHost {

    void start();

    void stop();

    int getPort();

    boolean isRunning();
}

