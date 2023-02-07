const config = {
    // the url of the GraphHopper backend, either use graphhopper.com or point it to your own GH instance
    api: 'https://graphhopper.com/api/1/',
    // the tile layer used by default, see MapOptionsStore.ts for all options
    routingApi: 'https://graphhopper.com/api/1/',
    geocodingApi: 'https://graphhopper.com/api/1/',
    defaultTiles: 'OpenStreetMap',
    navigationTiles: 'MapTiler',
    // various api keys used for the GH backend and the different tile providers
    keys: {
     "graphhopper":"2b68713e-e80d-4f3d-8ddb-e3fcf72d8eac",
     "maptiler":"wYonyRi2hNgJVH2qgs81",
     "omniscale":"_undefined_",
     "thunderforest":"_undefined_",
     "kurviger":"_undefined_"
    },
    // if true there will be an option to enable the GraphHopper routing graph and the urban density visualization in the layers menu
    routingGraphLayerAllowed: false,
    // parameters used for the routing request generation
    request: {
        details: [
            'road_class',
            'road_environment',
            'surface',
            'max_speed',
            'average_speed',
            'toll',
            'track_type',
            'country',
        ],
        snapPreventions: ['ferry'],
    },
    profiles: { car:{}, small_truck:{}, truck:{}, scooter:{}, foot:{}, hike:{}, bike:{}, mtb:{}, racingbike:{} },
}
