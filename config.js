/**
 * This file will be copied to the GraphHopper Maps folder, please change this file and not the on in the GraphHopper Maps Submodule
 */
module.exports = {
    // the url of the GraphHopper backend, either use graphhopper.com or point it to your own GH instance
    api: 'https://graphhopper.com/api/1/',
    // the tile layer used by default, see MapOptionsStore.ts for all options
    defaultTiles: 'MapTiler',
    // various api keys used for the GH backend and the different tile providers
    keys: {
        graphhopper: "2b68713e-e80d-4f3d-8ddb-e3fcf72d8eac",
        maptiler: "fDZ3K4ABPXKW2fdJrft5",
        omniscale: "missing_api_key",
        thunderforest: "missing_api_key",
        kurviger: "missing_api_key"
    }
}

