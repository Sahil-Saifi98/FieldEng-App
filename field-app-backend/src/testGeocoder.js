const { getAddressFromCoordinates } = require('./utils/geocoder');

async function test() {
  console.log('Testing geocoder...\n');
  
  // Test with a known location (India Gate, Delhi)
  const address = await getAddressFromCoordinates(28.6129, 77.2295);
  console.log('Address:', address);
  
  process.exit(0);
}

test();