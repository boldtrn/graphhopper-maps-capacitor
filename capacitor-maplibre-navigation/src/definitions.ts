import type { PluginListenerHandle } from '@capacitor/core';

export interface StartNavigationOptions {
  /**
   * Route coordinates as array of [lng, lat] pairs
   */
  coordinates: [number, number][];

  /**
   * Route bounding box [[sw_lng, sw_lat], [ne_lng, ne_lat]]
   */
  bounds: [[number, number], [number, number]];
}

export interface MapLibreNavigationPlugin {
  /**
   * Start native navigation view with the given route
   */
  startNavigation(options: StartNavigationOptions): Promise<void>;

  /**
   * Stop and close the native navigation view
   */
  stopNavigation(): Promise<void>;

  /**
   * Listen for navigation closed event (user pressed back or close button)
   */
  addListener(
    eventName: 'navigationClosed',
    listenerFunc: () => void,
  ): Promise<PluginListenerHandle>;

  /**
   * Remove all listeners for this plugin
   */
  removeAllListeners(): Promise<void>;
}
