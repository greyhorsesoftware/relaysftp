package com.gideonsoftworks.gideon.core;

/** Application lifecycle notifications; registered through {@link GAppLifecycle}. Invoked on the EDT. */
public interface GAppLifecycleListenerInterface
{
    /** The app is about to exit: save state now. */
    default void al_appWillQuit() {}
}
