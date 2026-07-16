interface SentinelRuntimeConfig {
  mapboxAccessToken?: string;
}

interface Window {
  __SENTINEL_CONFIG__?: SentinelRuntimeConfig;
  __SENTINEL_MAP_TEST_MODE__?: boolean;
}

declare module '@mapbox/mapbox-gl-draw' {
  const MapboxDraw: any;
  export default MapboxDraw;
}
