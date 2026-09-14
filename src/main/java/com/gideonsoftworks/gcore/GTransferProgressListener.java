package com.gideonsoftworks.gcore;

/**
 * Receives per-chunk progress for one file transfer from a protocol plugin.
 *
 * <p>Contract (this is what the plugins already do; the interface just names it): {@code transferred}
 * is the <em>cumulative</em> byte count of the file currently being moved, starting again at zero for
 * the next file. It is invoked on the transfer thread — for S3 on the SDK's own thread pool — and
 * possibly hundreds of times a second, so implementations must be thread-safe and cheap, and must
 * never touch Swing directly.
 *
 * <p>{@code GJobPlugin} implements it (routing into the job status); the mirror engine passes a
 * per-copy lambda so each row of the Running panel gets its own byte count.
 */
@FunctionalInterface
public interface GTransferProgressListener
{
    void updateTransferStats( long transferred );
}
