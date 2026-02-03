const NodeGeocoder = require('node-geocoder');

const options = {
  provider: 'locationiq',
  apiKey: process.env.LOCATIONIQ_API_KEY,
  httpAdapter: 'https',
  formatter: null
};

const geocoder = NodeGeocoder(options);

async function getAddressFromCoordinates(latitude, longitude) {
  try {
    console.log(`🔍 Geocoding: ${latitude}, ${longitude}`);
    
    const res = await geocoder.reverse({ lat: latitude, lon: longitude });
    
    if (res && res.length > 0) {
      const location = res[0];
      
      console.log('📍 Raw location data:', location);
      
      // Build formatted address from available fields
      const parts = [
        location.streetName || location.road,
        location.city || location.town || location.village,
        location.state || location.county,
        location.country
      ].filter(Boolean); // Remove null/undefined values
      
      const address = parts.join(', ') || location.formattedAddress || location.display_name || 'Address not found';
      
      console.log('✅ Formatted address:', address);
      return address;
    }
    
    console.log('⚠️ No geocoding results found');
    return 'Address not found';
  } catch (error) {
    console.error('❌ Geocoding error:', error.message);
    return 'Address unavailable';
  }
}

module.exports = { getAddressFromCoordinates };