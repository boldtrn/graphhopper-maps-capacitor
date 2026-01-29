import type { PluginListenerHandle } from '@capacitor/core';

export interface StartNavigationOptions {
  /**
   * URL for the GraphHopper /navigate endpoint (including API key)
   */
  navigateUrl: string;
  /**
   * JSON request body for POST /navigate
   */
  requestBody: string;
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
