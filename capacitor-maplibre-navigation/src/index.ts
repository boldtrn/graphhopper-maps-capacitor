import { registerPlugin } from '@capacitor/core';

import type { MapLibreNavigationPlugin } from './definitions';

const MapLibreNavigation = registerPlugin<MapLibreNavigationPlugin>(
  'MapLibreNavigation',
  {
    web: () => import('./web').then((m) => new m.MapLibreNavigationWeb()),
  },
);

export * from './definitions';
export { MapLibreNavigation };
