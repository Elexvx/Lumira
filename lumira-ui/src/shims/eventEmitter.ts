type EventKey = string | symbol;
type EventListener = (...args: unknown[]) => void;

class EventEmitter {
  private readonly listeners = new Map<EventKey, Set<EventListener>>();

  on(event: EventKey, listener: EventListener) {
    const listeners = this.listeners.get(event) || new Set<EventListener>();
    listeners.add(listener);
    this.listeners.set(event, listeners);
    return this;
  }

  once(event: EventKey, listener: EventListener) {
    const onceListener: EventListener = (...args) => {
      this.off(event, onceListener);
      listener(...args);
    };
    return this.on(event, onceListener);
  }

  off(event: EventKey, listener: EventListener) {
    const listeners = this.listeners.get(event);
    listeners?.delete(listener);
    if (listeners?.size === 0) {
      this.listeners.delete(event);
    }
    return this;
  }

  emit(event: EventKey, ...args: unknown[]) {
    for (const listener of [...(this.listeners.get(event) || [])]) {
      listener(...args);
    }
    return this;
  }
}

export default EventEmitter;
