const NodeGeocoder = require('node-geocoder');

const options = {
  provider: 'locationiq',
  apiKey: process.env.LOCATIONIQ_API_KEY,
  formatter: null
};

const geocoder = NodeGeocoder(options);

async function getAddressFromCoordinates(latitude, longitude) {
  try {
    console.log(`🔍 Geocoding: ${latitude}, ${longitude}`);
    
    // Validate coordinates
    if (!latitude || !longitude || isNaN(latitude) || isNaN(longitude)) {
      console.log('❌ Invalid coordinates');
      return 'Invalid coordinates';
    }

    // Check if API key is configured
    if (!process.env.LOCATIONIQ_API_KEY) {
      console.error('❌ LOCATIONIQ_API_KEY not configured');
      return 'Geocoding service not configured';
    }

    const res = await geocoder.reverse({ 
      lat: parseFloat(latitude), 
      lon: parseFloat(longitude) 
    });
    
    if (res && res.length > 0) {
      const location = res[0];
      
      console.log('📍 Raw location data:', JSON.stringify(location, null, 2));
      
      // Build formatted address from available fields
      // LocationIQ returns different fields than OpenStreetMap
      const parts = [
        location.neighbourhood || location.suburb || location.locality,
        location.city || location.town || location.village || location.county,
        location.state || location.stateDistrict,
        location.zipcode || location.postalCode,
        location.country
      ].filter(Boolean); // Remove null/undefined values
      
      let address = parts.join(', ');
      
      // If we didn't get enough parts, try using the formatted address
      if (parts.length < 2 && (location.formattedAddress || location.display_name)) {
        address = location.formattedAddress || location.display_name;
      }
      
      // If still no address, return coordinates as fallback
      if (!address || address.trim().length === 0) {
        address = `Location: ${latitude}, ${longitude}`;
      }
      
      console.log('✅ Formatted address:', address);
      return address;
    }
    
    console.log('⚠️ No geocoding results found');
    return `Location: ${latitude}, ${longitude}`;
  } catch (error) {
    console.error('❌ Geocoding error:', error.message);
    console.error('Stack trace:', error.stack);
    
    // Return coordinates as fallback instead of generic error
    return `Location: ${latitude}, ${longitude}`;
  }
}

module.exports = { getAddressFromCoordinates };