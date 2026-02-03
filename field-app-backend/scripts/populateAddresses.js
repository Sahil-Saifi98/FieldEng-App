const mongoose = require('mongoose');
const dotenv = require('dotenv');
const path = require('path');

// Load environment variables
dotenv.config({ path: path.join(__dirname, '../.env') });

const Attendance = require('../src/models/Attendance');
const { getAddressFromCoordinates } = require('../src/utils/geocoder');

async function populateAddresses() {
  try {
    console.log('🚀 Starting address population script...\n');
    console.log('🔄 Connecting to MongoDB...');
    
    if (!process.env.MONGODB_URI) {
      throw new Error('MONGODB_URI not found in environment variables. Please check your .env file.');
    }
    
    await mongoose.connect(process.env.MONGODB_URI);
    console.log('✅ Connected to MongoDB\n');
    
    // First, let's see how many total records exist
    const totalCount = await Attendance.countDocuments();
    console.log(`📊 Total attendance records in database: ${totalCount}`);
    
    // Check records with each condition separately
    const emptyAddress = await Attendance.countDocuments({ address: '' });
    const noAddress = await Attendance.countDocuments({ address: { $exists: false } });
    const nullAddress = await Attendance.countDocuments({ address: null });
    const unavailableAddress = await Attendance.countDocuments({ address: 'Address unavailable' });
    const locationAddress = await Attendance.countDocuments({ address: /^Location:/ });
    
    console.log(`   - Empty address (''): ${emptyAddress}`);
    console.log(`   - No address field: ${noAddress}`);
    console.log(`   - Null address: ${nullAddress}`);
    console.log(`   - 'Address unavailable': ${unavailableAddress}`);
    console.log(`   - 'Location: lat, lng' format: ${locationAddress}`);
    
    // Find all records without proper address
    const records = await Attendance.find({ 
      $or: [
        { address: '' }, 
        { address: { $exists: false } },
        { address: null },
        { address: 'Address unavailable' },
        { address: /^Location:/ }
      ] 
    });
    
    console.log(`\n📊 Found ${records.length} records needing address update\n`);
    
    if (records.length === 0) {
      console.log('✅ All records already have proper addresses!');
      await mongoose.connection.close();
      process.exit(0);
    }
    
    let successCount = 0;
    let failCount = 0;
    const failedRecords = [];
    
    for (let i = 0; i < records.length; i++) {
      const record = records[i];
      console.log(`[${i + 1}/${records.length}] Processing: ${record.employeeId}`);
      console.log(`   Location: ${record.latitude}, ${record.longitude}`);
      
      try {
        const address = await getAddressFromCoordinates(record.latitude, record.longitude);
        
        // Only update if we got a real address (not a fallback)
        if (address && !address.startsWith('Location:') && address !== 'Address unavailable') {
          record.address = address;
          await record.save();
          console.log(`   ✅ ${address}\n`);
          successCount++;
        } else {
          console.log(`   ⚠️  Geocoding failed, keeping as: ${address}\n`);
          record.address = address; // Still save the fallback
          await record.save();
          failCount++;
          failedRecords.push({
            id: record._id,
            employeeId: record.employeeId,
            lat: record.latitude,
            lng: record.longitude
          });
        }
        
        // Delay to respect LocationIQ rate limits (1 request per second on free tier)
        if (i < records.length - 1) {
          await new Promise(resolve => setTimeout(resolve, 1200));
        }
      } catch (error) {
        console.log(`   ❌ Error: ${error.message}\n`);
        // Save with fallback address
        record.address = `Location: ${record.latitude}, ${record.longitude}`;
        await record.save();
        failCount++;
        failedRecords.push({
          id: record._id,
          employeeId: record.employeeId,
          lat: record.latitude,
          lng: record.longitude,
          error: error.message
        });
      }
    }
    
    console.log('='.repeat(70));
    console.log('📊 Summary:');
    console.log(`   ✅ Successfully geocoded: ${successCount}`);
    console.log(`   ⚠️  Failed/Fallback: ${failCount}`);
    console.log(`   📝 Total processed: ${records.length}`);
    console.log('='.repeat(70));
    
    if (failedRecords.length > 0) {
      console.log('\n⚠️  Records with geocoding issues:');
      failedRecords.forEach((rec, idx) => {
        console.log(`   ${idx + 1}. ${rec.employeeId} (${rec.lat}, ${rec.lng})`);
        if (rec.error) console.log(`      Error: ${rec.error}`);
      });
    }
    
    await mongoose.connection.close();
    console.log('\n✅ Database connection closed');
    process.exit(0);
  } catch (error) {
    console.error('\n❌ Fatal Error:', error.message);
    console.error(error);
    if (mongoose.connection.readyState === 1) {
      await mongoose.connection.close();
    }
    process.exit(1);
  }
}

// Run the script
populateAddresses();