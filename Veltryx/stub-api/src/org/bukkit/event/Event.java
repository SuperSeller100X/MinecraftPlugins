package org.bukkit.event;
public abstract class Event {
    public enum Result { DENY, DEFAULT, ALLOW }
    /** Offline-test hook; API stubs are not packaged. */
    public static java.util.function.Consumer<Event> testEventHook;
    public boolean callEvent() {
        if (testEventHook != null) testEventHook.accept(this);
        return !(this instanceof Cancellable) || !((Cancellable) this).isCancelled();
    }
}
