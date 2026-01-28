import { WebPlugin } from '@capacitor/core';

import type { MapLibreNavigationPlugin, StartNavigationOptions } from './definitions';

export class MapLibreNavigationWeb extends WebPlugin implements MapLibreNavigationPlugin {
  async startNavigation(_options: StartNavigationOptions): Promise<void> {
    console.warn('MapLibreNavigation: startNavigation is not available on web');
  }

  async stopNavigation(): Promise<void> {
    console.warn('MapLibreNavigation: stopNavigation is not available on web');
  }
}
