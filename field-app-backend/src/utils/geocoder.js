const NodeGeocoder = require('node-geocoder');

const options = {
  provider: 'openstreetmap',
  httpAdapter: 'https',
  formatter: null
};

const geocoder = NodeGeocoder(options);

async function getAddressFromCoordinates(latitude, longitude) {
  try {
    const res = await geocoder.reverse({ lat: latitude, lon: longitude });
    
    if (res && res.length > 0) {
      const location = res[0];
      return location.formattedAddress || 
             `${location.streetName || ''}, ${location.city || ''}, ${location.state || ''}, ${location.country || ''}`.replace(/^,\s*|,\s*$/g, '');
    }
    return 'Address not found';
  } catch (error) {
    console.error('Geocoding error:', error);
    return 'Address unavailable';
  }
}

module.exports = { getAddressFromCoordinates };